# 栗小铺商城后端

Java 17 / Spring Boot 单 Maven 模块，包含用户端、管理端 REST API、商品搜索、购物车、订单、优惠券、微信支付与退款、Netty 客服 WebSocket。

仅包含后端生产源码、Mapper/Lua、数据库结构迁移和脱敏配置模板；不包含网页、小程序、Android、测试、数据库快照和真实凭据。

## 启动

1. 准备 JDK 17、Maven、MySQL、Redis、Elasticsearch、RocketMQ。
2. 新库执行 `src/main/resources/db/01-full-schema.sql` 和 `02-roles.sql`；已有库按版本选择迁移，不要重复执行全部 ALTER 脚本。
3. 将 `config/application-dev.example.yml` 复制为 `src/main/resources/application-dev.yml`，填写服务连接及随机 JWT 密钥；真实配置被 Git 忽略。第三方凭据按需填写。
4. 执行 `mvn -Dmaven.test.skip=true package`。
5. 用户端：`java -jar target/lixiaopu-0.0.1-SNAPSHOT.jar`（8081）；管理端：`java -jar target/lixiaopu-0.0.1-SNAPSHOT-admin.jar`（8082）。

商品只能关联启用的二级分类，其父级也须启用。周期调度默认关闭，仅为一个指定用户端实例添加 `--jobs.enabled=true`；多实例雪花节点组合须不同。微信支付默认关闭。此发布版本不打包静态前端或独立前端网关。
