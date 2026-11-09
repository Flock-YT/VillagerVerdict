# 交易页查看替代验证

- 集成日志：[replacement-search-integration.log](replacement-search-integration.log)
- 交易页按钮：[replacement-merchant-button.png](../../build/run/clientGameTest/screenshots/0008_replacement-merchant-button.png)
- 当前村民与联合替代高亮：[replacement-joint-highlights.png](../../build/run/clientGameTest/screenshots/0009_replacement-joint-highlights.png)

客户端集成用例 `VV_REPLACEMENT_SEARCH_PASS` 覆盖真实交易页、扫描上下文释放后的关联、联合替代、部分/无替代、标记总开关、窗口缩放、取消查找、切换村民、判定更新和目标卸载。验证命令：

```sh
JAVA_HOME=/Users/eric/.sdkman/candidates/java/25.0.5.0+8-zulu ./gradlew test runClientGameTest build --console=plain
```

结果：73 个 JUnit 测试通过；客户端集成测试通过；构建成功。

后续职业商品规则、商品查询和最终回归见 [signature-products.md](signature-products.md)。普通交易已不再参与替代查找。
