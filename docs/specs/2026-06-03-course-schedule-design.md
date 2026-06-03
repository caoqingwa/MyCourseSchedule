# CourseSchedule - 轻量化安卓课程表 设计文档

> 版本：v1.0
> 日期：2026-06-03
> 状态：设计确认

## 1. 项目概述

轻量化 Android 课程表应用，面向个人使用，预留分享扩展能力。支持每日/每周课表查看、日历选周、考试与课程提醒。

## 2. 技术栈

| 项目 | 选择 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material Design 3 |
| 最低 API | 26 (Android 8.0) |
| 数据库 | Room (SQLite) |
| 架构 | MVVM |
| DI | Hilt |
| 导航 | Compose Navigation |
| 异步 | Kotlin Coroutines + Flow |
| 定时任务 | WorkManager |
| 通知 | NotificationManager |

## 3. 页面结构

应用采用底部导航栏切换，共 3 个主页面：

### 3.1 今日课程页（默认首页）

功能：
- 打开 App 即显示当日课程
- 只显示未结束的课程，已过的课程自动消失
- 按上课时间排序
- 当前正在上的课高亮显示（绿色边框 + 当前角标）
- 显示「今日剩余 N 节课」计数

课程卡片信息：
- 课程名称（大字）
- 时间段标签（如 14:00-15:40）
- 📍教室
- 🕐节数（第5-6节）
- 👨‍🏫教师
- 底部提示下一节信息（如果有）

空课日：显示占位图 + 今天没有课程，好好休息吧

### 3.2 周课表页

功能：
- 5列（周一~周五）x 12行（第1-12节）网格
- 不同课程用不同颜色区分
- 点击任意课程格弹出详情卡片
- 当天列高亮显示
- 支持左右滑动切换周

网格显示信息：
- 课程名称
- 教室

详情卡片信息：
- 课程名称
- 📍教室
- 👨‍🏫教师
- 📅课程周数（如第1-16周 全周）
- 🕐时间安排（如周二 5-6节, 周三 5-6节）

### 3.3 日历总览页

功能：
- 完整月历视图，可左右切换月份
- 高亮当前学期日期范围
- 点击某天跳转到该天的今日课程页
- 显示当前周信息（第X周 · 日期范围）
- 本周课程统计卡片（周一到周五各几节课）
- 近期考试倒计时列表

## 4. 底部导航栏

独立的圆角胶囊式底栏，固定在底部，3个按钮：

| 按钮 | 页面 | 图标 |
|------|------|------|
| 今日 | 今日课程页 | 📅 |

## 8. 数据模型

### 8.1 Entity 定义

Semester（学期）:
- id: Long (主键)
- name: String (学期名称)
- startDate: Long (学期第一天时间戳)

Course（课程）:
- id: Long (主键)
- semesterId: Long (关联学期)
- name: String (课程名称)
- teacher: String (教师姓名)
- color: String (颜色值)
- roomId: Long? (关联教室)

Schedule（课表）:
- id: Long (主键)
- courseId: Long (关联课程)
- dayOfWeek: Int (1=周一, 5=周五)
- startPeriod: Int (起始节次)
- endPeriod: Int (结束节次)
- startWeek: Int (起始周)
- endWeek: Int (结束周)
- weekType: Int (0=全周, 1=单周, 2=双周)

Room（教室）:
- id: Long (主键)
- name: String (教室名称)
- building: String? (教学楼)

Exam（考试）:
- id: Long (主键)
- courseId: Long (关联课程)
- examDate: Long (考试日期时间戳)
- reminderDays: Int (提前提醒天数)
- notes: String? (备注)

### 8.2 关系

- 一个 Semester 包含多个 Course
- 一个 Course 有多个 Schedule
- 一个 Course 关联一个 Room（可选）
- 一个 Course 可能有多个 Exam

### 8.3 时间计算

getWeekNumber(date, semesterStart): 根据日期计算周数
getDayOfWeek(date): 根据日期计算星期几
isScheduleActive(schedule, weekNumber): 判断课程在某周是否生效

## 9. 课表时间配置

标准大学模式：每天12节课，每节45分钟，周一到周五

| 节次 | 开始时间 | 结束时间 |
|------|----------|----------|
| 1 | 08:00 | 08:45 |
| 2 | 08:55 | 09:40 |
| 3 | 10:00 | 10:45 |
| 4 | 10:55 | 11:40 |
| 5 | 14:00 | 14:45 |
| 6 | 14:55 | 15:40 |
| 7 | 16:00 | 16:45 |
| 8 | 16:55 | 17:40 |
| 9 | 19:00 | 19:45 |
| 10 | 19:55 | 20:40 |
| 11 | 20:50 | 21:35 |
| 12 | 21:45 | 22:30 |

## 10. 课程颜色方案

预设 8 种颜色：
- 绿色: 背景 #CBE8BE, 文字 #1A3A10
- 蓝色: 背景 #BDD8F8, 文字 #0A2A5C
- 紫色: 背景 #E0D0F0, 文字 #2A1050
- 橙色: 背景 #FFE0C0, 文字 #5A2800
- 粉色: 背景 #F8D0E0, 文字 #5A1030
- 青色: 背景 #C0F0E8, 文字 #0A3A30
- 黄色: 背景 #FFF3C4, 文字 #5A4000
- 红色: 背景 #FFD0D0, 文字 #5A1010

## 11. 项目结构

app/src/main/java/com/example/courseschedule/
- data/db/AppDatabase.kt
- data/db/dao/CourseDao.kt, ScheduleDao.kt, ExamDao.kt, RoomDao.kt, SemesterDao.kt
- data/db/entity/Course.kt, Schedule.kt, Exam.kt, Room.kt, Semester.kt
- data/repository/CourseRepository.kt
- ui/theme/Color.kt, Theme.kt, Type.kt
- ui/navigation/AppNavigation.kt
- ui/screen/today/TodayScreen.kt, TodayViewModel.kt
- ui/screen/week/WeekScreen.kt, WeekViewModel.kt
- ui/screen/calendar/CalendarScreen.kt, CalendarViewModel.kt
- ui/screen/detail/CourseDetailSheet.kt
- ui/component/CourseCard.kt, WeekGrid.kt, BottomNavBar.kt, CalendarPicker.kt
- worker/CourseReminderWorker.kt, ExamReminderWorker.kt
- util/DateUtils.kt, NotificationHelper.kt
- CourseScheduleApp.kt

## 12. 非功能需求

- 轻量化：APK 大小 < 5MB
- 离线可用：所有数据本地存储，无需网络
- 性能：页面切换 < 300ms
- 兼容性：API 26+ (Android 8.0+)
- 深色模式：跟随系统设置（Material 3 自动适配）

## 13. 未来扩展（暂不实现）

- JSON/CSV 导入导出课表
- 多学期管理
- 课表分享（生成图片/链接）
- 桌面小组件（Widget）
- 课程评价/笔记