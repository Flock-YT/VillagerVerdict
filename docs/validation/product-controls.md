# 商品附魔选项与快捷删除验证

验证日期：2026-11-10。Java 25.0.5、Minecraft 26.3。构建与客户端测试使用项目内隔离目录，未修改 PrismLauncher 游戏实例。

## 行为

- 附魔书可以选择注册表中的附魔；装备只显示当前物品支持的附魔。玻璃等没有适用附魔的商品不显示附魔选择、清除和等级控件。
- 商品切换保留仍适用的附魔，清除不适用的附魔，等级限制在附魔的有效范围内。完整 ID 搜索同样遵守适用范围，不提供绕过校验的原始附魔 ID 按钮；保存时再次校验。
- 普通商品的价格控件上移至 Y=74，价格编号为“②”，预览随之上移；切换商品保留价格。
- 商品列表每行最右侧为 20×20 垃圾桶按钮，图标使用绘制的线条，不依赖字体字符。中英文提示和读屏文本包含完整商品条件。
- 点击垃圾桶立即删除并保存、刷新列表，保留可用的滚动位置；删除末尾后修正偏移，删空后显示空状态。删除当前用于筛选的商品会清除对应筛选。编辑页删除入口继续可用。
- 生产修改位于 client 源集及中英文资源；测试位于 gametest 源集。公共 API、世界数据版本和存储结构未变化。

## 验证

```sh
JAVA_HOME=/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu ./gradlew test runClientGameTest build --console=plain
```

结果：`BUILD SUCCESSFUL`。84 项 JUnit 通过，失败、错误及跳过均为 0；完整客户端回归通过。新增客户端场景通过标记：`VV_PRODUCT_CONTROLS_PASS`。日志：[product-controls-integration.log](product-controls-integration.log)。

| 场景 | 断言 |
|---|---|
| 附魔书 → 钻石胸甲 | 保留保护 IV，最高等级时禁用加号 |
| 胸甲 → 钻石剑 → 附魔书 | 清除不适用附魔；锋利 V 在切回附魔书时保留 |
| 修补与弓 | 切换至修补时等级从 V 降至 I；切换到弓时保留修补 |
| 完整 ID 输入 | 弓可选择力量，锋利及未知 ID 不提供选项或原始 ID 绕过入口 |
| 弓 → 玻璃 | 清除力量 V，隐藏附魔控件；预览与保存无残留附魔，保存等级为 0，价格 23.5 保留 |
| 快捷删除 | 使用真实鼠标点击分发；停留在商品列表，仅删除对应商品并清除其活动筛选 |
| 保存与编辑页删除 | 关闭重开后继续删除；重新读取世界 JSON 验证删除结果，原编辑页删除可用 |
| 连续删除 | 24 条商品滚动到底部，删除最后一条后偏移减一；连续删空后偏移为 0，显示空状态并保存空列表 |
| 双语布局与读屏 | 英文、中文 × GUI 缩放 1、2；断言按钮尺寸、右侧位置、控件间距和包含商品条件的读屏文本 |

## 截图

已检查全部 13 张真实客户端截图：垃圾桶可辨认，按钮点击区域互不重叠；玻璃隐藏附魔后价格与预览上移，钻石装备保留完整附魔控件。

| 页面 | 英文宽屏 | 英文窄屏 | 中文宽屏 | 中文窄屏 |
|---|---|---|---|---|
| 商品列表 | [截图](product-controls-delete-en_us-wide.png) | [截图](product-controls-delete-en_us-narrow.png) | [截图](product-controls-delete-zh_cn-wide.png) | [截图](product-controls-delete-zh_cn-narrow.png) |
| 玻璃编辑 | [截图](product-controls-glass-en_us-wide.png) | [截图](product-controls-glass-en_us-narrow.png) | [截图](product-controls-glass-zh_cn-wide.png) | [截图](product-controls-glass-zh_cn-narrow.png) |
| 钻石胸甲编辑 | [截图](product-controls-armor-en_us-wide.png) | [截图](product-controls-armor-en_us-narrow.png) | [截图](product-controls-armor-zh_cn-wide.png) | [截图](product-controls-armor-zh_cn-narrow.png) |

[连续删空后的商品列表](product-controls-delete-empty-zh_cn.png)。

## 发布产物

`build/libs/VillagerVerdict-1.0.0.jar`，250,715 字节。SHA-256：`6bfec038baf41a17f7fcb01ea4879b3ea5a25baff685a0e8a73235ac3d0bed6c`。

已检查编辑器、垃圾桶按钮及中英文资源包含在 JAR 内，客户端测试模组与 JUnit 未打包。详细元数据及文件检查见 [artifact-check.json](artifact-check.json)。
