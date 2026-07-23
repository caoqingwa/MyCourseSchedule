# CourseSchedule 更新日志

## v2.4 (2026-06-19)

### 性能优化
- WeekViewModel: SimpleDateFormat → DateTimeFormatter，修复 companion object 线程安全 bug
- WeekViewModel: checkConflict 改为读取 uiState.value，消除 3 次冗余数据库查询
- CalendarViewModel: schedules 从 .first() 阻塞调用改为 combine 第四个 Flow，响应式更新
- CalendarViewModel: weeklyCourseCount 从 hardcoded (1..5) 改为动态 (1..semester.weekDays)
- TodayViewModel: getPeriodTimes() JSON 解析提升到 map 开头，单次解析替代每 schedule 2 次
- WeekGrid: startTimes/endTimes 用 remember(semester) 缓存，消除每次重组的 JSON 解析
- WeekGrid: occupied remember key 从 colBlocks.map{size} 改为 schedulesKey+coursesKey
- WeekGrid: fallback 颜色提取为顶层常量，避免每次创建新 Color 对象
- CourseCard: 颜色计算从 buildCourseColorMap 改为 getCourseColorByName，省去 Map 构建
- MainActivity: navigateTo 从 local fun 改为 remember lambda，减少重组时的函数对象分配
- Room 索引: Course.semesterId、Schedule.courseId、Exam.courseId 添加索引，消除子查询全表扫描
- DAO 优化: ScheduleDao/ExamDao 的 IN 子查询改为 INNER JOIN
- 数据库: 版本 v4→v5，MIGRATION_4_5 创建三个索引

---

## v2.3 (2026-06-19)

### 构建修复
- AndroidManifest: 添加 tools 命名空间，移除默认 WorkManagerInitializer 以适配 on-demand 初始化，修复 release lint 报错

### 底部导航栏滑动切换
- BottomNavBar: 在导航栏外层添加 pointerInput 拖拽手势检测，水平拖动超过 50dp 即切换页面
- BottomNavBar: 恢复各 tab 的 clickable，点击切换正常；拖拽手势仅在水平移动超过 30dp 阈值后才消费事件，不影响点击

### 课程颜色系统重做
- Color: 删除固定调色板 CourseColors，新增 buildCourseColorMap 函数，对课程名排序后在 HSL 色相环均匀分配色相（饱和度 0.45，亮度 0.72），自动跳过黄色系
- 规则保证：同名课程同色，不同名课程不同色，课程数量不限
- WeekGrid: 用 buildCourseColorMap 替代旧的按 courseId 分配颜色

### 考试提醒修复
- ExamReminderWorker: delayMillis <= 0 时不再静默跳过，改为 coerceAtLeast(0) 立即触发
- ExamReminderWorker: input data 改为传递 exam_millis（考试时间戳），worker 运行时重新计算剩余小时数，避免调度时预计算导致通知内容过时
- ExamReminderWorker: worker 运行时检查通知权限，无权限时静默跳过而非崩溃
- CourseCard: 同样用 buildCourseColorMap 按课程名取色
- WeekViewModel: 新建课程时 color 字段设为占位值，实际颜色由渲染时按名称动态计算
- WeekGrid: 移除 colColorIndices 相邻块颜色避让逻辑，HSL 方案天然保证不同名不同色

---

## v2.2 (2026-06-19)

### 周课表日期行
- WeekGrid: 新增日期行，显示当前周每天的实际日期（如 16、17、18...），浅色分割线分隔
- WeekGrid: 节次列表头显示当前周所在月份（如"6月"）
- WeekGrid: 今天日期用 primary 色高亮显示
- WeekScreen: 传递 selectedWeek 给 WeekGrid

### 周课表布局优化
- WeekGrid: 格子高度 44dp → 80dp，显示更多课程信息
- WeekGrid: 节次列宽度固定 40dp，与其他列等宽
- WeekGrid: 日期行字号增大到 12sp
- WeekGrid: 课程名 maxLines 2→3，lineHeight 增大
- WeekGrid: 节次/时间字号增大

### 课程颜色统一
- Color: 新增 getCourseColorByName 函数，基于课程名 hashCode 分配颜色
- WeekGrid/CourseCard: 同名课程使用相同颜色

## v2.1 (2026-06-19)

### 防止误触外层滚动
- SemesterSetupDialog: 移除 ScrollNumberPicker 的 nestedScroll，消除与 ModalBottomSheet 的滚动冲突导致的抽搐
- SemesterSetupDialog: Column 保留 verticalScroll，选择器区域滑动时页面会微微跟随，但滚动流畅无抖动

### 周末课程防误切
- SemesterSetupDialog: 当周六/日有课程时，5天制 FilterChip 禁用并显示红色警告提示
- WeekViewModel/TodayViewModel: 新增 hasWeekendCourses 状态，基于 schedules 的 dayOfWeek>5 判断
- ScheduleDao: 新增 countWeekendSchedules 查询（备用）
- WeekUiState/TodayUiState: 新增 hasWeekendCourses 字段

### 编辑课程数据不刷新修复
- WeekGrid: coursesKey 从 courses.keys.hashCode() 改为 courses.hashCode()，确保课程名/教师等字段变更后网格重新渲染

### 消息提示模式
- NotificationHelper: 添加 PendingIntent，点击考试通知可直接打开 App
- NotificationHelper: 添加运行时权限检查，Android 13+ 自动检测 POST_NOTIFICATIONS 权限
- NotificationHelper: 考试通知添加 BigTextStyle，显示完整提醒内容
- CalendarScreen: 顶部栏添加通知开关图标（铃铛），可一键开启/关闭考试提醒
- CalendarScreen: 考试列表标题旁显示当前提醒状态标签（已开启/已关闭）
- CalendarViewModel: 注入 ApplicationContext，管理通知开关状态
- CalendarViewModel: addExam/updateExam 自动根据开关状态调度/取消 WorkManager 任务
- CalendarViewModel: setNotificationsEnabled 开启时自动重新调度所有未过期考试提醒
- ExamReminderWorker: rescheduleAll 改为 suspend 函数，启动时重新注册所有考试通知
- CourseScheduleApp: 启动时调用 rescheduleAll 恢复所有考试提醒
- CourseScheduleApp: 移除未使用的 daily_summary 通知渠道
- ExamDao: 新增 getAllPending 查询未来考试
- Android 13+: 通知开关点击时自动请求 POST_NOTIFICATIONS 运行时权限

---

## v2.0 (2026-06-19)

### 5/7天每周课表切换
- Semester 实体新增 weekDays 字段（默认5天），支持5天制（周一至周五）或7天制（周一至周日）
- Room 数据库升级 v3→v4，ALTER TABLE 添加 weekDays 列
- WeekGrid 支持动态列数：根据 semester.weekDays 渲染5列或7列
- WeekGrid 网格线、触摸区域、课程色块全部适配动态列数
- SemesterSetupDialog 新增"每周课表天数"切换（FilterChip: 5天/7天）
- DateUtils 新增 DAY_NAMES_7 常量（周一至周日）
- AddCourseDialog/EditCourseDialog 新增 weekDays 参数，星期下拉菜单支持7天
- WeekViewModel/TodayViewModel saveSemester 签名更新，透传 weekDays
- CourseRepository.saveSemesterCurrent 签名更新，支持 weekDays

---

## v1.8 (2026-06-19)

### 安装修复
- build.gradle.kts: 添加显式 debug 签名配置，修复直接安装 APK 时 -15 (INSTALL_FAILED_VERIFICATION_FAILURE) 错误
- release 构建类型也使用 debug 签名配置

### 代码质量修复
- CourseRepository: 提取 saveSemesterCurrent() 方法，消除 WeekViewModel/TodayViewModel 中 saveSemester 的重复逻辑
- AddExamDialog: courses[selectedCourseIndex] 添加 getOrNull 和 coerceIn 防止 IndexOutOfBoundsException
- AddCourseDialog: startWeek/endWeek 提交时 coerceAtLeast(1)，防止输入 "0" 导致 week-0 课程持久化到数据库
- EditCourseDialog: 同上，startWeek/endWeek coerceAtLeast(1) 校验
- WeekViewModel: 移除重复的 viewModelScope import

---

## v1.7 (2026-06-13)

### 周课表性能优化
- WeekGrid: 空格子 Box 替换为 Canvas 绘制网格线，组合数从 ~72 降至 ~12
- WeekGrid: 空格子触摸用单一 pointerInput + 坐标计算替代 ~60 个 combinedClickable
- WeekGrid: 课程区域网格线用 Canvas drawLine 一次性绘制
- WeekGrid: 节次列 Box 加回 border 确保分隔线可见
- WeekGrid: MergedBlock data class 移到 composable 外部，避免每次重组创建新类实例

### 底部导航栏修复
- BottomNavBar: 添加 padding(horizontal=2.dp) 防止选中 tab 放大后遮挡邻居
- BottomNavBar: 分离 Float 和 Color 的 AnimationSpec 类型，修复编译错误

### 页面切换优化
- HorizontalPager: beyondViewportPageCount 1→0，避免同时渲染两个重页面
- 跨页切换（1↔3）: 用 scrollToPage + scale/alpha crossfade 替代 animateScrollToPage，避免中间页闪烁
- 相邻页切换（1↔2, 2↔3）: 保持 animateScrollToPage 正常滑动
- 跨页动画: 当前页 scale 1.0→0.92 + alpha→0 瞬移新页 + scale 1.08→1.0 + alpha→1

### 手势修复
- WeekScreen: 恢复速度检测（lastDragDelta/lastDragTime），速度阈值 400px/s
- WeekScreen: 速度 OR 距离任一满足即触发翻页

### 日历高亮修复
- WeekViewModel: consumeNavigationState 先设置 _highlightDayOfWeek 再设置 _selectedWeek，确保 combine 读到正确值
- WeekViewModel: _highlightDayOfWeek 改为 MutableStateFlow<Int>，作为 combine 源 Flow
- WeekScreen: LaunchedEffect(navTargetWeek, navTargetDay) 响应式消费 NavigationState

### 代码优化
- TodayScreen: remember(state) 改为预计算 nextInfoList，消除每项重组时重复字符串拼接
- DateUtils: 提取 DAY_NAMES/WEEK_TYPES 共享常量，AddCourseDialog/EditCourseDialog 消除重复定义
- SemesterSetupDialog: derivedStateOf 改为普通变量（editingPeriodIndex/periodCount 非 State）
- CalendarScreen: 考试列表 items 内 System.currentTimeMillis() 改用 state.todayMillis
- CalendarViewModel: 嵌套 launch+collect 协程泄漏改为 flatMapLatest+combine，旧 semester 切换时自动取消
- CalendarViewModel: CalendarUiState 添加 @Immutable，Compose 可跳过不变字段重组
- WeekViewModel: buildWeekPage 内 SimpleDateFormat 提取到 companion object 缓存
- CalendarScreen: exam detail dialog 内 dayOfWeekNames 加 remember

---

## v1.6 (2026-06-12)

### 动画优化
- WeekGrid: 添加 @Immutable 注解到所有数据类，减少不必要的重组
- BottomNavBar: tween 替换为 spring(dampingRatio=0.85, stiffness=400) 弹性动画
- CalendarPicker: 月份切换 tween 替换为 spring，增加 partial offset 初始偏移
- CalendarPicker: MonthGrid 整月数据 remember 预计算，避免每帧重建 Calendar
- CalendarPicker: 底部周范围文本 SimpleDateFormat remember 缓存
- CalendarScreen: 考试列表 items 内 SimpleDateFormat/Calendar 提取为 remember
- SemesterSetupDialog: isEditing/sectionHeight 改用 derivedStateOf 减少重组
- HorizontalPager: beyondViewportPageCount 0→1，相邻页面保持组合状态

### 周课表重构
- 提取 WeekSwitcherState 类封装动画+手势逻辑，消除 WeekScreen 内联复杂度
- 提取 WeekNavigationBar 独立组件，消除导航栏内联代码
- 所有切换方式（箭头、滑动、本周按钮、日历跳转）统一走 animateSwitch 路径
- 切换动画改为中心缩放渐变：当前页 alpha→0 + scale→0.85，新页 alpha→1 + scale→1.15→1.0
- 拖拽回弹：未达阈值时 offset.animateTo(0f) 自动弹回，不再卡在一半
- 滑动累积：动画进行中再滑时 pendingSwipes += delta，结束后自动连续切换
- pointerInput key 改为 stable key，避免切周时手势处理器重启

### 节次边界控制
- 节次滑块动态下限：已有课程排到第N节时无法减少到更低
- 拖动低于已排课节次时回弹并显示红色提示
- 起节/正节输入框添加 isError + supportingText 边界校验
- 起节 > 正节时显示"起节不能大于正节"错误文字
- 确定按钮在节次非法时禁用提交

### 日历高亮
- 日历点击日期跳转周课表时，短暂橙色高亮对应列（2秒后自动消失）
- 查看当前周时高亮今天所在列，查看其他周时不高亮
- NavigationState 新增 targetDayOfWeek 传递点击日期的星期
- WeekGrid 新增 highlightDayOfWeek 参数支持指定高亮列

### 逻辑修复
- ExamReminderWorker: 绕过 Hilt 直接创建 Room 实例改为注入 ExamDao
- ExamReminderWorker: enqueue 改为 enqueueUniqueWork 避免重复调度
- TodayScreen: LaunchedEffect 课程提醒去重，同课程同节次只调度一次
- TodayViewModel: 顺序 .first() 调用改为 combine 并行查询
- EditCourseDialog: 空名称添加 nameError 状态 + isError 视觉提示
- SemesterSetupDialog: 增加节数时用 defaultPeriodTimes 填充新增节次
- DateUtils: toMonday/getStartOfWeek 使用 Locale.US 固定 locale

### 版本号更新
- versionCode: 1→5, versionName: 1.0→1.5
- README/CHANGELOG 同步更新

---

## v1.5 (2026-06-11)

### 性能优化
- WeekGrid: 使用 remember(schedules.hashCode()) 避免 List 引用变化导致不必要重算
- WeekGrid: 空单元格渲染优化，减少过度绘制
- WeekViewModel: 拆分 combine 为按需查询，减少不必要的 Flow 订阅
- TodayScreen: 课程提醒使用 enqueueUniqueWork 避免重复调度
- WeekScreen: 屏幕宽度计算移至 LaunchedEffect

### 架构优化
- CourseRepository: 添加 @Transaction 注解保护多步数据库操作
- NavigationState: 移除静态可变状态，改用 SavedStateHandle

### 代码复用
- 提取 CommonTopBar 组件，消除 TodayScreen/WeekScreen 重复代码
- 提取 CourseColors 工具函数，统一颜色映射逻辑

### 功能增强
- 新增课程冲突检测，添加时自动提示时间重叠
- 新增数据导出/备份功能（JSON 格式）
- 新增桌面 Widget 支持（显示今日课程）
- 深色模式主题适配（Material3 Dynamic Color）

### Bug 修复
- 修复 DateUtils.toMonday 在不同 locale 下的行为差异
- 修复 WorkManager 重复提醒问题

---

## v1.4 (2026-06-10)

### 考试提醒功能
- 新增考试实体（Exam），支持考试科目、日期、时间、地点、提前x小时提醒
- 新增考试提醒 Worker（ExamReminderWorker），到达提醒时间自动推送通知
- 长按日历日期直接弹出添加考试/提醒弹窗
- 日历页显示考试卡片，点击查看详情，长按进入编辑
- 详情页支持编辑和删除考试
- 考试时间过去后自动删除提醒

### 考试时间选择器
- 滚轮式时间选择器，精确到15分钟
- 支持选择考试日期、起止时间、提醒提前时间

### 考试数据层
- ExamDao 新增查询方法
- DatabaseModule 添加 fallbackToDestructiveMigration
- 数据库版本升级至 v3

### 今日课程优化
- 课程下课后立即从当日课表中消失（基于实际下课时间判断，不再仅依赖节次编号）
- 当前课程精确判定：仅在上课时间段内显示为"当前"

### UI 动画优化
- 周课表左右切换 spring 动画优化
- 日历月份滑动动画优化
- BottomNavBar spring 缩放

### Bug 修复
- 修复 CalendarPicker 动画参数类型不匹配
- 修复 BottomNavBar 弹簧参数缺失
- 修复 SemesterSetupDialog AnimatedVisibility 上下文问题
- 修复 WeekGrid FontWeight、TextOverflow、TextAlign 缺失导入
- 修复周课表格子空格显示、连堂课断裂问题
- 修复考试时间滚轮偏移选择错误

---

## v1.3 (2026-06-07)

### 周课表滑动切换优化
- 使用 detectHorizontalDragGestures 替代手写 awaitPointerEventScope 循环，彻底解决垂直滚动卡顿问题
- 速度判定：松手时检测拖拽速度，>800px/s 即切换
- 距离判定：拖拽超过屏幕 30% 即切换（速度 OR 距离任一满足）
- 5% 屏幕宽度死区：前 5% 拖拽不产生视觉位移
- 边界橡皮筋：拖到第 1 周/最后一周时偏移衰减 25%
- 弹回弹簧：DampingRatioLowBouncy + StiffnessLow，弹性柔和
- 修复松手后内容卡在屏幕外的问题（切换前重置 visualOffset）

### 动画优化
- Spring 物理动画替代 tween：HorizontalPager、BottomNavBar、CalendarPicker、TodayScreen、SemesterSetupDialog 全部切换
- 周课表切换：fade + scale (0→0.95→1.0) 弹性过渡
- TodayScreen 课程卡片交错入场动画
- 日历月份切换 spring 滑动动画
- 底部导航栏 spring 缩放

### 依赖更新
- 新增 androidx.compose.foundation:foundation 依赖

---

## v1.2 (2026-06-05)

### 学期设置
- 支持自定义节数、上课时间、总周数、开学日期
- 滚动选择器设置开学日期和总周数
- 保存 2-3 个自定义学期预设
- 上课时间可逐节编辑（展开/收起动画）

### 周课表增强
- 连堂课合并显示为一个色块
- 长按已添加课程进入编辑/删除界面
- 课程详情显示"课程名@上课地点"
- 左侧节次栏显示上下课时间（两行）
- 切换到非本周时周数标红 + "本周"快捷按钮

### 课程添加/编辑
- 课程名必填，教师和教室非必填
- 修复编辑课程导致课程消失的 bug
- 修复添加课程对话框参数不匹配的编译错误

### 日历功能
- 日历选择日期自动跳转对应周课表
- 月份切换加入滑动边界控制（1-20周）
- 从周一开始计算当前周

### 修复
- 修复 DateUtils 编译错误
- 修复 WeekViewModel suspend 函数调用问题
- 修复 WeekGrid 未定义的 Unresolved reference
- 修复 CourseReminderWorker 调度问题
- 修复安装失败 (-15) 问题
- 修复 KSP MissingType 和 DAO 注解缺失

---

## v1.0-pre (2026-06-03)

### 初始版本
- 三页架构：今日课程 / 周课表 / 日历（HorizontalPager）
- 底部导航栏（日/周/日历三按钮）
- 每日视图：显示当天课程，按上课时间排序，已过课程自动隐藏
- 周课表视图：1-12节 x 周一~周五 网格
- 日历总览：月份网格，标注当前周数
- Room 数据库持久化课程数据
- Hilt 依赖注入
