# 单目标查找模式验证

2026-11-09，Java 25；执行 `./gradlew test runClientGameTest build --console=plain`，全部通过。JUnit 共 67 项，失败/错误/跳过均为 0。

- 主动定位仅显示目标的轮廓和头顶标记；列表选择、后台读取不会切换查找目标。
- 定位与取消不发出坐标/距离提示，移除了左上角导航 HUD。
- 定位快捷键再次按下取消；独立取消快捷键默认未绑定；管理界面提供取消按钮。
- 全局标记关闭时隐藏全部标记，再开启仍保持当前查找目标。
- 实体失效、卸载、数据集切换及清空记录时清理目标。
- 新增 `VV_NAVIGATION_PASS`，并通过原有轮廓、隔墙、语言、清空数据、世界隔离与转职读取回归。

回归中修复原有手动读取的顺序问题：在允许发起手动请求后先刷新转职信息，再建立请求，避免旧状态清理取消新请求；隔离旧响应期间不刷新记录。清空测试使用清空前范围内实际人数作回扫基准，替代过期的 303 人常量。

日志：`single-target-navigation.log`。
截图：`navigation-find-only-one-of-two.png`、`navigation-find-cancel-button.png`、`navigation-shortcuts-en.png`。
产物：`build/libs/VillagerVerdict-1.0.0.jar`。未修改或安装到 PrismLauncher 游戏实例目录。
