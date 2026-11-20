# CourseSchedule 发版流程

1. 确定本次发布版本。`app/build.gradle.kts` 中的 `versionName` 应与 Git 标签去掉 `v` 后的值一致，`versionCode` 应高于上一版。保留已有发布标签。
2. 在 `CHANGELOG.md` 添加 `## v版本号 (YYYY-MM-DD)` 小节，写明功能变化和入口。发版工作流会从该小节提取 GitHub Release 的发布说明。同步 README 的功能概览和版本历史。
3. 提交前执行 `./gradlew :app:testDebugUnitTest :app:assembleDebug`。检查手势导航和三键导航下的底栏、周课表视图偏好、从旧版升级后的课表和考试记录，以及考试提醒到达后能否打开详情。
4. 提交并推送发布代码，在同一提交创建并推送新版本标签。推送 `v*` 标签会触发 GitHub Actions 的「发版 Release」工作流，由它校验版本号、构建签名 APK 并上传至 GitHub Release。工作流依赖仓库签名密钥配置。
5. 确认工作流成功，检查 GitHub Release 的版本、说明和 APK 附件，下载 APK 验证能覆盖安装上一版。

如果同一个标签对应的工作流临时失败，可以从 Actions 手动选择「发版 Release」并填入已有标签重跑。如果需要修改该标签对应的代码，则使用新版本标签发版。

## v2.4.1 升级说明

`v2.4.1` 的 Android `versionCode` 为 `32`，高于 `v2.18.2` 的 `31`。使用同一签名构建的 APK 可通过手动下载安装覆盖升级。旧版应用内更新按版本号数字比较，`v2.18.2` 不会提示升级到 `v2.4.1`。发布时应在 GitHub Release 说明中提示用户手动下载 APK。
