package com.example.courseschedule.data.importer

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.util.zip.ZipInputStream
import java.util.regex.Pattern

/** 从 xlsx 解析出的一门课程（含全部排课时段） */
data class ImportedCourse(
    val name: String,
    val teacher: String,
    val schedules: List<ImportedSchedule>,
    val sourceRow: Int
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

data class ImportRowIssue(val row: Int, val reason: String)
data class ParsedCourseImport(val courses: List<ImportedCourse>, val issues: List<ImportRowIssue>)
data class ImportWorksheet(val name: String, val data: ParsedCourseImport)

/**
 * 解析包含「课程名称」和「上课时间」表头的工作表，保留 Excel 原始行号。
 */
object XlsxCourseParser {
    private const val MAX_UNCOMPRESSED_BYTES = 16 * 1024 * 1024
    private const val MAX_EXPANDED_CELLS = 1_000_000
    private const val XLSX_MAX_COLUMNS = 16_384

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
    // 注意：Android 使用 ICU 正则引擎，孤立的 "}" 必须转义为 "\}"，否则 PatternSyntaxException
    private val TIME_PATTERN = Pattern.compile(
        "(?:星期([一二三四五六日]))?第(\\d+)-(\\d+)节\\{(\\d+)-(\\d+)周(?:\\(([单双])\\))?\\}"
    )

    fun parse(input: InputStream): List<ImportWorksheet> {
        val metadata = setOf("xl/sharedStrings.xml", "xl/workbook.xml", "xl/_rels/workbook.xml.rels")
        val parts = linkedMapOf<String, ByteArrayOutputStream>()
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var total = 0
            val buffer = ByteArray(8192)
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                val destination = if (name in metadata ||
                    name.startsWith("xl/worksheets/") && name.endsWith(".xml") && "/_rels/" !in name
                ) ByteArrayOutputStream().also { parts[name] = it } else null
                while (true) {
                    val size = zip.read(buffer)
                    if (size < 0) break
                    total += size
                    require(total <= MAX_UNCOMPRESSED_BYTES) { "课表文件解压后超过 16 MB" }
                    destination?.write(buffer, 0, size)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val shared = parts.remove("xl/sharedStrings.xml")
            ?.let { parseSharedStrings(ByteArrayInputStream(it.toByteArray())) }.orEmpty()
        val workbook = parts.remove("xl/workbook.xml")
        val relations = parts.remove("xl/_rels/workbook.xml.rels")
        val sheets = if (workbook != null && relations != null) {
            worksheetPaths(workbook.toByteArray(), relations.toByteArray())
        } else parts.keys.sorted().map { it.substringAfterLast('/').substringBeforeLast('.') to it }

        var remainingCells = MAX_EXPANDED_CELLS
        val matches = sheets.mapNotNull { (name, path) ->
            val worksheet = parts.remove(path) ?: return@mapNotNull null
            val rows = parseSheet(ByteArrayInputStream(worksheet.toByteArray()), shared, remainingCells)
            remainingCells -= rows.sumOf { it.second.size }
            rowsToCourses(rows)?.let { ImportWorksheet(name, it) }
        }
        require(matches.isNotEmpty()) {
            "未找到同时包含「课程名称」和「上课时间」的工作表，可在设置下载 Excel 模板"
        }
        return matches
    }

    private fun worksheetPaths(workbook: ByteArray, relations: ByteArray): List<Pair<String, String>> {
        val targets = mutableMapOf<String, String>()
        val rels = newParser(ByteArrayInputStream(relations))
        while (rels.next() != XmlPullParser.END_DOCUMENT) {
            if (rels.eventType != XmlPullParser.START_TAG || rels.name != "Relationship") continue
            val type = rels.getAttributeValue(null, "Type") ?: continue
            if (!type.endsWith("/worksheet") || rels.getAttributeValue(null, "TargetMode") == "External") continue
            val id = rels.getAttributeValue(null, "Id") ?: continue
            val target = rels.getAttributeValue(null, "Target") ?: continue
            val path = URI("xl/workbook.xml").resolve(target).normalize().path ?: continue
            targets[id] = path.trimStart('/')
        }
        val sheets = mutableListOf<Pair<String, String>>()
        val book = newParser(ByteArrayInputStream(workbook))
        while (book.next() != XmlPullParser.END_DOCUMENT) {
            if (book.eventType != XmlPullParser.START_TAG || book.name != "sheet") continue
            val name = book.getAttributeValue(null, "name") ?: continue
            val id = (0 until book.attributeCount).firstOrNull { book.getAttributeName(it) == "id" }
                ?.let { book.getAttributeValue(it) } ?: continue
            val path = targets[id] ?: continue
            sheets.add(name to path)
        }
        return sheets
    }

    private fun parseSharedStrings(input: InputStream): List<String> {
        val out = mutableListOf<String>()
        collectSharedStrings(input, out)
        return out
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
                    } else if (parser.name == "si") {
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

    private fun parseSheet(input: InputStream, shared: List<String>, cellLimit: Int): List<Pair<Int, List<String>>> {
        val rows = mutableListOf<Pair<Int, List<String>>>()
        var rowNumber = 0
        var expandedCells = 0
        val parser = newParser(input)
        var currentRow = mutableListOf<Pair<String, String>>() // 列字母 -> 值
        var inRow = false
        var cellRef = ""
        var cellType = ""
        var inV = false
        var text = StringBuilder()

        fun flushCell() {
            val raw = text.toString()
            val value = if (cellType == "s") {
                // shared string：v 里是索引数字
                raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
            } else raw
            if (cellRef.isNotEmpty()) currentRow.add(cellRef to value)
            cellRef = ""; cellType = ""; inV = false; text = StringBuilder()
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        currentRow = mutableListOf(); inRow = true
                        rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowNumber + 1)
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
                            val row = expandRow(currentRow, cellLimit - expandedCells)
                            expandedCells += row.size
                            rows.add(rowNumber to row)
                            inRow = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        // 最后一个 c 未闭合时兜底
        if (cellRef.isNotEmpty()) flushCell()
        return rows
    }

    /** 把稀疏 (列字母,值) 展开为按列索引 0..max 的列表 */
    private fun expandRow(cells: List<Pair<String, String>>, remainingCells: Int): List<String> {
        if (cells.isEmpty()) return emptyList()
        val indexed = cells.mapNotNull { (ref, v) ->
            val idx = colIndex(ref)
            if (idx >= 0) idx to v else null
        }
        if (indexed.isEmpty()) return emptyList()
        val maxCol = indexed.maxOf { it.first }
        require(maxCol < remainingCells) { "工作表单元格过多" }
        val arr = MutableList(maxCol + 1) { "" }
        for ((idx, v) in indexed) {
            arr[idx] = v
        }
        return arr
    }

    private fun colIndex(ref: String): Int {
        if (ref.isEmpty()) return -1
        var n = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z') break
            n = n * 26 + (ch - 'A' + 1)
            require(n <= XLSX_MAX_COLUMNS) { "工作表列号超出范围" }
        }
        return n - 1
    }

    private fun newParser(input: InputStream): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(input, "UTF-8")
        return parser
    }

    /** 表头行 + 数据行 -> 课程列表 */
    private fun rowsToCourses(rows: List<Pair<Int, List<String>>>): ParsedCourseImport? {
        val headerIndex = rows.indexOfFirst { (_, cells) ->
            val names = cells.map { it.trim() }.toSet()
            HEADER_ALIASES.getValue("name").any(names::contains) &&
                HEADER_ALIASES.getValue("time").any(names::contains)
        }
        if (headerIndex < 0) return null
        val header = rows[headerIndex].second
        // 表头名 -> 列索引：按别名优先级匹配（如"教师姓名"优先于"任课教师"），列顺序可打乱
        val colMap = mutableMapOf<String, Int>()
        for ((key, aliases) in HEADER_ALIASES) {
            for (alias in aliases) {
                val idx = header.indexOfFirst { it.trim() == alias }
                if (idx >= 0) {
                    colMap[key] = idx
                    break
                }
            }
        }
        val nameIdx = colMap["name"] ?: error("缺少课程名称列")
        val teacherIdx = colMap["teacher"] ?: -1
        val timeIdx = colMap["time"] ?: error("缺少上课时间列")
        val placeIdx = colMap["place"] ?: -1
        val courses = mutableListOf<ImportedCourse>()
        val issues = mutableListOf<ImportRowIssue>()
        for ((number, row) in rows.subList(headerIndex + 1, rows.size)) {
            if (row.all { it.isBlank() }) continue
            val name = row.getOrNull(nameIdx)?.trim().orEmpty()
            if (name.isEmpty()) {
                issues.add(ImportRowIssue(number, "缺少课程名称"))
                continue
            }

            val timeRaw = row.getOrNull(timeIdx)?.trim() ?: ""
            if (timeRaw.isEmpty()) {
                issues.add(ImportRowIssue(number, "缺少上课时间"))
                continue
            }

            val teacher = row.getOrNull(teacherIdx)?.trim() ?: ""
            val segments = timeRaw.split(';')
            val place = row.getOrNull(placeIdx).orEmpty()
            val places = if (segments.size == 1) listOf(place.trim()) else place.split(';').map { it.trim() }
            val schedules = mutableListOf<ImportedSchedule>()
            for ((i, seg) in segments.withIndex()) {
                val s = parseTimeSegment(seg, places.getOrNull(i) ?: "")
                if (s != null) schedules.add(s)
                else if (seg.isNotBlank()) issues.add(ImportRowIssue(number, "无法识别第 ${i + 1} 个上课时段"))
            }
            if (schedules.isNotEmpty()) courses.add(ImportedCourse(name, teacher, schedules, number))
        }
        return ParsedCourseImport(courses, issues)
    }

    private fun parseTimeSegment(seg: String, room: String): ImportedSchedule? {
        val m = TIME_PATTERN.matcher(seg.trim())
        if (!m.find()) return null
        val day = m.group(1)?.let { DAY_MAP[it[0]] } ?: 1
        val sp = m.group(2)?.toIntOrNull() ?: return null
        val ep = m.group(3)?.toIntOrNull() ?: return null
        val sw = m.group(4)?.toIntOrNull() ?: return null
        val ew = m.group(5)?.toIntOrNull() ?: return null
        val wt = when (m.group(6)) {
            "单" -> 1
            "双" -> 2
            else -> 0
        }
        return ImportedSchedule(day, sp, ep, sw, ew, wt, room)
    }
}
