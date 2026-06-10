# CourseSchedule 更新日志

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
- 日历总览：月份网格 + 周数标注
- 课程管理：长按添加课程（课程名、教师、教室、单双周、起止周/节）
- Room 数据库持久化
- Hilt 依赖注入
- MVVM 架构