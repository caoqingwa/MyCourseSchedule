package com.example.courseschedule.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 采集 CourseSchedule 的 baseline profile，覆盖冷启动与主要交互：
 * 三页切换、周课表左右换周、日历左右换月。
 *
 * 生成（需连接真机或模拟器，API 28+）：
 *   ./gradlew :app:generateBaselineProfile
 * 产物：app/src/main/baselineProfiles/{baseline-prof.txt,startup-prof.txt}，提交后随 release 打包。
 */
@RunWith(AndroidJUnit4::class)
class CourseScheduleBaselineProfile {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun collectStartupAndMainFlows() {
        baselineProfileRule.collect(PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.text("今日")), 5_000)
            device.waitForIdle()

            // 周课表：切页 + 左右滑动换周（触发 Pager / WeekGrid）
            device.findObject(By.text("周课表"))?.click()
            device.wait(Until.hasObject(By.text("周课表")), 5_000)
            device.waitForIdle()
            repeat(2) {
                swipeHorizontal(fromLeft = true)
                swipeHorizontal(fromLeft = false)
            }

            // 日历：切页 + 左右滑动换月（触发 CalendarPicker）
            device.findObject(By.text("日历"))?.click()
            device.wait(Until.hasObject(By.text("课程表")), 5_000)
            device.waitForIdle()
            swipeHorizontal(fromLeft = true)
            swipeHorizontal(fromLeft = false)

            // 回到今日页
            device.findObject(By.text("今日"))?.click()
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.swipeHorizontal(fromLeft: Boolean) {
        val midY = (device.displayHeight * 0.5f).toInt()
        val startX = (device.displayWidth * if (fromLeft) 0.85f else 0.15f).toInt()
        val endX = (device.displayWidth * if (fromLeft) 0.15f else 0.85f).toInt()
        device.swipe(startX, midY, endX, midY, 20)
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE_NAME = "com.example.courseschedule"
    }
}
