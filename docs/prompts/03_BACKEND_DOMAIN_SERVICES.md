# 第三阶段派工提示词：后端业务服务与链状态闭环

最后更新：2026-07-07

将下面整段提示词交给执行代码生成的 AI。本阶段在第二阶段 FISCO 合约适配成果之上，开始实现真实后端业务服务、数据库持久化、链交易状态回填和公众查询聚合。

```text
你是一名资深 Java 后端工程师和农业溯源业务架构师。请在 D:\xinnongchanpin 工作区内执行第三阶段：后端业务服务与链状态闭环。

重要背景：

- 智能合约 `AgriTrace` 已生成并部署。
- 第二阶段已完成后端 FISCO BCOS 合约适配。
- 当前后端基于 FISCO BCOS 官方 spring-boot-crud 示例改造，包名主要是 `org.nongchanpin`。
- 当前已有链适配能力：
  - `org.nongchanpin.chain.ChainService`
  - `org.nongchanpin.chain.AgriTraceChainService`
  - `org.nongchanpin.chain.ChainParameterBuilder`
  - `org.nongchanpin.controller.ChainController`
  - hash/bytes32/time 工具
  - `AgriTrace.java` wrapper
- 当前已有数据库脚本：`database/schema.sql`。
- 当前 Maven wrapper 不完整：`backend/.mvn/wrapper/maven-wrapper.jar` 缺失，`mvnw.cmd test` 不能运行；本机也可能没有全局 `mvn`。

开始前必须读取并遵守：

1. docs/PROJECT_GOAL.md
2. docs/PREPARATION.md
3. docs/prompts/00_ORCHESTRATOR_RULES.md
4. docs/prompts/02_BACKEND_CONTRACT_ADAPTATION.md
5. contract/BACKEND_INTEGRATION_CONTRACT.md
6. contract/FISCO_DEPLOYMENT_RECORD.md
7. backend/README.md
8. backend/pom.xml
9. database/schema.sql
10. backend/src/main/java/org/nongchanpin/chain/ChainService.java
11. backend/src/main/java/org/nongchanpin/controller/ChainController.java

一、阶段目标

本阶段不是前端，不是 AI，不是 IoT 完整接入。目标是把后端从“链 smoke API”推进到“真实业务服务”：

- 恢复或明确后端可测试构建路径。
- 使用现有 MySQL schema 实现核心业务持久化。
- 实现产品、批次、单品码、NFC、溯源事件、chain_transaction 的领域服务。
- 业务写入时生成 canonical payload、hash，并调用现有 ChainService 上链。
- 上链前创建 `chain_transaction=PENDING`，上链后回填 `CONFIRMED/FAILED/CHAIN_DISABLED`。
- 公众查询接口聚合链下业务明细和链上校验状态。
- 保留第二阶段已完成的合约适配代码，不要重写合约和 wrapper。

二、构建工具优先处理

先处理可测试性，但不要卡死开发：

- 检查 `backend/.mvn/wrapper/` 是否存在。
- 如果能从官方示例或本地资源恢复 Maven Wrapper，请补齐 wrapper 配置和 jar。
- 如果无法恢复 wrapper，检查全局 `mvn` 是否可用。
- 如果仍无法运行 Maven，继续生成代码和测试，并在最终回复明确说明构建未运行原因。
- 不要为了构建问题删除现有后端代码。

三、包结构建议

在现有 `org.nongchanpin` 下增量实现：

```text
org.nongchanpin
  domain/
    product/
    batch/
    item/
    nfc/
    trace/
    chainlog/
    publicquery/
  mapper/
  model/
    entity/
    dto/
    vo/
    command/
  service/
  controller/
  util/
```

如果当前项目已经有相近目录，可沿用现有风格。不要从零重建后端。

四、数据库与 Mapper

使用现有 `database/schema.sql` 中的表。优先实现以下表的实体和 Mapper：

- `product`
- `product_batch`
- `trace_item`
- `nfc_tag`
- `trace_event`
- `chain_transaction`
- `quality_report` 可先做只读/占位
- `iot_data_window` 可先用于公众查询环境摘要，不做完整 MQTT 接入

要求：

- 使用当前项目已有 MyBatis 风格。
- Mapper XML 或注解二选一，保持一致。
- 不要引入一套与现有 pom 不兼容的大型 ORM。
- 如 schema 字段不足，可小幅补充 `database/schema.sql`，并说明原因。

五、核心业务接口

新增或整理这些业务 API。它们不同于 `/api/chain/**` smoke API。

产品：

- `POST /api/products`
- `GET /api/products`
- `GET /api/products/{id}`

批次：

- `POST /api/batches`
- `GET /api/batches`
- `GET /api/batches/{id}`
- `POST /api/batches/{id}/close`

单品码：

- `POST /api/items/generate`
- `GET /api/items`
- `GET /api/items/{id}`

NFC：

- `POST /api/nfc/bind`
- `POST /api/nfc/{id}/disable`

溯源事件：

- `POST /api/items/{itemId}/events`
- `GET /api/items/{itemId}/events`

公众查询：

- `GET /api/public/trace/{traceCode}`

六、业务上链流程

每个会写链的业务动作必须遵守：

1. 校验业务参数。
2. 写入或更新业务表。
3. 构造 canonical payload。
4. 计算 payload hash 和业务 ID hash。
5. 创建 `chain_transaction`，状态为 `PENDING`。
6. 调用 `ChainService`。
7. 根据返回结果更新 `chain_transaction`。
8. 回写业务表 `chain_status`。
9. 如果链不可用，明确标记 `CHAIN_DISABLED`，不要假装成功。

必须串起来的动作：

- 创建批次 -> `registerBatch`
- 关闭/召回批次 -> `closeBatch`
- 生成单品码 -> `bindItem`
- 绑定 NFC -> `bindNfcTag`
- 禁用/替换 NFC -> `updateNfcStatus`
- 追加溯源事件 -> `appendTraceEvent`
- 关闭或召回单品 -> `updateItemStatus`

传感器窗口 `appendSensorWindow` 可先预留服务方法，完整 IoT 聚合放到下一阶段。

七、公众查询聚合

`GET /api/public/trace/{traceCode}` 必须返回面向前端的聚合 VO，至少包含：

- 产品信息。
- 批次信息。
- 单品状态。
- NFC 绑定状态。
- 溯源事件时间线。
- 质检摘要，如当前有数据。
- 环境摘要，如当前有 `iot_data_window`。
- 链上校验状态：
  - 每条业务记录的链状态。
  - 链上 payloadHash 与链下重算 hash 是否一致。
  - 合约地址。
  - 最近交易 hash。
- AI 是否可用的占位字段，但不实现 AI。

注意：公众查询不要暴露数据库自增 ID、原始私钥、证书路径、内部错误堆栈。

八、chain_transaction 持久化

必须实现 `ChainTransactionService` 或等价服务：

- `createPending(...)`
- `markConfirmed(...)`
- `markFailed(...)`
- `markChainDisabled(...)`
- `findLatestByBusiness(...)`

字段至少对齐：

- `business_type`
- `business_id`
- `business_id_hash`
- `chain_status`
- `tx_hash`
- `block_number`
- `receipt_status`
- `contract_address`
- `event_name`
- `event_indexed_keys`
- `payload_hash`
- `chain_operator`
- `request_hash`
- `error_message`
- `submitted_at`
- `confirmed_at`

九、开发期密钥和证书

不要让密钥问题阻塞开发：

- 可以使用当前本地证书和私钥配置跑通真实 FISCO 调用。
- 不要删除 `backend/src/main/resources/conf/*.crt` 或 `*.key`。
- 不要在最终回复中输出真实私钥、证书正文、密码。
- 如果为了本地开发临时写入真实 `system.hexPrivateKey`，不要把值复制到 README 或 docs。

十、测试要求

至少补充或整理：

- 业务 hash/canonical payload 测试。
- `ChainTransactionService` 状态转换测试。
- 批次创建服务测试：业务入库、hash 生成、ChainService 调用、状态回写。
- 单品码生成测试：随机码不重复、hash 稳定、调用 `bindItem`。
- 公众查询聚合测试：能组合产品、批次、单品、事件和链状态。

如果 Maven 仍不可用，要说明未运行原因；如果可以运行，至少执行：

```powershell
cd backend
.\mvnw.cmd test
```

或：

```powershell
cd backend
mvn test
```

十一、不要做的事

本阶段不要：

- 写 Vue 前端。
- 重写 `AgriTrace.sol`。
- 重新生成 Java wrapper，除非当前 wrapper 明显错误且说明原因。
- 做完整 MQTT/EMQX 接入。
- 做 AI provider 真实调用。
- 做公有链真实锚定交易。
- 为了追求架构漂亮而删除第二阶段已跑通的链适配代码。

十二、完成后的交付

最终回复必须说明：

- 新增/修改的关键文件。
- 已完成哪些业务 API。
- 哪些业务动作已经串起 MySQL + ChainService + chain_transaction。
- 是否运行测试/构建，结果如何。
- 仍待补的真实链联调、数据库账号、Maven wrapper 或业务范围。
- 下一阶段建议，通常是 IoT/MQTT 传感器接入和环境窗口摘要。
```
