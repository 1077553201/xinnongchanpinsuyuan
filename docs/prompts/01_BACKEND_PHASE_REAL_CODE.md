# 第一阶段派工提示词：后端真实工程基础

最后更新：2026-07-07

> 暂停直接使用：当前顺序已调整为“智能合约优先”。请先使用 `docs/prompts/01_CONTRACT_PHASE_REAL_CODE.md` 生成并部署合约；等 ABI、bin、Java wrapper、合约地址和部署交易信息回填后，再基于本文件改写成后端适配阶段提示词。

将下面整段提示词交给执行代码生成的 AI。不要再使用“最小运行模式”作为目标，本阶段要产出真实后端工程基础。

```text
你是一名资深 Java 后端工程师、FISCO BCOS 应用工程师和物联网后端架构师。请在 D:\xinnongchanpin 工作区内执行第一阶段：基于 FISCO BCOS 官方 Maven SpringBoot 示例改造后端真实工程基础。

重要：本阶段不是 demo，不是最小 mock，不是临时骨架，也不要从零手写 Spring Boot 模板。你必须先使用 FISCO BCOS 官方 Maven SpringBoot 示例 `spring-boot-crud` 作为后端工程基础，再在它的 Maven Wrapper、SDK 配置、Spring Boot 启动结构和链连接示例上改造出真实可维护的业务后端。外部系统未配置时可以提供明确的 disabled/profile 降级，但主代码结构必须按真实系统设计。

开始前必须读取并遵守：

1. docs/PROJECT_GOAL.md
2. docs/PREPARATION.md
3. docs/prompts/00_ORCHESTRATOR_RULES.md
4. .env.example
5. .gitignore

官方后端模板来源：

- 官方文档：https://fisco-bcos-doc.readthedocs.io/zh-cn/latest/docs/sdk/java_sdk/spring_boot_crud.html
- GitHub：https://github.com/FISCO-BCOS/spring-boot-crud.git
- Gitee 备用：https://gitee.com/FISCO-BCOS/spring-boot-crud

一、阶段目标

完成后端第一阶段的真实工程代码：

- 如果 backend/ 不存在，先克隆 FISCO 官方示例到 backend/：`git clone https://github.com/FISCO-BCOS/spring-boot-crud.git backend`。如果 GitHub 网络不可用，再使用 Gitee 备用地址。
- 如果 backend/ 已存在，先检查是否已经基于 `spring-boot-crud`，再在现有基础上改造；不要删除已有成果。
- 保留并利用官方示例的 Maven Wrapper、FISCO SDK 配置方式、证书目录约定和链调用示例。
- 在官方示例基础上重构为农产品溯源业务后端。
- 创建 database/schema.sql 和必要的 seed.sql。
- 建立 MySQL 8 数据模型。
- 建立清晰的领域模块、Controller、Service、Repository/Mapper、DTO、VO、异常处理、统一响应。
- 提供核心 REST API，真实落库，不以纯内存 mock 作为主实现。
- 为 FISCO、MQTT、NFC、AI、公有链锚定建立真实适配边界。
- 未提供 FISCO 证书、MySQL 密码、MQTT broker、AI key 时，系统通过 profile/配置明确提示 disabled，而不是静默伪造成功。

二、技术选择

- Java：11。
- Spring Boot：优先沿用官方示例的版本；如确需升级，必须兼容 Java 11 并说明原因。
- 构建：Maven，优先使用官方示例自带 Maven Wrapper，而不是依赖全局 `mvn`。
- 数据库：MySQL 8，字符集 utf8mb4。
- ORM/数据访问：MyBatis 或 MyBatis-Plus，选择一种并保持一致。
- JSON：Jackson。
- 参数校验：spring-boot-starter-validation。
- 安全：预留 JWT 登录鉴权和 RBAC，第一阶段至少完成用户、角色、权限结构和登录接口边界。
- MQTT：预留 Eclipse Paho 或 Spring Integration MQTT 适配边界，第一阶段可先实现 mock telemetry HTTP 接口和 MQTT 配置类，不要求真实 broker 已运行。
- FISCO：基于官方示例已有 Java SDK 配置和链调用方式建立 ChainService 端口和 FiscoChainService 适配层；真实证书路径未确认时，保留可编译的 disabled 实现，并在 README 标明如何接入真实 FISCO SDK。不要把链上能力写成永远成功的假实现。
- AI：建立 AiProvider 接口和 disabled provider；不调用真实模型，不写死 API key。

三、后端包结构

包名处理：

```text
com.xinnong.trace
```

如果官方示例原包名不同，应在改造时统一迁移到 `com.xinnong.trace`，同时保证启动类、配置扫描、Mapper 扫描和测试路径全部一致。

推荐结构：

```text
backend/
  pom.xml
  mvnw
  mvnw.cmd
  src/main/java/com/xinnong/trace/
    TraceApplication.java
    common/
    config/
    security/
    organization/
    user/
    product/
    batch/
    item/
    traceevent/
    iot/
    nfc/
    chain/
    ai/
    anchor/
    publicquery/
  src/main/resources/
    application.yml
    application-local.yml
    mapper/
  src/test/java/com/xinnong/trace/
```

官方示例中的 `Person`、`KV set/get` 等演示接口不得作为最终业务接口保留在主 API 中。可以将其改造成 `/api/chain/health` 或链路 smoke test 的内部实现，也可以删除，但必须保留真实 FISCO 连接配置和调用经验。

四、数据库要求

创建 `database/schema.sql`，至少包含以下表，字段要能支持真实业务，不要只有 id/name：

- sys_organization：农场、企业、监管机构等组织。
- sys_user：后台用户。
- sys_role：角色。
- sys_user_role：用户角色关系。
- product：农产品主数据。
- product_batch：生产批次。
- trace_item：单品码，保存 traceCode 哈希、批次关系、二维码/NFC 状态。
- nfc_tag：NFC 标签 UID/标签哈希、绑定状态、绑定单品。
- trace_event：农事、采收、加工、质检、仓储、物流、销售、召回等业务事件。
- quality_report：质检报告元数据。
- iot_gateway：边缘网关。
- iot_device：传感器设备。
- iot_sensor_reading：传感器原始读数。
- iot_data_window：传感器时间窗口摘要。
- chain_transaction：链上交易记录。
- ai_session：AI 会话。
- ai_message：AI 消息。
- anchor_record：后续公有链锚定记录。

统一要求：

- 所有表使用 InnoDB 和 utf8mb4。
- 所有表包含 id、create_time、update_time。
- 关键业务表包含 status。
- 为 trace_code_hash、batch_code、product_code、nfc_uid_hash、tx_hash、device_code 等关键字段建立唯一索引或普通索引。
- 不保存明文 traceCode 到链上字段；数据库可按业务需要保存加密值或展示码，但必须有 hash 字段。
- `trace_event` 和 `iot_data_window` 必须保存 canonical_payload_hash 或 data_hash，用于链上摘要校验。

五、核心接口要求

第一阶段必须实现以下 API 的真实后端代码。未接 FISCO 时，链上相关字段可以进入 `PENDING` 或 `CHAIN_DISABLED` 状态，但不能假装已经上链成功。

基础：

- GET /api/health
- GET /api/chain/health

组织与用户：

- POST /api/auth/login
- GET /api/users/me

产品与批次：

- POST /api/products
- GET /api/products
- POST /api/batches
- GET /api/batches

单品码：

- POST /api/items/generate
- GET /api/items
- GET /api/items/{id}

溯源事件：

- POST /api/items/{itemId}/events
- GET /api/items/{itemId}/events

IoT：

- POST /api/iot/telemetry/mock
- GET /api/iot/devices
- GET /api/iot/readings

NFC：

- POST /api/nfc/bind
- GET /api/nfc/tags/{uidHash}

公众查询：

- GET /api/public/trace/{traceCode}

AI：

- POST /api/ai/farm/chat
- POST /api/ai/public/chat

六、关键业务实现要求

1. 统一响应
   - 所有接口返回统一结构，例如 code、message、data、traceId、timestamp。
   - 全局异常处理覆盖参数校验、业务异常、数据不存在、外部服务未配置。

2. 单品码生成
   - traceCode 必须使用高熵随机值，不允许自增或容易猜测。
   - 保存 traceCodeHash。
   - 生成时绑定 batchId。
   - 返回给前端用于二维码打印的数据包含 traceCode 或 traceUrl，但不要写入链上明文字段。

3. 稳定哈希
   - 实现 CanonicalJsonHashService。
   - 对业务事件、传感器窗口摘要生成稳定 JSON 后计算 SHA-256 或 FISCO 推荐哈希。
   - 单元测试覆盖字段顺序不影响哈希结果。

4. 溯源事件
   - 事件类型至少支持：SOWING、FARMING、HARVEST、PROCESSING、QUALITY_TEST、WAREHOUSE_IN、WAREHOUSE_OUT、LOGISTICS、STORE_ARRIVAL、SALE、RECALL。
   - 事件入库时生成 eventHash。
   - 记录 chainStatus：PENDING、CONFIRMED、FAILED、CHAIN_DISABLED。

5. IoT 遥测
   - mock telemetry HTTP 接口必须按真实 MQTT payload 格式接收数据。
   - 字段至少支持 farmId、gatewayCode、deviceCode、metric、value、unit、reportedAt、signature 可选。
   - 入库到 iot_sensor_reading。
   - 预留窗口聚合服务 IotWindowAggregationService。

6. NFC
   - NFC 绑定使用 UID hash 或 tag hash，不保存不必要的明文敏感标识。
   - 同一 NFC 标签不能绑定多个有效单品。
   - 同一单品不能重复绑定多个 active 标签，除非设计为换标流程。

7. 公众查询
   - GET /api/public/trace/{traceCode} 根据 traceCode 计算 hash 并查询单品。
   - 返回产品、批次、事件时间线、环境摘要、质检摘要、NFC 状态、链上校验状态、AI 可用状态。
   - 返回内容必须区分 verifiedFacts 和 aiSuggestionAvailable。

8. ChainService
   - 定义端口方法：health、registerBatch、bindItem、appendTraceEvent、appendSensorWindow、bindNfcTag。
   - disabled 实现必须明确返回 CHAIN_DISABLED，不能返回 confirmed。
   - 为后续 FISCO SDK 接入保留配置属性：groupId、peers、certPath、account/keystore、contractAddress。

9. AI Provider
   - 定义 AiProvider 和 ChatService。
   - disabled 实现返回清晰提示：AI provider not configured。
   - AI 消息入库，区分 FARM_ASSISTANT 和 PUBLIC_ASSISTANT。

七、配置要求

从环境变量或 application-local.yml 读取配置。不要写真实敏感值。需要兼容官方示例原有 `applicationContext.xml` 或等价 Java Config；如果迁移为 Spring Boot Configuration Properties，必须保证 FISCO SDK peers、group、证书路径和账户配置仍清晰可配。

需要至少覆盖：

- DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD
- FISCO_GROUP_ID、FISCO_PEERS、FISCO_SDK_CERT_PATH、FISCO_ACCOUNT_KEYSTORE_PATH、FISCO_ACCOUNT_PASSWORD、FISCO_CONTRACT_ADDRESS
- MQTT_HOST、MQTT_PORT、MQTT_USERNAME、MQTT_PASSWORD
- AI_PROVIDER、AI_BASE_URL、AI_API_KEY、AI_MODEL

八、文档要求

更新或创建：

- README.md：项目定位、后端启动、数据库初始化、配置说明、敏感信息规则。
- backend/README.md：后端模块结构、API 分组、profile、外部依赖说明。
- docs/PREPARATION.md：如果发现新的环境事实或缺口，要追加记录。

九、测试要求

至少创建单元测试：

- CanonicalJsonHashServiceTest：字段顺序稳定性。
- TraceCodeServiceTest：生成的 traceCode 不为空、hash 稳定、批量生成不重复。
- NfcBindingServiceTest：重复绑定规则。
- PublicTraceQueryServiceTest：可从 traceCode 聚合公开查询结果。

如果 Maven 不可用，仍然生成测试代码，并在最终回复说明无法运行原因。不要因为 Maven 不可用而降低代码质量。

十、验收标准

阶段完成后必须满足：

- backend/ 基于 FISCO 官方 `spring-boot-crud` 示例改造，而不是从零新建模板。
- backend/ Maven 工程结构完整，保留 Maven Wrapper。
- database/schema.sql 覆盖核心业务表。
- 核心 REST API 对应 Controller/Service/DTO 已实现。
- 配置不包含真实敏感值。
- ChainService、IoT、NFC、AI、Anchor 的边界清晰。
- 公众查询服务能从单品码聚合产品、批次、事件、NFC、链状态和环境摘要。
- 代码不是纯内存 demo，主路径面向 MySQL 持久化。
- 最终回复列出文件、能力、测试情况和下一阶段所需输入。

十一、边界

本阶段不要做：

- Vue 前端页面。
- FISCO 合约部署。
- 公有链真实交易。
- 真实 AI 模型调用。
- 真实设备控制。
- 把原始连续传感器数据写入链上。
```
