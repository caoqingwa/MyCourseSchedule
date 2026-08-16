package com.example.courseschedule.data.importer

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.regex.Pattern

/** 从 xlsx 解析出的一门课程（含全部排课时段） */
data class ImportedCourse(
    val name: String,
    val teacher: String,
    val schedules: List<ImportedSchedule>
)

/** 单个排课时段 */
data class ImportedSchedule(
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: Int, // 0 全周 / 1 单周 / 2 双周
    val roomName: String
)

/**
 * 解析课程表 xlsx。按表头名匹配列（列顺序可打乱），跳过课程设计类（名称含"课程设计"或上课时间为空）。
 * xlsx 为 zip 包：sharedStrings.xml 存文本，sheet1.xml 引用索引。
 */
object XlsxCourseParser {

    // 表头别名：标准名 -> 可能的列名
    private val HEADER_ALIASES = mapOf(
        "name" to listOf("课程名称", "课程名"),
        "teacher" to listOf("教师姓名", "任课教师", "教师", "老师"),
        "time" to listOf("上课时间", "时间", "课程时间"),
        "place" to listOf("教学地点", "地点", "教室", "上课地点"),
        "weeks" to listOf("起始结束周", "起止周", "周次")
    )

    private val DAY_MAP = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7
    )

    // 匹配 "星期四第9-11节{3-11周(单)}" 或 "第9-11节{1-16周}"（无星期前缀时补默认）
    private val TIME_PATTERN = Pattern.compile(
        "(?:星期([一二三四五六日]))?第(\\d+)-(\\d+)节\\{(\\d+)-(\\d+)周(?:\\(([单双])\\))?}"
    )

    /** 解析 xlsx 输入流，返回课程列表 */
    fun parse(input: InputStream): List<ImportedCourse> {
        val sharedStrings = readSharedStrings(input)
        val rows = readSheetRows(input, sharedStrings)
        return rowsToCourses(rows)
    }

    /** 读取 xl/sharedStrings.xml 全部文本 */
    private fun readSharedStrings(zipInput: InputStream): List<String> {
        val result = mutableListOf<String>()
        ZipInputStream(zipInput).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/sharedStrings.xml") {
                    collectSharedStrings(zip, result)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun collectSharedStrings(input: InputStream, out: MutableList<String>) {
        val parser = newParser(input)
        var text = StringBuilder()
        var inT = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "t") {
                        inT = true
                        text = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> if (inT) text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    if (parser.name == "t") inT = false
                    else if (parser.name == "si") out.add(text.toString())
                }
            }
            event = parser.next()
        }
    }

    /** 读取第一个 worksheet 的单元格，返回 List<List<String>>（含表头行） */
    private fun readSheetRows(zipInput: InputStream, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        ZipInputStream(zipInput).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("xl/worksheets/") && entry.name.endsWith(".xml")) {
                    parseSheet(zip, shared, rows)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return rows
    }

    private fun parseSheet(input: InputStream, shared: List<String>, rows: MutableList<List<String>>) {
        val parser = newParser(input)
        var currentRow = mutableListOf<Pair<String, String>>() // 列字母 -> 值
        var inRow = false
        var cellRef = ""
        var cellType = ""
        var inV = false
        var text = StringBuilder()

        fun flushCell() {
            val value = text.toString()
            if (cellRef.isNotEmpty()) currentRow.add(cellRef to value)
            cellRef = ""; cellType = ""; inV = false; text = StringBuilder()
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = mutableListOf(); inRow = true
                    }
                    "c" -> {
                        cellRef = parser.getAttributeValue(null, "r") ?: ""
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                    }
                    "v" -> inV = true
                    "t" -> inV = true // inlineStr 情况
                }
                XmlPullParser.TEXT -> if (inV) text.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> flushCell()
                    "row" -> {
                        if (inRow) {
                            // 按列字母排序补齐缺失单元格
                            rows.add(expandRow(currentRow))
                            inRow = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        // 最后一个 c 未闭合时兜底
        if (cellRef.isNotEmpty()) flushCell()
    }

    /** 把稀疏 (列字母,值) 展开为按列索引 0..max 的列表 */
    private fun expandRow(cells: List<Pair<String, String>>): List<String> {
        if (cells.isEmpty()) return emptyList()
        val maxCol = cells.maxOf { colIndex(it.first) }
        val arr = MutableList(maxCol + 1) { "" }
        for ((ref, v) in cells) {
            val idx = colIndex(ref)
            if (idx <= maxCol) arr[idx] = v
        }
        return arr
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1)
            else break
        }
        return n - 1
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        return parser
    }

    /** 表头行 + 数据行 -> 课程列表 */
    private fun rowsToCourses(rows: List<List<String>>): List<ImportedCourse> {
        if (rows.isEmpty()) return emptyList()
        val header = rows[0]
        // 表头名 -> 列索引
        val colMap = mutableMapOf<String, Int>()
        for ((idx, h) in header.withIndex()) {
            val key = headerKey(h)
            if (key != null && key !in colMap) colMap[key] = idx
        }
        val nameIdx = colMap["name"] ?: return emptyList()
        val teacherIdx = colMap["teacher"] ?: -1
        val timeIdx = colMap["time"] ?: -1
        val placeIdx = colMap["place"] ?: -1
        val weeksIdx = colMap["weeks"] ?: -1

        val courses = mutableListOf<ImportedCourse>()
        for (r in 1 until rows.size) {
            val row = rows[r]
            if (row.size <= nameIdx) continue
            val name = row[nameIdx].trim()
            if (name.isEmpty()) continue
            // 课程设计类跳过
            if (name.contains("课程设计") || name.contains("课设")) continue

            val timeRaw = row.getOrNull(timeIdx)?.trim() ?: ""
            if (timeRaw.isEmpty()) continue

            val teacher = row.getOrNull(teacherIdx)?.trim() ?: ""
            val places = row.getOrNull(placeIdx)?.split(';')?.map { it.trim() } ?: emptyList()
            val segments = timeRaw.split(';')
            val schedules = mutableListOf<ImportedSchedule>()
            for ((i, seg) in segments.withIndex()) {
                val s = parseTimeSegment(seg, places.getOrNull(i) ?: "")
                if (s != null) schedules.add(s)
            }
            if (schedules.isNotEmpty()) courses.add(ImportedCourse(name, teacher, schedules))
        }
        return courses
    }

    private fun headerKey(raw: String): String? {
        val t = raw.trim()
        for ((key, aliases) in HEADER_ALIASES) {
            if (t in aliases) return key
        }
        return null
    }

    private fun parseTimeSegment(seg: String, room: String): ImportedSchedule? {
        val m = TIME_PATTERN.matcher(seg.trim())
        if (!m.find()) return null
        val day = m.group(1)?.let { DAY_MAP[it[0]] } ?: 1
        val sp = m.group(2).toInt()
        val ep = m.group(3).toInt()
        val sw = m.group(4).toInt()
        val ew = m.group(5).toInt()
        val wt = when (m.group(6)) {
            "单" -> 1
            "双" -> 2
            else -> 0
        }
        return ImportedSchedule(day, sp, ep, sw, ew, wt, room)
    }
}
