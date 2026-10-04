# 栗小铺

项目是一个 Maven 模块：根目录只有一个 `pom.xml`，Java 源码和资源分别位于 `src/main/java`、`src/main/resources`。参照栗子商城示例，源码在 `com.lixiaopu` 下按职责分类，用户端和管理端共用实体、Mapper、认证与业务服务，分别由两个 Spring Boot 启动类启动。

| 应用 | 启动类 | 默认端口 | 静态资源 |
| --- | --- | --- | --- |
| 用户端 | `com.lixiaopu.LixiaopuApplication` | 8081 | `src/main/resources/static` |
| 管理端 | `com.lixiaopu.AdminApplication` | 8082 | `src/main/resources/admin-static` |

`src/main/java/com/lixiaopu` 的主要分类：

| 包 | 内容 |
| --- | --- |
| `controller/user`、`controller/admin`、`controller/tool` | 用户端、管理端和工具接口 |
| `service`、`service/impl` | 业务接口与实现 |
| `mapper` | MyBatis Mapper 接口 |
| `pojo/entity`、`pojo/dto`、`pojo/vo`、`pojo/query`、`pojo/enums` | 实体、传输对象、视图对象、查询参数和枚举 |
| `security`、`properties`、`common` | 认证安全、配置属性、通用类型与工具 |
| `infrastructure`、`aop`、`job`、`application` | 外部系统适配、切面、任务和管理端初始化 |
| `src/main/resources/mapper` | MyBatis XML 映射文件，与配置中的 `mapper-locations` 对应 |

用户端入口扫描 `controller/user`、`controller/tool` 以及所需的共享包；管理端入口扫描 `controller/admin`、`application` 以及所需的共享包。两端都通过 `@MapperScan` 注册 `mapper` 接口。

在 IDEA 中以 Maven 项目打开根目录，使用 JDK 17，分别运行上表中的 `main` 方法。两个应用是两个独立进程，可以同时运行。根目录执行 `mvn test` 可编译并运行单元测试；执行 `mvn package` 会生成 `target/lixiaopu-0.0.1-SNAPSHOT.jar`（用户端）和 `target/lixiaopu-0.0.1-SNAPSHOT-admin.jar`（管理端）。

本机配置文件 `src/main/resources/application-user.yml`、`src/main/resources/application-admin.yml` 被 `.gitignore` 忽略。新检出工程时，可将 `config/application-user.example.properties`、`config/application-admin.example.properties` 分别复制为 `src/main/resources/application-user.properties`、`src/main/resources/application-admin.properties`，再配置可用的 MySQL、Redis 和两端相同的 `LXP_JWT_SECRET`。复制后的属性文件同样被 Git 忽略；不要把本机密码或密钥提交到仓库。数据库建表与角色初始化步骤见 [数据库说明](docs/数据库说明.md)。

原多模块代码的修错前参考归档保存在 `backups/`，该目录也被 Git 忽略。具体错误及本次合并范围见 [报错定位与重构说明](docs/报错定位与重构说明.md)。
