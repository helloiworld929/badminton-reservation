# Agent 开发指南

## 项目概览

本仓库是一个羽毛球场地预约系统。

- 后端：Spring Boot 2.7.18，Java 11。
- 数据持久化：MySQL 8、MyBatis XML Mapper、PageHelper。
- 缓存与会话支持：Redis。
- 云服务：阿里云 OSS 文件上传、阿里云短信服务。
- 前端：由 Spring Boot 提供服务的静态 HTML、CSS 和 JavaScript。
- 构建产物：`target/badminton.jar`。

应用入口是 `com.badminton.BadmintonApplication`。

## 目录结构

- `src/main/java/com/badminton/controller`：用户端和管理端 HTTP 接口。
- `src/main/java/com/badminton/service`：预约、活动、用户、文件上传、短信和定时任务逻辑。
- `src/main/java/com/badminton/entity`：数据库实体。
- `src/main/java/com/badminton/dto`：请求和响应模型。
- `src/main/java/com/badminton/mapper`：MyBatis Mapper 接口。
- `src/main/resources/mapper`：MyBatis SQL XML。Mapper 接口方法必须与 XML 的 statement id 保持一致。
- `src/main/resources/schema.sql`：数据库结构和非敏感的初始参考数据。
- `src/main/resources/static`：浏览器页面和前端 API 调用。
- `src/main/resources/application.properties`：运行配置和环境变量绑定。
- `Dockerfile`、`docker-compose.yml`：容器化本地运行和部署配置。

## 常用命令

运行构建和测试：

```bash
mvn -q package
```

提供必要配置后运行打包后的服务：

```bash
java -jar target/badminton.jar
```

启动容器化服务。`MYSQL_ROOT_PASSWORD` 和阿里云变量必填，OSS 和短信共用同一组阿里云凭据：

```bash
MYSQL_ROOT_PASSWORD='change-this-locally' \
ALIBABA_CLOUD_ACCESS_KEY_ID='your-local-value' \
ALIBABA_CLOUD_ACCESS_KEY_SECRET='your-local-value' \
docker compose up --build
```

PowerShell 中可以先设置变量，再执行 `docker compose up --build`：

```powershell
$env:MYSQL_ROOT_PASSWORD = 'change-this-locally'
$env:ALIBABA_CLOUD_ACCESS_KEY_ID = 'your-local-value'
$env:ALIBABA_CLOUD_ACCESS_KEY_SECRET = 'your-local-value'
docker compose up --build
```

生产环境不要把凭据放在项目目录中。将变量写入 `/etc/badminton/badminton.env`，设置为 `root:root` 和 `600` 权限，然后使用：

```bash
docker compose --env-file /etc/badminton/badminton.env up -d
```

凭据轮换时更新该文件并执行 `docker compose up -d --force-recreate app`。确认应用正常后，再禁用旧凭据。

部署验收依次检查 `docker compose config`、`docker compose ps`、`curl -f http://localhost:8080/` 和应用日志。OSS/SMS 只在需要时做受控的上传或短信测试，不在应用启动时自动调用外部服务。

提交前运行 `git diff --check` 和 `mvn -q test`。测试使用 JUnit 5、Mockito 和 Spring Boot Test；当前测试不连接真实数据库、Redis、OSS 或短信服务。

## 配置说明

`src/main/resources/application.properties` 使用以下环境变量：

- `MYSQL_PASSWORD`：应用连接 MySQL 所需，必须提供。
- `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USER`：MySQL 连接参数，未设置时使用本地开发默认值。
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`：Redis 连接参数，本地开发时 Redis 密码可以为空。
- `app.reservation.daily-open-time`：每天开始接受预约的时间，格式为 `HH:mm`，例如 `14:00`。
- `ALIBABA_CLOUD_ACCESS_KEY_ID`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET`：统一供阿里云 OSS 和短信服务使用，使用相关功能时必须提供。生产值来自 `/etc/badminton/badminton.env`。

`.env.example` 只用于说明变量名。复制为本地 `.env` 后填写开发值；Spring Boot 直接运行时不会自动加载 `.env`，需要由终端或 IDE 注入环境变量。

不要把密码、Access Key、Token、手机号、个人资料或私有 URL 写入源码、SQL 初始数据、日志或文档。请使用环境变量、被 Git 忽略的本地 `.env` 文件或生产环境的 `/etc/badminton/badminton.env`。不要提交 `.env`、`target/`、IDE 配置或 `.claude/` 内容。

## 大学内部场景的功能扩展

本系统面向校内师生，不是商业场馆平台。预约本身免费，不引入支付、退款、余额、优惠券或订单功能。

### 第一优先级

- **校园身份认证**：支持学号/工号、校园统一身份认证或管理员导入；用户角色至少区分学生、教师、场馆管理员。
- **校内预约规则**：按用户角色设置每日次数、提前预约天数、单次时长、同时持有的预约数量和爽约限制。
- **场地状态管理**：支持开放、锁定、维护、考试/活动占用，并允许管理员设置临时关闭时间段。
- **候补队列**：场地满员后允许加入候补；有人取消时按顺序通知下一位。
- **签到与爽约**：继续保留核销码或二维码签到；超时未签到自动记录爽约并限制预约权限。
- **管理审计**：记录管理员调整场地、修改用户状态、取消预约和处理候补的操作日志。

### 第二优先级

- **同行人管理**：预约人可以填写同行人或邀请校内用户，避免多人重复占用同一时段。
- **校园日历与节假日**：展示校历、节假日和场馆特殊开放时间，预约规则按日期生效。
- **通知中心**：在预约成功、取消、候补转正、即将开始和爽约时提供站内通知；短信仅作为可选补充。
- **预约记录与统计**：用户查看历史记录，管理员按日期、场地、用户角色统计使用率和爽约率。
- **日常活动报名**：将羽毛球活动和自由预约区分开，支持活动人数上限、报名截止时间和名单管理。

### 后续候选功能

- **二维码场地签到**：在场地张贴二维码，签到时校验用户、预约和时间窗口。
- **多校区或多场馆**：在现有场地模型上增加校区/场馆层级，但只有出现真实管理需求时再实施。
- **场地公告与设备报修**：管理员发布临时公告，师生提交球网、灯光等设备问题。
- **数据导出**：管理员导出预约和使用率数据，用于场馆排班和年度总结。

### 功能设计原则

1. 先解决校内高频流程，再扩展外围功能；每个功能都应对应明确的用户场景。
2. 预约规则集中在后端配置和服务逻辑中，前端只负责展示和提交请求。
3. 所有用户身份和个人数据遵循最小采集原则；测试和示例数据不得使用真实师生信息。
4. 新功能优先沿用现有 Spring Boot、MyBatis、MySQL、Redis 和静态前端，不为小功能引入微服务或新的基础设施。
5. 涉及预约状态、候补顺序或管理员操作的改动必须补充自动化测试。
6. 数据库结构变更要同步更新 `schema.sql`，并说明已有部署如何迁移。

## 修改约定

1. 控制器、服务、Mapper、实体和前端修改应保持在现有分层中。
2. 修改 Mapper 方法时，同时更新对应 XML statement，并检查参数名称是否一致。
3. 保持现有 API 响应格式和异常处理约定。
4. 预约规则应放在服务层或配置中，不要在控制器和 JavaScript 中重复实现。
5. `schema.sql` 是可部署输入，不要重新加入真实用户记录或硬编码凭据。
6. 暂存前检查 `git diff`。除非任务明确要求，否则不要强制推送或重写历史。
7. 在必要处添加中文注释

## 安全注意事项

本仓库是公开仓库，配置默认值必须可以安全公开，真实凭据必须在仓库外轮换和保存。如果凭据或个人数据被提交，必须从可达历史中移除并立即轮换；只在后续提交中删除是不够的。
