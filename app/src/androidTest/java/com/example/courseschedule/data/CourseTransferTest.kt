package com.example.courseschedule.data

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.courseschedule.data.db.AppDatabase
import com.example.courseschedule.data.db.ScheduleBackup
import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Exam
import com.example.courseschedule.data.db.entity.Room as CourseRoom
import com.example.courseschedule.data.db.entity.Schedule
import com.example.courseschedule.data.db.entity.ScheduleChange
import com.example.courseschedule.data.db.entity.Semester
import com.example.courseschedule.data.importer.ImportedCourse
import com.example.courseschedule.data.importer.ImportedSchedule
import com.example.courseschedule.data.importer.ParsedCourseImport
import com.example.courseschedule.data.importer.XlsxCourseParser
import com.example.courseschedule.data.repository.CourseRepository
import com.example.courseschedule.ui.screen.week.WeekViewModel
import com.example.courseschedule.util.SettingsPrefs
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
class CourseTransferTest {
    @Test
    fun unsuccessfulRestoreLeavesPreviousUndoAvailableAfterRepositoryRecreation() = runBlocking {
        withRepository { repository, _ ->
            val repo = repository()
            val semester = Semester(name = "原学期", startDate = 0, totalWeeks = 20)
            val semesterId = repo.insertSemester(semester)
            val courseId = repo.insertCourse(Course(semesterId = semesterId, name = "原课程", teacher = "", color = "0"))
            val scheduleId = repo.insertSchedule(Schedule(
                courseId = courseId, dayOfWeek = 1, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16
            ))
            val examId = repo.insertExam(Exam(courseId = courseId, examDate = 1_800_000_000_000L, notes = "原考试"))
            repo.saveScheduleChange(ScheduleChange(scheduleId, 4, 5, 1, 2, "新教室"))
            SettingsPrefs.selectSemester(semesterId)
            val before = JSONObject(repo.captureBackup().encode().toString(Charsets.UTF_8))

            val incoming = JSONObject(before.toString()).apply {
                getJSONObject("tables").getJSONArray("semesters").getJSONArray(0).put(1, "恢复后的学期")
                getJSONObject("tables").getJSONArray("courses").getJSONArray(0).put(2, "恢复后的课程")
                remove("createdAt")
                remove("selectedSemesterId")
            }
            fun decode(data: JSONObject) = ScheduleBackup.read(data.toString().byteInputStream())
            repo.restoreBackup(decode(incoming))
            assertEquals("恢复后的课程", repo.getCourseById(courseId)?.name)

            val broken = JSONObject(incoming.toString()).apply {
                getJSONObject("tables").getJSONArray("schedules").getJSONArray(0).put(1, courseId + 100)
            }
            val invalidBackup = decode(broken)
            assertTrue(runCatching { repo.restoreBackup(invalidBackup) }.isFailure)
            assertEquals("恢复后的课程", repo.getCourseById(courseId)?.name)

            val reopened = repository()
            assertTrue(reopened.hasRestoreSnapshot())
            reopened.undoRestore()
            assertEquals("原学期", reopened.getSemesterById(semesterId)?.name)
            assertEquals("原课程", reopened.getCourseById(courseId)?.name)
            assertEquals("原考试", reopened.getExamDao().getById(examId)?.notes)
            assertEquals("新教室", reopened.getScheduleChanges(semesterId).first().single().roomName)
            assertEquals(semesterId, SettingsPrefs.selectedSemesterId.value)
            assertFalse(reopened.hasRestoreSnapshot())
        }
    }

    @Test
    fun reimportAppliesOnlyConfirmedChangesAndExportsSelectedSemester() = runBlocking {
        withRepository { repository, context ->
            val repo = repository()
            val vm = WeekViewModel(repo, context)
            try {
                val semester = Semester(name = "当前学期", startDate = 0, totalWeeks = 20)
                val semesterId = repo.insertSemester(semester)
                val otherSemesterId = repo.insertSemester(semester.copy(name = "另一学期", startDate = 1_000_000_000))
                SettingsPrefs.selectSemester(semesterId)
                val courseId = repo.insertCourse(Course(
                    semesterId = semesterId, name = "数学", teacher = "原教师", color = "0"
                ))
                val roomId = repo.insertRoom(CourseRoom(name = "原教室"))
                val scheduleId = repo.insertSchedule(Schedule(
                    courseId = courseId, dayOfWeek = 1, startPeriod = 1, endPeriod = 2,
                    startWeek = 1, endWeek = 16, roomId = roomId
                ))
                val otherCourseId = repo.insertCourse(Course(
                    semesterId = otherSemesterId, name = "另一学期的课程", teacher = "", color = "0"
                ))
                repo.insertSchedule(Schedule(
                    courseId = otherCourseId, dayOfWeek = 3, startPeriod = 1, endPeriod = 2,
                    startWeek = 1, endWeek = 16
                ))
                repo.saveScheduleChange(ScheduleChange(scheduleId, 4, 5, 1, 2, "临时教室"))
                val slot = ImportedSchedule(1, 1, 2, 1, 16, 0, "新教室")
                val file = ParsedCourseImport(
                    listOf(
                        ImportedCourse("数学", "新教师", listOf(slot), 2),
                        ImportedCourse("英语", "第二位教师", listOf(slot.copy(dayOfWeek = 2)), 3)
                    ),
                    emptyList()
                )

                val initial = vm.previewImport(file)
                assertEquals(2, initial.updates.size)
                assertEquals(listOf("英语"), initial.courses.map { it.name })
                vm.importParsedCourses(semesterId, initial.courses, emptyList())
                assertEquals("原教师", repo.getCourseById(courseId)?.teacher)
                assertEquals(roomId, repo.getSchedulesByCourse(courseId).single().roomId)

                val pending = vm.previewImport(file)
                assertEquals(1, pending.duplicates)
                assertTrue(pending.courses.isEmpty())
                val applied = vm.importParsedCourses(semesterId, emptyList(), pending.updates)
                assertEquals(2, applied.updateCount)
                val unchanged = vm.previewImport(file)
                assertEquals(2, unchanged.duplicates)
                assertTrue(unchanged.updates.isEmpty())
                assertEquals(scheduleId, repo.getScheduleChanges(semesterId).first().single().scheduleId)

                val exported = ByteArrayOutputStream().also { repo.exportSemester(it) }
                val courses = XlsxCourseParser.parse(exported.toByteArray().inputStream()).single().data.courses
                assertEquals(setOf("数学", "英语"), courses.map { it.name }.toSet())
                val math = courses.single { it.name == "数学" }
                assertEquals("新教师", math.teacher)
                assertEquals(slot, math.schedules.single())
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    private suspend fun withRepository(
        test: suspend (() -> CourseRepository, ContextWrapper) -> Unit
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testContext = instrumentation.context
        val directory = Files.createTempDirectory(testContext.cacheDir.toPath(), "transfer-test").toFile()
        val context = object : ContextWrapper(testContext) {
            override fun getNoBackupFilesDir() = directory
        }
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        SettingsPrefs.init(context)
        try {
            test({
                CourseRepository(
                    database, database.semesterDao(), database.courseDao(), database.scheduleDao(),
                    database.roomDao(), database.examDao(), context
                )
            }, context)
        } finally {
            SettingsPrefs.init(instrumentation.targetContext)
            database.close()
            directory.deleteRecursively()
        }
    }
}
