# 70 项实施对照

本表保留原有 67 项能力索引，并追加快捷键管理、数据清理和快捷键反馈功能。所有行均有业务代码，验证程度单独列明；“代码”或“未验证”不等于实测通过。

| 编号 | 能力 | 实现位置（类/方法） | 验证边界 |
|---|---|---|---|
| 01 | 实体加载/卸载索引 | `VerdictService.load/unload` | 集成：301 实体 |
| 02 | 三维距离附近清单 | `VerdictService.scan` | 集成：48 格半径内 47 格与半径外 49 格对象 |
| 03 | 20 tick / 80 格默认及手动扫描 | `Settings / VerdictService.scan` | 集成：主动扫描 |
| 04 | 注册表职业和等级 | `VerdictService.scan` | 集成：图书管理员、农民及失业职业转换 |
| 05 | 幼年/无业/傻子排除扫描、存储、列表和探测 | `VillagerRecord.tradable` | 单元及真实客户端集成，包含获得/失去职业与转职快照清理 |
| 06 | 服务器/存档/数据集/维度/UUID 隔离 | `WorldIdentity / VerdictService.switchWorld` | 单元与双存档集成 |
| 07 | 实时索引与持久记录分离 | `VerdictService.loaded / records` | 代码；卸载生命周期未完整实测 |
| 08 | 正常人工交互捕获 | `UseEntityCallback / VerdictService.offers` | 真实交易包集成 |
| 09 | 基础/实际/第二输入/输出 | `Trade / ItemCodecAdapter` | 单元与普通物品集成 |
| 10 | Codec 和当前注册表保存组件 | `ItemCodecAdapter` | 普通物品集成；复杂组件未实测 |
| 11 | 附魔 ID 和等级保存 | `ItemData.enchantments` | 单元；真实附魔交易未专项实测 |
| 12 | 库存/职业等级/检查时间 | `TradeSnapshot / Trade` | 单元与集成 |
| 13 | 唯一交互上下文关联 | `ProbeController.Context / PacketListenerMixin` | 状态机单元与正常包集成 |
| 14 | 完整交易标识保留数量/组件 | `TradeKey / ItemData` | 单元；语言切换不改标识 |
| 15 | 商品比较组独立 | `CommodityKey / Optimizer` | 单元 |
| 16 | 单位产出/不同资源/双输入向量 | `TradeComparison / Trade.cost` | 单元 |
| 17 | 基础与实际价格分离 | `TradeComparison / ComparisonResult` | 单元与详情截图 |
| 18 | 当前/未解锁/未知/过期完整度 | `TradeSnapshot.Completeness / VerdictScreen` | 单元与详情截图 |
| 19 | 新旧仅内存比较并覆盖 | `SnapshotDelta / VerdictService.offers` | 单元 |
| 20 | 记录变更更新分析和索引 | `VerdictService.changed / analyze` | 集成；后台代次校验 |
| 21 | 高级附魔优先于价格 | `ItemData.dominates / TradeComparison` | 单元 |
| 22 | 装备其他组件及逐项附魔保守比较 | `ItemData.sameValue / dominates` | 单元；附魔集合不同时不替代 |
| 23 | 不同附魔等级独立存储展示 | `TradeSnapshot / VerdictScreen` | 单元；真实多等级 GUI 未专项实测 |
| 24 | 初始空目标，全部已知商品覆盖 | `Settings / WorldData.empty / Optimizer` | 单元与集成 |
| 25 | 目标价格条件和最低保留数 | `TargetTrade / Optimizer` | 单元 |
| 26 | 确定性贪心多重覆盖 | `Optimizer` | 单元及 500/4000 基准 |
| 27 | 冗余移除和不可满足约束 | `Optimizer` | 单元 |
| 28 | 保护/人工保留固定纳入 | `VillagerRecord.fixed / Optimizer` | 单元 |
| 29 | 人工淘汰排除并报缺口 | `Optimizer` | 单元；状态互斥，保护需显式切换才能解除 |
| 30 | 综合所有交易而非单交易定性 | `Optimizer` | 交叉交易覆盖单元 |
| 31 | 绿/黄/红状态 | `AnalysisResult / Optimizer / renderer` | 单元及普通世界截图 |
| 32 | 高/中/低置信度与未来交易风险 | `Optimizer` | 单元 |
| 33 | 替代 UUID/坐标/交易/价差及跳转 | `ComparisonResult / VerdictScreen.details` | 单元；点击跳转未专项实测 |
| 34 | 扫描半径内全部职业成年村民状态标记 | `EntityRendererMixin / prepared Marker` | 301 场景；视觉细节未完整验收 |
| 35 | 扫描半径内多行职业标记，不受旧文字距离设置截断 | `ProfessionHighlights / MarkerText / MarkerRenderer` | 13 种职业筛选单元测试；双语真实交易、附魔最高等级、钻石装备、自定义名称、全部开关、状态复用、转职、隔墙及远近/拥挤截图 |
| 36 | 彩色实体轮廓 | `EntityRendererMixin` | 黄色轮廓无遮挡/隔墙截图；红绿轮廓未专项实测 |
| 37 | 轮廓及隔墙显示固定启用 | `EntityRendererMixin` | 客户端渲染状态断言及隔墙截图 |
| 38 | 状态文字、摘要开关和标记总开关 | `Settings.statusMarks/summaries/markers` | 配置与原生控件；全组合未验证 |
| 39 | 已有发光保留，附加文字/符号 | `EntityRendererMixin` | 原版发光保留集成；多模组冲突未验证 |
| 40 | 管理统计和扫描/探测入口 | `VerdictScreen / VerdictService.stats` | 集成 |
| 41 | 滚动列表、选中详情及透明管理界面 | `VerdictScreen / WorldScreen / WorldConfirmScreen` | 中英文昼夜宽窄截图；所有管理页及确认框无整屏背景/模糊、不暂停，未加载世界时恢复原版背景 |
| 42 | 职业/物品/附魔/等级/价格搜索 | `VerdictScreen.search` | 代码；所有搜索组合未验证 |
| 43 | 价格/等级/距离/职业/综合排序 | `VerdictScreen.filtered` | 单元价格；所有 GUI 排序未逐项操作 |
| 44 | 全交易、图标、提示、备注和标签 | `VerdictScreen / FormScreen` | 详情/图标截图；备注标签 CRUD 未完整实测 |
| 45 | 目标完整编辑/删除 | `VerdictScreen.target / FormScreen` | 集成创建；编辑删除未逐项实测 |
| 46 | 颜色、频率、缓存、提醒设置 | `SettingsScreen` | 表单截图；全字段修改未完整实测 |
| 47 | 单写入队列与合并 | `JsonStore` | 100 次写入单元及真实场景 |
| 48 | 损坏/未来版本写保护 | `JsonStore.read` | 单元 |
| 49 | 最新快照、重启和迁移 | `WorldData / JsonStore / Settings` | 单元 |
| 50 | 临时文件原子替换及世界异步切换 | `JsonStore.atomic / VerdictService` | 单元与双存档集成 |
| 51 | 同维度长方体区域创建/编辑 | `TradingHallRegion / VerdictScreen.region` | 单元与创建集成 |
| 52 | 重叠区域独立分析和目标 | `VerdictService.regional / updateTarget` | 代码；重叠实际场景未验证 |
| 53 | 只统计当前已发现加载对象 | `VerdictService.stats` | 集成；无业对象单独统计 |
| 54 | 最近待检查导航及卸载最后位置 | `navigateNext / HUD / ShortcutActions` | 真实导航快捷键及坐标提示通过；无目标提示通过；卸载后的完整导航路径未专项实测 |
| 55 | 自动探测默认关闭/按服务器保存 | `Settings.autoServers / ProbeController` | 单元与集成 |
| 56 | 首次启用额外交互说明 | `SettingsScreen.toggleProbe / WorldConfirmScreen` | 真实快捷键打开、确认/取消、探测开启/暂停及 Action Bar 提示通过 |
| 57 | 串行队列及正常/异常状态链 | `ProbeController` | 18 项状态机单元 |
| 58 | 间隔/超时/冷却/缓存/退避/三超时停止 | `ProbeController / Settings` | 单元；真实成功流程集成 |
| 59 | GUI/光标/双手/潜行/距离/遮挡门槛 | `VerdictService.safeBaseline / hitPoint` | 正常门槛集成；所有拒绝条件未逐项实测 |
| 60 | 无界面保留菜单同步并安全关闭 | `GuiMixin / PacketListenerMixin / VerdictService` | 真实交易包与菜单集成 |
| 61 | 人工优先、错配停止、世界切换取消 | `ProbeController / Fabric callbacks` | 单元和世界切换；真实争用未验证 |
| 62 | 可解释的分项综合排序，无统一线性分 | `Optimizer.prefer / VerdictScreen.filtered` | 单元和代码；不承诺数学最优 |
| 63 | 原版仅注册 F8 管理键，其他项由模组管理 | `VillagerverdictClient / ShortcutScreen` | 原版列表仅一项；中英文分类名；框架模拟改绑、清除和重置 |
| 64 | 宽屏并排/窄屏分页、滚动、键盘焦点 | `VerdictScreen / FormScreen` | 宽窄截图；全键盘路径未验证 |
| 65 | 扫描半径控制标记范围、选中优先 | `Settings / prepareMarkers` | 301 场景全部标记；旧上限/距离设为 1 仍标记远处对象 |
| 66 | 中英文和物品随当前语言显示 | `lang/en_us.json / zh_cn.json / ItemCodecAdapter` | 真实资源重载及双语截图 |
| 67 | 目标提示、声音及价格/解锁/库存变更去重 | `SnapshotDelta / fingerprint / notifyChanges` | 变化和语言去重单元；声画提醒未专项实测 |

| 68 | 按世界或服务器清空村民记录并保留配置 | `DataScreen / VerdictService / WorldData` | 单元隔离和配置保留；真实 GUI 确认/取消、清理和重扫 |
| 69 | 管理页快捷键改绑、清除和恢复默认 | `ShortcutScreen` | 旧 options 不恢复 F9/F10；独立文件及新控制器恢复；原版与模组管理键同步 |
| 70 | 游戏内快捷键即时状态提示 | `ShortcutActions / Hud.setOverlayMessage` | 真实按键验证标记双向切换、扫描人数、定位/无目标、探测授权和暂停；清理中扫描提示不可用；自动操作和改绑不刷提示 |

## 职业标记规则

仅从已采集的交易产物筛选，不以村民解锁等级推断商品。附魔合并最高等级，其他物品按显示名称去重，保持首次出现顺序。没有符合条件的交易或未定义规则的自定义职业仅显示状态和等级。

| 职业 | 标志性高阶交易 |
|---|---|
| 图书管理员 | 附魔书中的附魔名称 |
| 盔甲匠 | 钻石头盔、胸甲、护腿、靴子 |
| 武器匠 | 钻石剑、钻石斧 |
| 工具匠 | 钻石镐、斧、锹、锄 |
| 制箭师 | 附魔弓、附魔弩、药水箭 |
| 渔夫 | 附魔钓鱼竿 |
| 农民 | 金胡萝卜、闪烁的西瓜片 |
| 牧师 | 末影珍珠、附魔之瓶 |
| 制图师 | 探索地图，保留目的地名称 |
| 皮匠 | 马鞍、皮革马铠 |
| 石匠 | 石英块、石英柱、带釉陶瓦 |
| 牧羊人 | 旗帜、画 |
| 屠夫 | 熟鸡肉、熟猪排 |

## 20 项职责边界

| 职责 | 实现 |
|---|---|
| 1 客户端注册和按键 | VillagerverdictClient |
| 2 配置、默认值、迁移 | Settings / SettingsScreen |
| 3 世界身份和数据集 | WorldIdentity / switchWorld |
| 4 实体生命周期索引 | VerdictService.load / unload |
| 5 距离扫描和附近清单 | VerdictService.scan |
| 6 持久记录与人工状态 | VillagerRecord / VerdictService.edit |
| 7 异步版本化存储 | WorldData / JsonStore |
| 8 游戏物品组件适配 | ItemCodecAdapter |
| 9 协议接入与窗口关联 | ClientPacketListenerMixin / Context |
| 10 人工交易捕获 | UseEntityCallback / offers |
| 11 自动队列及退避 | ProbeController |
| 12 菜单抑制、安全关闭和恢复 | GuiMixin / VerdictService |
| 13 成本和商品比较 | Trade / TradeKey / CommodityKey / TradeComparison |
| 14 保留集合规划 | Optimizer |
| 15 原因、置信度和替代说明 | ComparisonResult / AnalysisResult |
| 16 目标与价格条件 | TargetTrade / updateTarget |
| 17 区域及独立分析 | TradingHallRegion / regional |
| 18 管理 GUI、搜索、滚动和表单 | VerdictScreen / FormScreen |
| 19 世界标记和导航 | prepareMarkers / EntityRendererMixin / HUD |
| 20 变化提醒与指纹去重 | SnapshotDelta / TradeSnapshot.fingerprint / notifyChanges |

模型、比较、覆盖、存储和状态机不依赖 Minecraft 类。客户端集成测试模组及 JUnit 不进入发布 JAR。代码实现与未完成的运行验收需分别评价，详见 TEST_REPORT.md。
