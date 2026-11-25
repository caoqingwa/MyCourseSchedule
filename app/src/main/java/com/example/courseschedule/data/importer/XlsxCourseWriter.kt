package com.example.courseschedule.data.importer

import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import org.xmlpull.v1.XmlPullParserFactory
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxCourseWriter {
    const val MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"

    fun write(
        output: OutputStream,
        courses: List<Course> = emptyList(),
        schedules: List<Schedule> = emptyList(),
        roomNames: Map<Long, String> = emptyMap()
    ) {
        val byId = courses.associateBy { it.id }
        ZipOutputStream(output).use { zip ->
            val parts = mapOf(
                "[Content_Types].xml" to """
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                """,
                "_rels/.rels" to """
                    <Relationships xmlns="$PACKAGE_REL_NS">
                      <Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                """,
                "xl/workbook.xml" to """
                    <workbook xmlns="$SHEET_NS" xmlns:r="$REL_NS">
                      <sheets><sheet name="课表" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                """,
                "xl/_rels/workbook.xml.rels" to """
                    <Relationships xmlns="$PACKAGE_REL_NS">
                      <Relationship Id="rId1" Type="$REL_NS/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                """
            )
            for ((name, xml) in parts) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.trimIndent().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val xml = XmlPullParserFactory.newInstance().newSerializer()
            xml.setOutput(zip, "UTF-8")
            xml.startDocument("UTF-8", null)
            xml.setPrefix("", SHEET_NS)
            xml.startTag(SHEET_NS, "worksheet")
            xml.startTag(SHEET_NS, "sheetData")
            fun row(number: Int, values: List<String>) {
                xml.startTag(SHEET_NS, "row").attribute(null, "r", number.toString())
                for ((column, value) in values.withIndex()) {
                    xml.startTag(SHEET_NS, "c")
                        .attribute(null, "r", "${'A' + column}$number")
                        .attribute(null, "t", "inlineStr")
                    xml.startTag(SHEET_NS, "is")
                    xml.startTag(SHEET_NS, "t").text(value).endTag(SHEET_NS, "t")
                    xml.endTag(SHEET_NS, "is")
                    xml.endTag(SHEET_NS, "c")
                }
                xml.endTag(SHEET_NS, "row")
            }
            row(1, listOf("课程名称", "教师姓名", "上课时间", "教学地点"))
            for ((index, schedule) in schedules.withIndex()) {
                val course = byId.getValue(schedule.courseId)
                val day = "一二三四五六日"[schedule.dayOfWeek - 1]
                val weekType = when (schedule.weekType) {
                    1 -> "(单)"
                    2 -> "(双)"
                    else -> ""
                }
                val time = "星期${day}第${schedule.startPeriod}-${schedule.endPeriod}节" +
                    "{${schedule.startWeek}-${schedule.endWeek}周$weekType}"
                val room = schedule.roomId?.let { roomNames[it] }.orEmpty()
                row(index + 2, listOf(course.name, course.teacher, time, room))
            }
            xml.endTag(SHEET_NS, "sheetData")
            xml.endTag(SHEET_NS, "worksheet")
            xml.endDocument()
            zip.closeEntry()
        }
    }
}
