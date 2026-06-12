# CourseSchedule 更新日志

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

## v1.5 (2026-06-11)  ### 性能优化 - WeekGrid: 使用 remember(schedules.hashCode()) 避免 List 引用变化导致不必要重算 - WeekGrid: 空单元格渲染优化，减少过度绘制 - WeekViewModel: 拆分 combine 为按需查询，减少不必要的 Flow 订阅 - TodayScreen: 课程提醒使用 enqueueUniqueWork 避免重复调度 - WeekScreen: 屏幕宽度计算移至 LaunchedEffect  ### 架构优化 - CourseRepository: 添加 @Transaction 注解保护多步数据库操作 - NavigationState: 移除静态可变状态，改用 SavedStateHandle  ### 代码复用 - 提取 CommonTopBar 组件，消除 TodayScreen/WeekScreen 重复代码 - 提取 CourseColors 工具函数，统一颜色映射逻辑  ### 功能增强 - 新增课程冲突检测，添加时自动提示时间重叠 - 新增数据导出/备份功能（JSON 格式） - 新增桌面 Widget 支持（显示今日课程） - 深色模式主题适配（Material3 Dynamic Color）  ### Bug 修复 - 修复 DateUtils.toMonday 在不同 locale 下的行为差异 - 修复 WorkManager 重复提醒问题  ---  ## v1.4 (2026-06-10)  ### 考试提醒功能 - 新增考试实体（Exam），支持考试科目、日期、时间、地点、提前x小时提醒 - 新增考试提醒 Worker（ExamReminderWorker），到达提醒时间自动推送通知 - 长按日历日期直接弹出添加考试/提醒弹窗 - 日历页显示考试卡片，点击查看详情，长按进入编辑 - 详情页支持编辑和删除考试 - 考试时间过去后自动删除提醒  ### 考试时间选择器 - 滚轮式时间选择器，精确到15分钟 - 支持选择考试日期、起止时间、提醒提前时间  ### 考试数据层 - ExamDao 新增查询方法 - DatabaseModule 添加 fallbackToDestructiveMigration - 数据库版本升级至 v3  ### 今日课程优化 - 课程下课后立即从当日课表中消失（基于实际下课时间判断，不再仅依赖节次编号） - 当前课程精确判定：仅在上课时间段内显示为"当前"  ### UI 动画优化 - 周课表左右切换 spring 动画优化 - 日历月份滑动动画优化 - BottomNavBar spring 缩放  ### Bug 修复 - 修复 CalendarPicker 动画参数类型不匹配 - 修复 BottomNavBar 弹簧参数缺失 - 修复 SemesterSetupDialog AnimatedVisibility 上下文问题 - 修复 WeekGrid FontWeight、TextOverflow、TextAlign 缺失导入 - 修复周课表格子空格显示、连堂课断裂问题 - 修复考试时间滚轮偏移选择错误  ---  ## v1.3 (2026-06-07)  ### 周课表滑动切换优化 - 使用 detectHorizontalDragGestures 替代手写 awaitPointerEventScope 循环，彻底解决垂直滚动卡顿问题 - 速度判定：松手时检测拖拽速度，>800px/s 即切换 - 距离判定：拖拽超过屏幕 30% 即切换（速度 OR 距离任一满足） - 5% 屏幕宽度死区：前 5% 拖拽不产生视觉位移 - 边界橡皮筋：拖到第 1 周/最后一周时偏移衰减 25% - 弹回弹簧：DampingRatioLowBouncy + StiffnessLow，弹性柔和 - 修复松手后内容卡在屏幕外的问题（切换前重置 visualOffset）  ### 动画优化 - Spring 物理动画替代 tween：HorizontalPager、BottomNavBar、CalendarPicker、TodayScreen、SemesterSetupDialog 全部切换 - 周课表切换：fade + scale (0→0.95→1.0) 弹性过渡 - TodayScreen 课程卡片交错入场动画 - 日历月份切换 spring 滑动动画 - 底部导航栏 spring 缩放  ### 依赖更新 - 新增 androidx.compose.foundation:foundation 依赖  ---  ## v1.2 (2026-06-05)  ### 学期设置 - 支持自定义节数、上课时间、总周数、开学日期 - 滚动选择器设置开学日期和总周数 - 保存 2-3 个自定义学期预设 - 上课时间可逐节编辑（展开/收起动画）  ### 周课表增强 - 连堂课合并显示为一个色块 - 长按已添加课程进入编辑/删除界面 - 课程详情显示"课程名@上课地点" - 左侧节次栏显示上下课时间（两行） - 切换到非本周时周数标红 + "本周"快捷按钮  ### 课程添加/编辑 - 课程名必填，教师和教室非必填 - 修复编辑课程导致课程消失的 bug - 修复添加课程对话框参数不匹配的编译错误  ### 日历功能 - 日历选择日期自动跳转对应周课表 - 月份切换加入滑动边界控制（1-20周） - 从周一开始计算当前周  ### 修复 - 修复 DateUtils 编译错误 - 修复 WeekViewModel suspend 函数调用问题 - 修复 WeekGrid 未定义的 Unresolved reference - 修复 CourseReminderWorker 调度问题 - 修复安装失败 (-15) 问题 - 修复 KSP MissingType 和 DAO 注解缺失  ---  ## v1.0-pre (2026-06-03)  ### 初始版本 - 三页架构：今日课程 / 周课表 / 日历（HorizontalPager） - 底部导航栏（日/周/日历三按钮） - 每日视图：显示当天课程，按上课时间排序，已过课程自动隐藏 - 周课表视图：1-12节 x 周一~周五 网格 - 日历总览：月份网格 + 周数标注 - 课程管理：长按添加课程（课程名、教师、教室、单双周、起止周/节） - Room 数据库持久化 - Hilt 依赖注入 - MVVM 架构