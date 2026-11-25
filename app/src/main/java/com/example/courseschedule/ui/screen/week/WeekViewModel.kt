package com.example.courseschedule.ui.screen.week

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Room
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.ScheduleChange
import com.example.courseschedule.data.db.ScheduleBackup
import com.example.courseschedule.util.SettingsPrefs
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.importer.ImportedCourse
import com.example.courseschedule.data.importer.CourseImportResult
import com.example.courseschedule.data.importer.ImportUpdate
import com.example.courseschedule.data.importer.ImportedSchedule
import com.example.courseschedule.data.importer.ImportRowIssue
import com.example.courseschedule.data.importer.ParsedCourseImport
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.navigation.NavigationState
import com.example.courseschedule.util.DateUtils
import com.example.courseschedule.util.groupWeeksForSchedules
import com.example.courseschedule.util.selectedWeeksOverlap
import com.example.courseschedule.util.weeksForSpan
import com.example.courseschedule.worker.ExamReminderWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import com.example.courseschedule.util.scheduleOccurrences
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: CourseRepository,
    @ApplicationContext private val applicationContext: Context
) : ViewModel() {

    @Immutable
    data class WeekPageData(
        val roomMap: Map<Long, String> = emptyMap(),
        val weekRange: String = ""
    )

    @Immutable
    data class WeekUiState(
        val semester: Semester? = null,
        val currentWeek: Int = 1,
        val selectedWeek: Int = 1,
        val currentDayOfWeek: Int = 1,
        val highlightDayOfWeek: Int = 0,
        val totalWeeks: Int = 20,
        val currentPage: WeekPageData = WeekPageData(),
        val allSchedules: List<Schedule> = emptyList(),
        val scheduleChanges: List<ScheduleChange> = emptyList(),
        val allCourseMap: Map<Long, Course> = emptyMap(),
        val presets: List<Semester> = emptyList(),
        val maxScheduledPeriod: Int = 0,
        val hasWeekendCourses: Boolean = false
    )

    @Immutable
    data class ConflictInfo(
        val courseName: String,
        val dayOfWeek: Int,
        val period: Int
    )

    @Immutable
    data class ImportConflict(
        val importedCourseName: String,
        val existingName: String,
        val sourceRow: Int,
        val schedule: ImportedSchedule
    )

    private val _selectedWeek = MutableStateFlow(0)
    private val _highlightDayOfWeek = MutableStateFlow(0)

    init {
        consumeNavigationState()
    }

    private fun consumeNavigationState() {
        // Set highlight FIRST so combine sees it when _selectedWeek triggers
        val targetDay = NavigationState.targetDayOfWeek
        if (targetDay > 0) {
            _highlightDayOfWeek.value = targetDay
            NavigationState.targetDayOfWeek = 0
        }
        val targetWeek = NavigationState.targetWeek
        if (targetWeek > 0) {
            _selectedWeek.value = targetWeek
            NavigationState.targetWeek = 0
        }
    }

    fun consumeTargetWeek() {
        consumeNavigationState()
    }

    fun clearHighlight() {
        _highlightDayOfWeek.value = 0
    }

    fun selectWeek(week: Int, highlightDay: Int = 0) {
        if (highlightDay > 0) _highlightDayOfWeek.value = highlightDay
        _selectedWeek.value = week.coerceIn(1, uiState.value.totalWeeks.coerceAtLeast(1))
    }

    /** 只保留 UI 实际消费的字段（roomMap 直传 + weekRange）；各周时段过滤改由页面按 week 局部计算 */
    private fun buildWeekPage(
        roomMap: Map<Long, String>,
        semester: Semester,
        week: Int
    ): WeekPageData {
        if (week < 1 || week > semester.totalWeeks) return WeekPageData(roomMap = roomMap)
        val range = DateUtils.getWeekRange(semester.startDate, week)
        val rangeStr = Instant.ofEpochMilli(range.first).atZone(Zone).format(FMT_FULL) + " ~ " +
            Instant.ofEpochMilli(range.second).atZone(Zone).format(FMT_SHORT)
        return WeekPageData(roomMap = roomMap, weekRange = rangeStr)
    }

    companion object {
        private val FMT_FULL = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        private val FMT_SHORT = DateTimeFormatter.ofPattern("MM.dd")
        private val Zone = ZoneId.systemDefault()
    }

    val uiState: StateFlow<WeekUiState> = repository.getCurrentSemester()
        .flatMapLatest { semester ->
            if (semester == null) {
                flowOf(WeekUiState())
            } else {
                combine(
                    repository.getSchedulesBySemester(semester.id),
                    repository.getCoursesBySemester(semester.id).map { it.associateBy { c -> c.id } },
                    repository.getAllRooms().map { it.associateBy({ r -> r.id }, { r -> r.name }) },
                    repository.getAllSemesters(),
                    combine(_selectedWeek, _highlightDayOfWeek, repository.getScheduleChanges(semester.id)) { w, h, changes ->
                        Triple(w, h, changes)
                    }
                ) { schedules, courseMap, roomMap, presets, weekAndHighlight ->
                    val (selectedWeek, highlightDay, changes) = weekAndHighlight
                    val now = System.currentTimeMillis()
                    val currentWeek = DateUtils.getWeekNumber(now, semester.startDate)
                    val dow = DateUtils.getDayOfWeek(now)
                    val displayWeek = (if (selectedWeek > 0) selectedWeek else currentWeek)
                        .coerceIn(1, semester.totalWeeks)
                    val currentPage = buildWeekPage(roomMap, semester, displayWeek)
                    val maxPeriod = schedules.maxOfOrNull { it.endPeriod } ?: 0
                    val hasWeekend = schedules.any { it.dayOfWeek > 5 }
                    WeekUiState(
                        semester = semester,
                        currentWeek = currentWeek,
                        selectedWeek = displayWeek,
                        currentDayOfWeek = dow,
                        highlightDayOfWeek = highlightDay,
                        totalWeeks = semester.totalWeeks,
                        currentPage = currentPage,
                        allSchedules = schedules,
                        scheduleChanges = changes,
                        allCourseMap = courseMap,
                        presets = presets,
                        maxScheduledPeriod = maxPeriod,
                        hasWeekendCourses = hasWeekend
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekUiState())

    suspend fun saveOccurrenceChange(change: ScheduleChange, allowConflict: Boolean): List<String> {
        val semester = repository.getCurrentSemester().first() ?: error("当前学期不存在")
        val schedules = repository.getSchedulesBySemester(semester.id).first()
        val source = schedules.firstOrNull { it.id == change.scheduleId } ?: error("原排课已删除")
        val start = Instant.ofEpochMilli(semester.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val original = LocalDate.ofEpochDay(change.originalDay)
        require(scheduleOccurrences(original, start, semester.totalWeeks, listOf(source), emptyList()).isNotEmpty()) {
            "这一天没有原排课"
        }
        require(change.startPeriod in 1..semester.periodCount &&
            change.endPeriod in change.startPeriod..semester.periodCount) { "节次超出学期范围" }
        val target = change.targetDay?.let(LocalDate::ofEpochDay)
        if (target != null) {
            val monday = start.minusDays((start.dayOfWeek.value - 1).toLong())
            require(!target.isBefore(monday) && target.isBefore(monday.plusWeeks(semester.totalWeeks.toLong()))) {
                "目标日期必须在当前学期内"
            }
            val changes = repository.getScheduleChanges(semester.id).first().filterNot {
                it.scheduleId == change.scheduleId && it.originalDay == change.originalDay
            } + change
            val overlapping = scheduleOccurrences(target, start, semester.totalWeeks, schedules, changes)
                .filter { it.change != change && it.schedule.startPeriod <= change.endPeriod &&
                    it.schedule.endPeriod >= change.startPeriod }
            if (overlapping.isNotEmpty() && !allowConflict) {
                val names = repository.getCoursesBySemester(semester.id).first().associate { it.id to it.name }
                return overlapping.map { names[it.schedule.courseId] ?: "课程" }.distinct()
            }
        }
        repository.saveScheduleChange(change)
        return emptyList()
    }

    suspend fun captureBackup(): ScheduleBackup = repository.captureBackup()
    suspend fun exportSemester(output: OutputStream) = repository.exportSemester(output)
    suspend fun hasRestoreSnapshot(): Boolean = repository.hasRestoreSnapshot()

    suspend fun restoreBackup(backup: ScheduleBackup) = withContext(NonCancellable) {
        repository.restoreBackup(backup)
        refreshAfterRestore()
    }

    suspend fun undoRestore() = withContext(NonCancellable) {
        repository.undoRestore()
        refreshAfterRestore()
    }

    private suspend fun refreshAfterRestore() {
        _selectedWeek.value = 0
        _highlightDayOfWeek.value = 0
        ExamReminderWorker.rescheduleAll(
            applicationContext, repository.getExamDao(), androidx.work.ExistingWorkPolicy.REPLACE
        )
    }

    suspend fun removeOccurrenceChange(scheduleId: Long, originalDay: Long) =
        repository.removeScheduleChange(scheduleId, originalDay)

    fun selectSemester(semester: Semester) {
        _selectedWeek.value = 0
        _highlightDayOfWeek.value = 0
        SettingsPrefs.selectSemester(semester.id)
    }

    fun createSemester(name: String, startDate: Long, totalWeeks: Int, periodCount: Int, weekDays: Int, periodTimesJson: String) {
        viewModelScope.launch {
            val semester = Semester(
                name = name, startDate = startDate, totalWeeks = totalWeeks,
                periodCount = periodCount, weekDays = weekDays, periodTimesJson = periodTimesJson
            )
            val id = repository.insertSemester(semester)
            selectSemester(semester.copy(id = id))
        }
    }

    fun saveSemester(name: String, startDateMillis: Long, totalWeeks: Int, periodCount: Int, weekDays: Int, periodTimesJson: String) {
        viewModelScope.launch {
            val isNew = repository.getCurrentSemester().first() == null
            repository.saveSemesterCurrent(name, startDateMillis, totalWeeks, periodCount, weekDays, periodTimesJson)
            if (isNew) _selectedWeek.value = 0
        }
    }

    fun deletePreset(semester: Semester) {
        viewModelScope.launch {
            repository.deleteSemester(semester)
        }
    }

    fun addCourse(
        dayOfWeek: Int, name: String, teacher: String, room: String,
        weeks: Set<Int>, startPeriod: Int, endPeriod: Int
    ) {
        viewModelScope.launch {
            val semester = repository.getCurrentSemester().first() ?: return@launch
            val validWeeks = weeks.filter { it in 1..semester.totalWeeks }.toSet()
            if (validWeeks.isEmpty()) return@launch
            val roomId = if (room.isNotBlank()) repository.insertRoom(Room(name = room)) else null
            val course = Course(
                semesterId = semester.id, name = name, teacher = teacher,
                color = "0"
            )
            val schedules = groupWeeksForSchedules(validWeeks).map { span ->
                Schedule(
                    courseId = 0, dayOfWeek = dayOfWeek, startPeriod = startPeriod,
                    endPeriod = endPeriod, startWeek = span.startWeek, endWeek = span.endWeek,
                    weekType = span.weekType, roomId = roomId
                )
            }
            repository.insertCourseWithSchedules(course, schedules)
        }
    }

    fun updateCourseAndSchedule(
        courseId: Long, scheduleId: Long,
        name: String, teacher: String, room: String,
        dayOfWeek: Int, weeks: Set<Int>, startPeriod: Int, endPeriod: Int
    ) {
        viewModelScope.launch {
            val course = repository.getCourseById(courseId) ?: return@launch
            val schedules = repository.getSchedulesByCourse(courseId)
            val target = schedules.firstOrNull { it.id == scheduleId } ?: return@launch
            val validWeeks = weeks.filter { it > 0 }.toSet()
            if (validWeeks.isEmpty()) return@launch
            val group = schedules.filter {
                it.dayOfWeek == target.dayOfWeek && it.startPeriod == target.startPeriod &&
                    it.endPeriod == target.endPeriod && it.roomId == target.roomId
            }
            val newSchedules = groupWeeksForSchedules(validWeeks).map { span ->
                Schedule(
                    courseId = courseId, dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod, endPeriod = endPeriod,
                    startWeek = span.startWeek, endWeek = span.endWeek,
                    weekType = span.weekType
                )
            }
            repository.replaceSchedules(course.copy(name = name, teacher = teacher), group, newSchedules, room)
        }
    }

    /** 删除编辑弹窗对应的单个排课时段；仅剩该时段时课程一并删除 */
    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            repository.deleteScheduleWithCourseIfOrphan(scheduleId)
        }
    }

    /** 清除所有课程/课表/考试/教室数据（保留学期设置） */
    fun clearAllCourseData() {
        viewModelScope.launch {
            repository.clearAllCourseData()
            ExamReminderWorker.cancelAll(applicationContext)
        }
    }

    suspend fun importParsedCourses(
        semesterId: Long, courses: List<ImportedCourse>, updates: List<ImportUpdate>
    ): CourseImportResult {
        val semester = repository.getSemesterById(semesterId) ?: error("目标学期已删除，请重新选择课表")
        val validCourses = courses.mapNotNull { course ->
            // 丢弃越界/颠倒的时段（day 1..7、节次 1..periodCount、周次 1..totalWeeks），避免脏数据入库
            val schedPairs = course.schedules
                .filter { isWithinSemester(it, semester) }
                .map { s ->
                    Schedule(
                        courseId = 0,
                        dayOfWeek = s.dayOfWeek,
                        startPeriod = s.startPeriod,
                        endPeriod = s.endPeriod,
                        startWeek = s.startWeek,
                        endWeek = s.endWeek,
                        weekType = s.weekType
                    ) to s.roomName
                }
            if (schedPairs.isEmpty()) null else Triple(course.name, course.teacher, schedPairs)
        }
        return repository.importCourses(semester.id, validCourses, updates)
    }

    data class ImportPreview(
        val courses: List<ImportedCourse>,
        val issues: List<ImportRowIssue>,
        val duplicates: Int,
        val updates: List<ImportUpdate>,
        val conflicts: List<ImportConflict>,
        val semesterId: Long,
        val semesterName: String
    ) {
        val scheduleCount = courses.sumOf { it.schedules.size }
        val conflictedSlots = conflicts.map { it.sourceRow to it.schedule }.toSet()
    }

    suspend fun previewImport(parsed: ParsedCourseImport): ImportPreview {
        val semester = repository.getCurrentSemester().first() ?: error("请先设置当前学期")
        val existing = repository.getSchedulesBySemester(semester.id).first()
        val currentCourses = repository.getCoursesBySemester(semester.id).first().associateBy { it.id }
        val byName = currentCourses.values.groupBy { it.name }
        val rooms = repository.getAllRoomsOnce().associate { it.id to it.name }
        val bySlot = existing.groupBy { schedule ->
            currentCourses[schedule.courseId]?.name to ImportedSchedule(
                schedule.dayOfWeek, schedule.startPeriod, schedule.endPeriod,
                schedule.startWeek, schedule.endWeek, schedule.weekType, ""
            )
        }
        val issues = parsed.issues.toMutableList()
        val pendingRooms = mutableListOf<ImportUpdate.Room>()
        val pendingTeachers = mutableMapOf<Course, MutableList<Pair<Int, String>>>()
        val seen = mutableMapOf<Pair<String, ImportedSchedule>, String>()
        var duplicates = 0
        val courses = parsed.courses.mapNotNull { course ->
            val matches = byName[course.name].orEmpty()
            val currentCourse = matches.singleOrNull()
            val teacherDiffers = currentCourse != null && course.teacher.isNotBlank() &&
                currentCourse.teacher != course.teacher
            var validSlot = false
            val valid = course.schedules.filter { schedule ->
                if (!isWithinSemester(schedule, semester)) {
                    issues.add(ImportRowIssue(course.sourceRow, "排课节次或周次超出学期范围"))
                    return@filter false
                }
                validSlot = true
                val slot = course.name to schedule.copy(roomName = "")
                val stored = bySlot[slot].orEmpty()
                if (stored.isNotEmpty()) {
                    val target = stored.singleOrNull()
                    val oldRoom = target?.roomId?.let { rooms[it] }.orEmpty()
                    if (schedule.roomName.isNotBlank()) {
                        if (target != null && currentCourse != null) {
                            pendingRooms.add(ImportUpdate.Room(
                                currentCourse, target, oldRoom, schedule.roomName, course.sourceRow
                            ))
                        } else if (schedule.roomName != oldRoom) {
                            issues.add(ImportRowIssue(course.sourceRow, "同名时段匹配到多条排课，已保留原教室"))
                        }
                    }
                    if (!teacherDiffers && (schedule.roomName.isBlank() || schedule.roomName == oldRoom)) {
                        duplicates++
                    }
                    return@filter false
                }
                val otherRoom = seen.putIfAbsent(slot, schedule.roomName)
                if (otherRoom == null) return@filter true
                if (schedule.roomName.isNotBlank() && schedule.roomName != otherRoom) {
                    issues.add(ImportRowIssue(course.sourceRow, "文件内相同时段的教室不同，已保留首条"))
                } else duplicates++
                false
            }
            if (validSlot && course.teacher.isNotBlank() && currentCourse != null) {
                pendingTeachers.getOrPut(currentCourse) { mutableListOf() }
                    .add(course.sourceRow to course.teacher)
            }
            if (valid.isEmpty()) null else course.copy(schedules = valid)
        }
        val updates = mutableListOf<ImportUpdate>()
        for ((course, candidates) in pendingTeachers) {
            val names = candidates.map { it.second }.distinct()
            if (names.size == 1 && names.first() != course.teacher) {
                updates.add(ImportUpdate.Teacher(course, names.first()))
            } else if (names.size > 1) {
                issues.add(ImportRowIssue(candidates.first().first, "${course.name} 的教师填写不一致，已保留原教师"))
            }
        }
        for (candidates in pendingRooms.groupBy { it.schedule.id }.values) {
            val first = candidates.first()
            if (candidates.any { it.newName != first.newName }) {
                issues.add(ImportRowIssue(first.sourceRow, "${first.course.name} 的教室填写不一致，已保留原教室"))
            } else if (first.newName != first.oldName) {
                updates.add(first)
            }
        }
        return ImportPreview(
            courses, issues.sortedBy { it.row }, duplicates, updates,
            checkImportConflicts(courses, existing, currentCourses.mapValues { it.value.name }),
            semester.id, semester.name
        )
    }

    private fun isWithinSemester(schedule: ImportedSchedule, semester: Semester): Boolean =
        schedule.dayOfWeek in 1..7 &&
            schedule.startPeriod in 1..semester.periodCount &&
            schedule.endPeriod in schedule.startPeriod..semester.periodCount &&
            schedule.startWeek in 1..semester.totalWeeks &&
            schedule.endWeek in schedule.startWeek..semester.totalWeeks

    /** 比对已有排课及文件中靠前的排课，跳过冲突时优先保留先出现的课程。 */
    private fun checkImportConflicts(
        courses: List<ImportedCourse>, schedules: List<Schedule>, courseNames: Map<Long, String>
    ): List<ImportConflict> {
        val byDay = mutableMapOf<Int, MutableList<Pair<String, ImportedSchedule>>>()
        for (schedule in schedules) {
            val name = courseNames[schedule.courseId] ?: "课程"
            byDay.getOrPut(schedule.dayOfWeek) { mutableListOf() }.add(name to ImportedSchedule(
                schedule.dayOfWeek, schedule.startPeriod, schedule.endPeriod,
                schedule.startWeek, schedule.endWeek, schedule.weekType, ""
            ))
        }
        val conflicts = mutableListOf<ImportConflict>()
        for (course in courses) {
            for (s in course.schedules) {
                val weeks = weeksForSpan(s.startWeek, s.endWeek, s.weekType)
                val candidates = byDay.getOrPut(s.dayOfWeek) { mutableListOf() }
                var conflictsWithEarlier = false
                for ((name, existing) in candidates) {
                    val periodOverlap = s.startPeriod <= existing.endPeriod && s.endPeriod >= existing.startPeriod
                    if (!periodOverlap) continue
                    if (!selectedWeeksOverlap(weeks, existing.startWeek, existing.endWeek, existing.weekType)) continue
                    conflictsWithEarlier = true
                    conflicts.add(
                        ImportConflict(
                            importedCourseName = course.name,
                            existingName = name,
                            sourceRow = course.sourceRow,
                            schedule = s
                        )
                    )
                }
                if (!conflictsWithEarlier) candidates.add(course.name to s)
            }
        }
        return conflicts
            .distinct()
            .sortedWith(compareBy<ImportConflict> { it.schedule.dayOfWeek }.thenBy { it.schedule.startPeriod })
    }

    suspend fun checkConflict(
        dayOfWeek: Int, startPeriod: Int, endPeriod: Int,
        weeks: Set<Int>, excludeScheduleIds: Set<Long> = emptySet()
    ): List<ConflictInfo> {
        val semester = repository.getCurrentSemester().first() ?: return emptyList()
        val schedules = repository.getSchedulesBySemester(semester.id).first()
            .filterNot { it.id in excludeScheduleIds }
        val changes = repository.getScheduleChanges(semester.id).first()
        val courseMap = repository.getCoursesBySemester(semester.id).first().associateBy { it.id }
        val start = Instant.ofEpochMilli(semester.startDate).atZone(ZoneId.systemDefault()).toLocalDate()
        val monday = start.minusDays((start.dayOfWeek.value - 1).toLong())
        val conflicts = linkedSetOf<ConflictInfo>()
        for (week in weeks) {
            if (week !in 1..semester.totalWeeks) continue
            val date = monday.plusWeeks((week - 1).toLong()).plusDays((dayOfWeek - 1).toLong())
            for (occurrence in scheduleOccurrences(date, start, semester.totalWeeks, schedules, changes)) {
                val existing = occurrence.schedule
                if (startPeriod > existing.endPeriod || endPeriod < existing.startPeriod) continue
                val name = courseMap[existing.courseId]?.name ?: "课程"
                conflicts.add(ConflictInfo(name, dayOfWeek, existing.startPeriod))
            }
        }
        return conflicts.toList()
    }
}
