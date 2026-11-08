# Villager Verdict 1.0.0 测试报告

测试日期：2026-11-08。最终结果：**构建通过，131 项 JUnit 全部通过，真实客户端集成测试通过**。仍有未完成的运行验收，不能把本报告理解为全部 26 项验收通过，也不将本次交付称为全部验收完成。未收到独立的原始 26 条验收全文，下面按本次计划组织 26 个验收检查点。

## 环境、命令与产物

- macOS arm64；Zulu Java 25.0.5+8；Gradle Wrapper 9.6.1；Fabric Loom 1.17.21。
- Minecraft 26.3；Fabric Loader 0.19.5；Fabric API 0.163.0+26.3；OpenGL / Apple M6（游戏日志报告）。
- 开发客户端为离线测试账号 Player0；测试使用独立的超平坦单人存档，没有操作玩家存档或连接外部服务器。
- 使用本机 Java 25 执行：

```sh
JAVA_HOME='/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu' ./gradlew test runClientGameTest --console=plain
JAVA_HOME='/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu' ./gradlew clean build --console=plain
```

两条命令最终均为 `BUILD SUCCESSFUL`。干净构建没有跳过测试。客户端集成测试是单独运行的任务，不伪装为 `build` 自动覆盖的内容。`runClient` 也曾启动成功，窗口创建、资源加载和 Mixin 应用成功；由于 UI 工具不能连接独立 Java 进程，后续交互验证改用 Fabric 官方客户端测试框架。

成品：[VillagerVerdict-1.0.0.jar](build/libs/VillagerVerdict-1.0.0.jar)，154,476 字节。

SHA-256：`3fd5ad38b8118f6f92c98590d7a45db6b1160f780a59b4e4b8f1e53b4314d157`。

## 自动测试

| 测试类 | 数量 | 结果 | 范围 |
|---|---:|---|---|
| AnalysisTest | 30 | 通过 | 单位价格、货币隔离、双输入向量、组件、附魔等级、交叉覆盖、价格不同的备份、约束缺口、保护、人工排除、过期、置信度和解释 |
| ProbeTest | 23 | 通过 | 默认关闭、串行、窗口先后、容器错配、晚到、人工冲突、超时、退避、取消和本地关闭完成；注入时钟 |
| StorageTest | 14 | 通过 | 最新覆盖、重启恢复、身份隔离、损坏/null/未来版本保护、原子替换、两世界写入、重复 UUID 拒绝、世界封套和配置迁移 |
| DeltaTest | 5 | 通过 | 价格/库存/新增交易变化、不变快照及语言变化不改变交易标识/指纹 |
| PerformanceTest | 1 | 通过 | 500 名村民、4,000 条交易、120 种商品、保留两份覆盖 |
| WorldStorageTest | 4 | 通过 | 按世界/服务器清理隔离、配置保留、排队存盘、完成后重启、损坏/未来版本/身份错配保护、目录错误 |
| ShortcutBindingsTest | 3 | 通过 | 独立配置缺省值、原生旧按键不导入、重启恢复、损坏及未来版本保护 |
| VillagerEligibilityTest | 3 | 通过 | 成年职业与自定义职业纳入；失业、傻子和幼年排除 |
| ProfessionHighlightsTest | 48 | 通过 | 13 种职业的高阶商品、低阶排除、买入交易排除、附魔最高等级、多附魔、重复商品及地图/药水效果差异 |
| 总计 | 131 | 通过 | 失败 0，错误 0，跳过 0 |

客户端集成测试 `ClientIntegrationTest` 创建真实集成服务器和村民，使用真实服务器交易包。人工路径先调用 Fabric 交互事件，再走原版 `MultiPlayerGameMode.interact`，没有合成商人响应包；这验证事件后的完整协议链，不能代替物理鼠标/快捷键实测。自动路径由生产代码正常队列发起，检查隐藏菜单、内容同步、本地菜单恢复、光标为空、商人交易 uses 仍为 0。随后实际打开玩家背包和箱子，创建目标和区域，打开设置，切换中文、GUI 缩放和详情页面。最后加载 300 名额外无业村民并切换到另一个全新存档，检查数据没有串入。

本次通过原生按键页面检查本模组只出现管理键，中英文分类名均为 Villager Verdict。向隔离测试的旧 options 文件写入 F9/F10 后重新读取，内部快捷键仍未绑定；随后验证内部改绑不进入原版列表、加载完成前不能编辑、独立 JSON 保存及新控制器恢复、原版管理键改绑同步。通过框架模拟按键验证实际触发、冲突解绑、Esc 清除和恢复默认；通过确认/取消页面清空世界及服务器村民记录，验证磁盘、内存、异步分析重置、重新扫描和清理期间修改目标仍能保存。扫描半径设为 48、旧标记上限和文字距离均设为 1，范围内 47 格对象仍有标记，范围外 49 格对象无标记。

日志中的最终断言标识：`VV_SHORTCUTS_PASS`、`VV_DATA_CLEAR_PASS`、`VV_INTEGRATION_PASS` 和 `VV_WORLD_ISOLATION_PASS`。程序集成截图由测试框架在真实渲染后输出，已人工查看管理、详情、中英文、设置和拥挤场景截图。

## 26 个验收检查点

“通过”限定于本行所述范围；状态为“未验证”的项目即使有部分自动化证据，也不算整项验收完成。

| # | 检查点 | 状态 | 证据与边界 |
|---|---|---|---|
| 01 | 固定依赖、Java 25、Wrapper 和干净构建 | 通过 | 最终 clean build 日志；77 项测试重新执行 |
| 02 | 默认关闭时不主动探测 | 通过 | 状态机拒绝自动 begin；集成等待期间未自动获得快照 |
| 03 | 实体索引、扫描、附近清单和规模 | 通过 | 实际加载 303 实体；48 格半径内的 47 格对象标记，49 格对象不标记 |
| 04 | 职业/幼年/无业/傻子处理 | 未验证 | 图书管理员和 300 无业对象通过；幼年/傻子未专项实测 |
| 05 | 人工交易捕获与唯一关联 | 通过 | 真实菜单、容器 ID 和服务器交易包写入一条快照；人工事件由测试直接调用 |
| 06 | 复杂组件和真实附魔序列化 | 未验证 | 普通物品 Codec 集成和模型组件单元通过；未覆盖真实地图、染色、装备、多附魔等注册表组件 |
| 07 | 单位价格和基础/实际价格 | 通过 | 归一化、数值价格顺序及详情基础/实际价格显示 |
| 08 | 双输入成本及不同货币不换算 | 通过 | 多资源不可比较和重复资源合并单元 |
| 09 | 高级附魔优先及组件保守比较 | 通过 | 更贵高级替代低级、同级比价、逐项附魔取舍和组件差异单元 |
| 10 | 目标、价格上限及不同价格备份 | 通过 | 最高等级超上限仍保留、备份价格条件不足、不同价格备份覆盖单元 |
| 11 | 人工保护、保留、排除和覆盖缺口 | 通过 | 保护固定纳入、排除报缺口及多交易覆盖单元；状态为互斥人工选择 |
| 12 | 最新快照覆盖与重启恢复 | 通过 | 写两次后重新创建存储对象，仅恢复最新交易 |
| 13 | 世界隔离和异步切换 | 通过 | 多身份单元；双存档隔离；世界/服务器清理后目标和区域保留，其他服务器不变 |
| 14 | 损坏文件保护、迁移、合并和原子替换 | 通过 | 损坏/null/未来版本不覆盖；100 次写入无残留 tmp；版本迁移单元 |
| 15 | 串行、超时、退避和三超时停用 | 通过 | 可注入时钟的确定性状态机测试；不是网络故障环境实测 |
| 16 | 人工争用、晚到包及错配的真实网络表现 | 未验证 | 状态机冲突、隔离期和错配单元通过；真实乱序/争用/网络延迟未注入 |
| 17 | 无界面菜单生命周期和安全关闭 | 通过 | 正常原版集成服务器：无 MerchantScreen 泄漏，内容同步后菜单恢复，没有购买 |
| 18 | 探测后的背包/箱子与交易无副作用 | 通过 | 实际打开 InventoryScreen 和 ContainerScreen；光标为空；服务器 uses=0 |
| 19 | 全部 GUI 的增删改查、悬浮、搜索排序和键盘路径 | 未验证 | 目标/区域创建、详情及设置页面通过；没有逐项操作所有编辑/删除/滚动/筛选/替代跳转组合 |
| 20 | 中英文和宽窄窗口布局 | 通过 | 原生资源重载切中文；逻辑宽度 854/427 的双栏及分页截图，物品名称随当前语言显示 |
| 21 | F8 默认管理键及快捷键管理页 | 通过 | 原版仅管理键且分类名正确；旧 F9/F10 隔离、独立配置恢复、实际触发、原版同步、改绑和恢复默认 |
| 22 | 扫描半径内全部职业成年村民状态文字和轮廓 | 通过 | 301 场景全部标记；旧上限和文字距离设为 1 时仍标记 47 格对象，49 格对象排除；红/绿实景未专项验证 |
| 23 | 隔墙、全部视觉组合和其他模组发光兼容 | 未验证 | 轮廓及隔墙固定启用；无遮挡/石墙后轮廓截图、实时渲染状态、总开关和原版发光保留验证通过；其他模组组合未验证 |
| 24 | 目标提示音、通知及库存/解锁/价格提醒 | 未验证 | 变化类型、最新指纹和语言去重单元通过；未逐项触发实际声画提醒验收 |
| 25 | 数百村民完整性能验收 | 未验证 | 扫描/分析/标记准备/写盘样本已记录；无 GPU 帧时间、长时间运行、磁盘故障或完整压力回归 |
| 26 | 无本模组的独立原版及 Fabric 服务器兼容 | 未验证 | 真实单人原版交易协议已通过；未启动独立服务器，未自动接受专用服务器 EULA，未进行公网/认证测试 |

## 性能样本

最终客户端集成日志：301 名已加载职业成年村民（只有原交易村民有交易快照），一次扫描 **559 µs**，后台分析 **1,690 µs**，准备标记 **137 µs**，异步 JSON 序列化及原子写盘 **13,129 µs**。标记准备不等于 GPU 渲染时间。该场景只适合检验实体规模和索引开销，不代表 301 份完整交易分析成本。

干净构建重新执行的合成基准：500 名村民、4,000 条交易、120 种商品，分析 **313.214 ms**，保留 30 人并满足两份覆盖。它是单次 JVM 样本，不是分位数或跨硬件承诺。生产代码将分析放到独立线程，完成后校验世界代次及是否有新数据，过期结果丢弃；JSON 也由单独写入线程完成。实体渲染提取阶段只访问已准备标记，不调用优化器。

## 证据与限制

- [客户端集成日志](docs/validation/client-integration.log)、[干净构建日志](docs/validation/clean-build.log)、[JAR 检查清单与 SHA-256](docs/validation/artifact-check.json)。
- JUnit XML 位于 `docs/validation/TEST-*.xml`，当前构建 HTML 报告位于 `build/reports/tests/test/index.html`。
- [英文管理](docs/validation/screenshots/0001_management-en.png)、[详情](docs/validation/screenshots/0002_details-en.png)、[宽屏](docs/validation/screenshots/0003_wide-management.png)、[中文管理](docs/validation/screenshots/0004_management-zh.png)、[背包](docs/validation/screenshots/0008_inventory-after-probe.png)、[箱子](docs/validation/screenshots/0009_chest-after-probe.png)、[301 实体场景](docs/validation/screenshots/0010_crowded-scene.png)。
- 新增截图：[英文快捷键](docs/validation/screenshots/0011_shortcuts-en.png)、[中文快捷键](docs/validation/screenshots/0012_shortcuts-zh.png)、[数据管理](docs/validation/screenshots/0013_world-data.png)、[清空确认](docs/validation/screenshots/0014_clear-world-confirm.png)、[47 格高亮](docs/validation/screenshots/0015_far-marker.png)。
- 原版按键列表截图：[英文](docs/validation/screenshots/0016_native-controls-en.png)、[中文](docs/validation/screenshots/0017_native-controls-zh.png)。两者本模组分类下均只有管理键。
- JAR 检查确认客户端入口、三个 Mixin、语言 JSON、图标、MIT 许可证、Minecraft/Loader/API/Java 声明齐全；测试入口和 JUnit 没有进入发布 JAR。

原版协议不能证明隐藏交易、后台存档切换身份、请求被服务器明确拒绝或关闭得到确认。实现只记录当前收到的交易，后台换世界需使用数据集名称；超时仅作为超时；完成仅说明本地关闭生命周期结束。贪心覆盖不保证数学最优。涉及这些限制的界面和文档不应做更强承诺。

## 职业筛选与固定轮廓回归

本轮使用 Java 25 完成 `test runClientGameTest` 和 `clean build`。新增集成场景验证失业、傻子和幼年村民不进入扫描记录、附近集合或手动探测；获得职业后纳入；转职清空旧快照；失业时清理记录、选择、探测及分析，磁盘不残留记录；重新获得职业时创建无旧快照的新记录。拥挤场景和距离边界改用职业成年村民。

标记总开关仍有效，轮廓与隔墙固定启用，设置字段和中英文开关文案已移除。实际截图显示职业村民有黄色轮廓，同场傻子和幼年村民无标记；石墙后仍能看到轮廓与摘要。原版发光颜色保留通过服务器真实同步验证；未验证其他第三方渲染模组。

- [集成日志](docs/validation/worker-filter-integration.log)
- [干净构建日志](docs/validation/worker-filter-clean-build.log)
- [无遮挡截图](docs/validation/screenshots/worker-outline-visible.png)
- [隔墙截图](docs/validation/screenshots/worker-outline-through-wall.png)
- [设置截图](docs/validation/screenshots/worker-settings.png)

## 转职后未扫描的手动交易捕获回归

手动探测成功建立上下文后，立即使用与扫描共用的记录刷新逻辑同步职业、等级及位置，转职时清空旧交易快照，保留人工标记、备注和标签。被清空数据后的响应隔离机制拒绝的探测不会创建记录；请求途中转职仍取消旧探测。

新增真实客户端场景关闭自动扫描，保留图书管理员记录及旧快照，将服务器村民转为农民后直接交互，不执行扫描。修复前断言 `manual probe did not refresh profession and clear old snapshot` 失败；修复后通过真实商人包采集小麦交易，并验证保护状态、备注和标签保留。

Java 25 执行 `./gradlew test runClientGameTest` 成功，83 项 JUnit 无失败，客户端新增场景及既有清理隔离等完整回归通过。日志：[手动转职捕获回归](docs/validation/manual-profession-integration.log)。本次仅验证代码及测试，未重新生成发布 JAR；上文产物信息对应此前的干净构建。

## 透明界面与快捷键反馈回归

管理页、表单、快捷键、数据管理及本模组确认框采用统一的世界背景处理：游戏内不绘制整屏底纹、不应用菜单模糊、不暂停游戏；详情、表单和统计区域保留局部半透明面板。统计行上移以避开游戏 HUD 的生命值区域。没有加载世界时，测试将背景绘制操作及模糊标记与原版确认框比较，确认仍保留原版菜单背景。

游戏内快捷键通过原版 Action Bar 显示结果。真实绑定并按键验证标记双向切换、附近扫描人数、定位坐标、无目标、探测授权取消/确认和暂停，并读取 HUD 的实际文本及剩余显示时间。清理操作的完成回调尚未执行时，在客户端线程排入绑定扫描键并分发客户端 tick，验证扫描被拒绝时提示暂不可用。自动扫描、直接后台导航及快捷键捕获均不会新增提示。中英文标记提示均经过真实按键验证，探测仍在授权确认后才启动。

渲染验证覆盖中英文、昼夜、GUI 缩放 1/2（854/427 逻辑宽度），检查所有管理页面和两类确认框没有整屏绘制、模糊或暂停；同时检查实际截图。Java 25 执行 `./gradlew test runClientGameTest`，83 项 JUnit 通过，完整客户端集成测试通过。

- [本轮集成日志](docs/validation/transparent-feedback-integration.log)
- [英文白天宽屏](docs/validation/screenshots/transparent-management-en-day-1.png)
- [中文夜间窄屏](docs/validation/screenshots/transparent-management-zh-night-2.png)
- [透明设置页](docs/validation/screenshots/transparent-settings-zh.png)
- [探测授权确认框](docs/validation/screenshots/transparent-probe-confirm-en.png)
- [数据清理确认框](docs/validation/screenshots/transparent-clear-confirm.png)
- [中文快捷键状态提示](docs/validation/screenshots/feedback-markers-zh.png)

本轮未重新生成发布 JAR，未修改 PrismLauncher 实例。未增加配置字段或变更默认快捷键绑定。

## 多行职业标记回归

头顶文字由原有单行摘要改为独立的多行命名牌：状态、等级、职业标志性高阶交易依次居中显示。仅筛选已收到的交易产物，附魔取已知最高等级；经验修补等最大等级为 1 的附魔不显示数字。13 种职业的商品范围见 FEATURES.md。内部 Marker 携带不可变行列表，经 Fabric 渲染状态附加数据在名称提交阶段逐行绘制，行距 11 个原版名称像素；原有自定义名称位于上方并保留颜色。无标记、关闭开关、转职或复用实体渲染状态时清除旧行。

新增 48 项 JUnit 用例，覆盖全部职业的入选商品、低阶和无关商品排除、不能把买入物资当出售商品、重复商品合并、附魔最高等级、多附魔书展开、不同地图目的地和药水效果保留，以及未知职业。全套 131 项 JUnit 无失败、无错误、无跳过。

真实客户端通过服务器商人包采集附魔书及装备交易，中文准确得到 `保留 / 等级：1 / 经验修补 / 保护4 / 耐久3`，英文准确得到 `Keep / Level: 1 / Mending / Protection 4 / Unbreaking 3`。盔甲匠仅列钻石护腿和钻石靴子，铁护腿被排除、重复钻石护腿合并。未采集或空快照只显示状态和等级。验证八种总开关/状态/摘要组合、同一渲染状态复用、非村民状态清理、自定义名称原色与高度，并拦截真实名称提交，核对行序、透视参数和逐行高度。

实景截图覆盖约 5 格近距离、约 19 格远距离、石墙遮挡和拥挤场景。拥挤场景的额外村民使用复制的已采集快照作为排版夹具；基础附魔书和钻石装备来自真实商人交互。每个村民内部的行距固定；不同村民靠近时文字仍可能在屏幕上重叠，未实现跨村民标签避让。转职测试关闭自动扫描，在职业数据同步后立即检查旧文字不再显示，再扫描确认旧快照清空。

Java 25 执行 `./gradlew test runClientGameTest` 成功，原有透明界面、快捷键反馈、职业筛选、数据清理隔离及世界切换回归均通过。本次未重新生成发布 JAR，未修改 PrismLauncher 实例。

- [本轮集成日志](docs/validation/multiline-integration.log)
- [职业筛选单元测试报告](docs/validation/TEST-cn.ericcraft.villagerverdict.ProfessionHighlightsTest.xml)
- [中文近距离与自定义名称](docs/validation/screenshots/multiline-marker-near-zh.png)
- [隔墙显示](docs/validation/screenshots/multiline-marker-wall-zh.png)
- [中文远距离](docs/validation/screenshots/multiline-marker-far-zh.png)
- [英文远距离](docs/validation/screenshots/multiline-marker-far-en.png)
- [拥挤场景](docs/validation/screenshots/multiline-marker-crowded-zh.png)
