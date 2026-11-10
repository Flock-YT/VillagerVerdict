# 职业典型交易详情验证

验证日期：2026-11-10。Java 25.0.5、Minecraft 26.3、Fabric Loom 1.17.21。使用项目内隔离的 Gradle 客户端测试目录，没有修改 PrismLauncher 实例、模组、配置或存档。

## 行为与数据格式

- 管理页只保留村民列表与找商品两个标签。区域编辑、区域筛选、区域专属查询、独立分析、通知匹配和持久化字段均已删除。
- 详情按 `ProfessionHighlights.trades(record)` 逐条筛选，保留不同等级、不同价格及相同商品的独立交易。图书管理员展示附魔名称和等级，盔甲匠展示钻石盔甲及附魔，武器匠展示钻石剑和钻石斧；其他职业沿用现有规则。
- 每行仅显示产物图标、商品/附魔和每次交易所需的基础绿宝石原价。多件产出显示数量；折扣、涨价、库存次数和额外输入材料不进入交易行。长附魔文本自动换行。
- 已扫描但没有典型商品显示专用空状态；未扫描保持查看交易提示。完整快照继续保存在缓存中，商品查询、保留分析和替代关系使用既有规则。
- 世界数据格式为版本 5，仅包含版本、身份、交易缓存和商品查询。旧开发格式不迁移，读取失败的文件仍受写保护。

## 验证结果

```sh
JAVA_HOME=/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu ./gradlew clean build runClientGameTest --console=plain
```

结果：`BUILD SUCCESSFUL`；81 项 JUnit 通过，失败、错误及跳过均为 0。客户端集成测试通过。

| 场景 | 验证 |
|---|---|
| 非典型交易排除 | 附魔书详情不包含书架或种子；盔甲匠排除铁甲；武器匠仅保留钻石武器 |
| 等级与价格逐条保留 | 保护 3 / 原价 16、保护 4 / 原价 17、保护 4 / 原价 18 三条交易独立存在 |
| 基础原价 | 实际价格为 1 或 40、库存耗尽时，原价摘要保持一致；多件产出保留每次交易价格 |
| 双语宽窄布局 | 英文、中文 × GUI 缩放 1、2；断言真实渲染文字并生成截图 |
| 空状态 | 已扫描无典型商品与未扫描状态分别断言提示 |
| 存储 | 版本 5 往返读写无区域字段；清理交易后保留商品查询；旧格式读取失败后禁止覆盖 |
| 其他客户端流程 | 查询增删改、价格筛选、自动/人工捕获、职业筛选、导航、世界隔离、清理期间世界切换全部回归通过 |
| 发布包 | 区域类、客户端测试模组及 JUnit 均未进入 JAR；中英文资源不包含区域功能键 |

日志：[signature-details-integration.log](signature-details-integration.log)。新场景通过标记为 `VV_SIGNATURE_DETAILS_PASS`；其他场景包括 `VV_SIGNATURE_PRODUCTS_PASS`、`VV_DATA_CLEAR_PASS` 和 `VV_CLEAR_TRANSITIONS_PASS`。

## 截图

已检查图标、原价、附魔换行及布局；所有截图由真实客户端集成测试生成。

- 图书管理员：[中文宽屏](signature-details-librarian-zh_cn-wide.png)、[中文窄屏](signature-details-librarian-zh_cn-narrow.png)、[英文宽屏](signature-details-librarian-en_us-wide.png)、[英文窄屏](signature-details-librarian-en_us-narrow.png)。
- 盔甲匠：[中文窄屏](signature-details-armorer-zh_cn-narrow.png)、[英文窄屏](signature-details-armorer-en_us-narrow.png)。
- [武器匠](signature-details-weaponsmith-zh_cn-narrow.png)、[已扫描无典型交易](signature-details-empty-zh_cn-narrow.png)。

## 发布产物

`build/libs/VillagerVerdict-1.0.0.jar`，234,954 字节。

SHA-256：`02e822d5693c4ee82be05803148e381d8155e96b0b9d1c4b43cc059113301424`。详细元数据与包检查见 [artifact-check.json](artifact-check.json)。
