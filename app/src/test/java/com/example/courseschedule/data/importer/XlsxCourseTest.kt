package com.example.courseschedule.data.importer

import com.example.courseschedule.data.db.entity.Course
import com.example.courseschedule.data.db.entity.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class XlsxCourseTest {
    @Test
    fun exportedWorkbookCanBeImported() {
        val bytes = ByteArrayOutputStream()
        XlsxCourseWriter.write(
            bytes,
            courses = listOf(Course(1, 1, "课程设计 & 实训", "张老师", "0")),
            schedules = listOf(Schedule(courseId = 1, dayOfWeek = 7, startPeriod = 1,
                endPeriod = 2, startWeek = 3, endWeek = 15, weekType = 1, roomId = 4)),
            roomNames = mapOf(4L to "A101;B102")
        )
        val sheet = XlsxCourseParser.parse(bytes.toByteArray().inputStream()).single()
        assertEquals("课表", sheet.name)
        assertTrue(sheet.data.issues.isEmpty())
        assertEquals(
            ImportedCourse("课程设计 & 实训", "张老师",
                listOf(ImportedSchedule(7, 1, 2, 3, 15, 1, "A101;B102")), 2),
            sheet.data.courses.single()
        )
    }

    @Test
    fun recognizesHeaderBelowTitleAndReturnsOnlyMatchingSheetsInWorkbookOrder() {
        val template = ByteArrayOutputStream().also { XlsxCourseWriter.write(it) }
        assertTrue(XlsxCourseParser.parse(template.toByteArray().inputStream()).single().data.courses.isEmpty())
        val parts = linkedMapOf<String, String>()
        ZipInputStream(template.toByteArray().inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                parts[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        val sheetPath = "xl/worksheets/sheet1.xml"
        val templateSheet = parts.getValue(sheetPath)
        parts[sheetPath] = templateSheet.replace("课程名称", "说明")
        parts["xl/worksheets/sheet2.xml"] = templateSheet
            .replace("r=\"1\"", "r=\"4\"")
            .replace(Regex("r=\"([A-D])1\"")) { "r=\"${it.groupValues[1]}4\"" }
            .replace("<sheetData>", """<sheetData><row r="1">
                <c r="A1" t="inlineStr"><is><t>教务处课表</t></is></c></row>""")
            .replace("</sheetData>", """
                <row r="8">
                  <c r="A8" t="inlineStr"><is><t>高等数学</t></is></c>
                </row>
                </sheetData>
            """.trimIndent())
        parts["xl/worksheets/sheet3.xml"] = templateSheet
        parts["xl/workbook.xml"] = parts.getValue("xl/workbook.xml").replace(
            "</sheets>", """<sheet name="第二张课表" sheetId="3" r:id="rId3"/>
                <sheet name="第一张课表" sheetId="2" r:id="rId2"/></sheets>"""
        )
        parts["xl/_rels/workbook.xml.rels"] = parts.getValue("xl/_rels/workbook.xml.rels").replace(
            "</Relationships>", """
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="/xl/worksheets/sheet2.xml"/>
                <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
                </Relationships>
            """.trimIndent()
        )
        val workbook = ByteArrayOutputStream()
        ZipOutputStream(workbook).use { zip ->
            for ((path, xml) in parts) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }
        val sheets = XlsxCourseParser.parse(workbook.toByteArray().inputStream())
        assertEquals(listOf("第二张课表", "第一张课表"), sheets.map { it.name })
        assertEquals(listOf(ImportRowIssue(8, "缺少上课时间")), sheets.last().data.issues)
    }
}
