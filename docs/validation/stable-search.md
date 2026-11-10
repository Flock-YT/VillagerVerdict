# 稳定输入与通用拼音搜索联动验证

验证日期：2026-11-10。Java 25.0.5、Minecraft 26.3、Fabric Loader 0.19.5。全部构建与测试使用项目隔离目录，未修改 PrismLauncher 游戏实例。

## 修改

- 原管理页每 20 tick 无条件重建控件，附魔书搜索及商品选择器每次输入也重建控件。现在按稳定 ID 协调可见控件：复用输入框和列表按钮，只更新变化的文字、颜色、操作和提示，删除不再显示的控件。
- 管理页每 tick 更新内容，输入回调立即更新结果。输入框不因输入、排序、筛选、滚动、后台扫描或分析更新而替换；未变化的提示保持同一个对象。行操作更新为最新记录，渲染与点击目标使用同一份结果列表。
- 商品目录、适用附魔目录、附魔书统计与可搜索文字缓存按物品、记录、语言及世界变化更新；搜索结果仍重新匹配，JECH 设置不会被旧的匹配布尔缓存覆盖。
- JECH 4.6.9 是可选编译依赖。存在 `jecharacters` 时才加载独立适配类，调用其公开 `Match.contains` 方法，沿用它的拼音、简拼、双拼和模糊音设置。未安装时保留中文、英文及 ID 子串搜索。
- 村民、附魔书、物品选择器和附魔选择器统一使用大小写无关的匹配入口。搜索提示随 JECH 是否安装变化，发布包不内置 JECH、PinIn 或新的必装依赖。存储格式保持不变。
- 配置切换测试发现 PinIn 1.6 的加速缓存只随查询变化清理，同一查询可能暂时沿用旧配置。适配类检测 JECH 的键盘与模糊音设置变化后，先经公开匹配方法执行空查询，使下一次实际查询重新匹配；不修改 JECH 设置或私有字段。

## 执行命令

```sh
JAVA_HOME=/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu ./gradlew test runClientGameTest build --console=plain
JAVA_HOME=/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu ./gradlew test runClientGameTest build -PwithJech=true --console=plain
```

`-PwithJech=true` 只给开发运行增加 JECH 4.6.9（Modrinth 版本 ID `fTpybBSu`），不会将它打包到发布 JAR。省略该参数即测试没有 JECH 的独立运行。

## 自动化验收

| 场景 | 检查 |
|---|---|
| 输入稳定性 | 四个搜索框经真实客户端 Unicode 文本事件逐字输入后仍是同一个对象并保持焦点；再跨 65 tick 验证光标、选区、横向滚动位置不变 |
| 悬浮稳定性 | 真实鼠标悬停；探测关闭、开启、再关闭，并主动扫描，跨多个刷新周期断言行对象、提示对象和悬停状态不变 |
| 实时数据 | 修改村民备注后立即搜到新内容；扫描、保留状态、筛选数量、详情及导航通过完整既有客户端回归 |
| 四个拼音入口 | JECH 环境下验证 `jingyanxiubu`、`jyxb`、`经验xiubu` 匹配经验修补，`boli`、`BL`、`bo璃` 匹配玻璃；未安装时相同拼音不命中中文名称 |
| 普通搜索 | 两种环境都验证中文名称、英文名称和完整物品／附魔 ID，英文查询不区分大小写 |
| JECH 配置 | 在测试进程中关闭／开启 sh→s 模糊音、切换小鹤双拼，立即验证匹配变化，随后恢复原配置；不修改用户实例配置 |
| 缓存失效 | 备注变更与中英文资源重载后结果更新，切换语言不沿用旧目录文字 |
| 既有行为 | 商品增删改、连续删除、附魔适用范围、筛选、透明布局、自动探测、导航及数据清理完整回归 |

单元测试覆盖普通中文／英文／ID 搜索、土耳其语系统区域下的大小写处理、空查询，以及可选匹配器的结果不被缓存。

## 验证结果与截图

两种环境均为 `BUILD SUCCESSFUL`。87 项 JUnit 全部通过，失败、错误及跳过均为 0；两次完整客户端回归均通过，包括商品增删改、附魔限制、探测、导航和数据清理。

- 无 JECH：[完整日志](stable-search-no-jech.log)，新增场景标记为 `VV_STABLE_SEARCH_PASS JECH=false`。
- JECH 4.6.9：[完整日志](stable-search-jech.log)，新增场景标记为 `VV_STABLE_SEARCH_PASS JECH=true`，包括同一查询下的模糊音动态切换及小鹤双拼。
- 新增单元测试：[SearchTextTest 结果](TEST-cn.ericcraft.villagerverdict.SearchTextTest.xml)。

已检查中英文、宽窄布局及两种环境下的截图。悬浮检查前后提示保持可见，查询文本及结果显示正确。

| 场景 | 无 JECH | 安装 JECH |
|---|---|---|
| 悬浮检查前 | [截图](stable-search-hover-before-jech-false.png) | [截图](stable-search-hover-before-jech-true.png) |
| 跨多个刷新周期后 | [截图](stable-search-hover-after-jech-false.png) | [截图](stable-search-hover-after-jech-true.png) |
| 中文附魔书宽屏 | [中文查询](stable-search-books-zh_cn-1-jech-false.png) | [简拼查询](stable-search-books-zh_cn-1-jech-true.png) |
| 中文附魔书窄屏 | [中文查询](stable-search-books-zh_cn-2-jech-false.png) | [简拼查询](stable-search-books-zh_cn-2-jech-true.png) |
| 物品选择器 | [玻璃](stable-search-items-zh_cn-jech-false.png) | [boli](stable-search-items-zh_cn-jech-true.png) |
| 附魔选择器 | [经验修补](stable-search-enchantments-zh_cn-jech-false.png) | [jyxb](stable-search-enchantments-zh_cn-jech-true.png) |
| 英文附魔书 | [MENDING](stable-search-books-en_us-jech-false.png) | [MENDING](stable-search-books-en_us-jech-true.png) |

## 发布包

`build/libs/VillagerVerdict-1.0.0.jar`，260,968 字节。SHA-256：`bc1072d7b70c368a6ec40b7bde36be2962ac23823b45c37a5ce6c02a3ced9c5a`。

已检查编辑器、实时控件、JECH 适配器及中英文资源在包内；未打包 JECH、PinIn、嵌套依赖、JUnit 或客户端测试类，模组描述符没有新增 JECH 必装依赖。详见 [artifact-check.json](artifact-check.json)。

## 原生输入法验证限制

已启动项目隔离的原生开发客户端尝试检查 macOS 中文输入法候选窗，但桌面控制工具未提供 Java 游戏窗口，按 `java` 和 `Minecraft` 连接均返回 `Invalid app`。因此未完成系统候选窗的人工检查；自动化验证覆盖的是游戏内 Unicode 输入事件、输入框对象、焦点、光标、选区和横向滚动状态，不能等同于系统候选窗验证。尝试结束后已关闭该开发客户端。

## 窄屏详情选中项回退验证

2026-11-10：将村民筛选结果计算及商品筛选下的选中项校正移到列表／详情分支之前；列表复用同一份结果。窄屏详情中，选中村民离开后会选择首个剩余匹配项，没有匹配项时清空选择。

新增 `testMobileProductSelection` 使用两位提供经验修补的真实村民，经商品筛选和列表按钮进入窄屏详情，保持列表隐藏并依次卸载选中村民及最后一位匹配村民。断言屏幕及详情模式保持不变、筛选结果实时更新、选择正确回退／清空，以及定位和编辑按钮正确启用／禁用；结束后恢复 GUI 缩放和商品查询并清理实体。

修复前运行新增用例，在 `narrow product detail did not reconcile selection after seller unload` 断言处失败，确认覆盖原回归。修复后执行：

```sh
JAVA_HOME=/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu ./gradlew test runClientGameTest --console=plain
JAVA_HOME=/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu ./gradlew runClientGameTest -PwithJech=true --console=plain
```

87 项 JUnit 全部通过，失败、错误及跳过均为 0。无 JECH 完整回归为 `BUILD SUCCESSFUL in 2m 22s`，JECH 4.6.9 完整回归为 `BUILD SUCCESSFUL in 2m 17s`；两种环境均输出 `VV_MOBILE_PRODUCT_SELECTION_PASS`，并通过原有稳定搜索、探测、导航、界面和数据清理回归。所有运行均使用项目隔离目录。

实际输出摘录：[验证日志](mobile-product-selection.log)。完整输出分别保存在项目的 `build/mobile-product-selection-before-fix.log`、`build/mobile-product-selection-no-jech.log` 和 `build/mobile-product-selection-jech.log`。

已检查窄屏截图：[回退到剩余村民](mobile-product-selection-fallback.png)、[无匹配项且定位／编辑禁用](mobile-product-selection-empty.png)。
