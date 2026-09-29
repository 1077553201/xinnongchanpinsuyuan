# 第二阶段派工提示词：后端按 AgriTrace 合约适配

最后更新：2026-07-07

将下面整段提示词交给执行代码生成的 AI。本阶段目标是在当前 `backend/` 官方示例基础上，按已经生成和部署的 `AgriTrace` 合约做真实后端适配。

```text
你是一名资深 Java 后端工程师和 FISCO BCOS 应用工程师。请在 D:\xinnongchanpin 工作区内执行第二阶段：后端按已部署 AgriTrace 合约适配。

重要背景：

- 合约阶段已经完成，核心文件在 contract/。
- 当前合约地址：0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94。
- CNS：AgriTrace 1.0。
- 部署账户地址：0xd8d242c3b2c751458471573d5934bd0888a29f48。
- 部署交易 hash、部署区块高度、部署时间仍待回填，不要伪造。
- 当前后端是基于 FISCO BCOS 官方 spring-boot-crud 示例改造的工程。
- 当前后端 Java 包主要是 org.nongchanpin，不要从零重建后端，不要删除已有后端成果。
- 当前后端已存在：
  - backend/src/main/java/org/nongchanpin/contracts/AgriTrace.java
  - backend/src/main/resources/abi/AgriTrace.abi
  - backend/src/main/resources/bin/ecc/AgriTrace.bin
  - backend/src/main/resources/conf/ca.crt
  - backend/src/main/resources/conf/sdk.crt
  - backend/src/main/resources/conf/sdk.key
- `backend/src/main/resources/application.properties` 当前通过 `SYSTEM_HEX_PRIVATE_KEY` 环境变量读取私钥。开发阶段可以使用本地真实私钥和证书跑通链调用，但不要把真实值写进面向提交/交付的文档或最终回复。

开始前必须读取并遵守：

1. docs/PROJECT_GOAL.md
2. docs/PREPARATION.md
3. docs/prompts/00_ORCHESTRATOR_RULES.md
4. contract/AgriTrace.sol
5. contract/BACKEND_INTEGRATION_CONTRACT.md
6. contract/FISCO_DEPLOYMENT_RECORD.md
7. backend/pom.xml
8. backend/src/main/resources/application.properties
9. .env.example
10. .gitignore

一、阶段目标

在现有后端基础上完成真实合约适配：

- 保留 FISCO 官方示例的 Maven Wrapper、SDK 配置方式和现有 wrapper 使用方式。
- 整理现有示例式 `AgriTraceService`，形成清晰的链服务边界。
- 所有链写入必须调用真实 `AgriTrace.java` wrapper，不允许写永远成功的假实现。
- 建立 `PENDING / CONFIRMED / FAILED / CHAIN_DISABLED` 状态模型。
- 完成合约 bytes32 hash 转换、uint8/uint256 类型转换、交易回执解析、错误处理。
- 提供后端 API 让后续业务服务能调用链上批次、单品、NFC、事件、传感器窗口能力。
- 更新 README 和配置说明，说明私钥、证书和合约地址的开发期配置方式，重点是不阻塞本地真实链调用。

二、开发期密钥处理原则

不要让密钥问题阻塞真实开发。开发机上允许存在并使用真实 SDK 证书、真实私钥或本地账户配置来跑通 FISCO 调用，但遵守最低限度规则：

- 不要删除或移动现有 `backend/src/main/resources/conf/*.crt`、`*.key`，它们是本地链开发需要的材料。
- 优先使用环境变量、本地 `.env`、本地 ignored 配置或当前工程已有配置方式读取私钥。
- 如果为了本地调试必须临时在配置文件中填真实私钥，可以这样做以保证开发进度；不要在最终回复中输出该值。
- 不要因为检测到真实证书或真实私钥就中断后端适配任务。
- 最终回复不得输出真实私钥正文、证书正文、密码。

三、后端包结构策略

当前后端已有 `org.nongchanpin` 包。此阶段优先在现有包结构内完成适配，不做大规模包名迁移。

建议整理为：

```text
backend/src/main/java/org/nongchanpin/
  Application.java
  config/
  constants/
  contracts/
  controller/
  model/
    bo/
    dto/
    vo/
  service/
  chain/
  util/
```

如果现有文件已经能满足，请增量调整；不要把后端删除后重建。

四、必须实现或整理的服务边界

创建或整理链服务接口：

```java
public interface ChainService {
    ChainHealthResult health();
    ChainTxResult setWriter(String writerAddress, boolean allowed);
    ChainTxResult registerBatch(RegisterBatchCommand command);
    ChainTxResult closeBatch(CloseBatchCommand command);
    ChainTxResult bindItem(BindItemCommand command);
    ChainTxResult bindNfcTag(BindNfcTagCommand command);
    ChainTxResult updateNfcStatus(UpdateNfcStatusCommand command);
    ChainTxResult appendTraceEvent(AppendTraceEventCommand command);
    ChainTxResult appendSensorWindow(AppendSensorWindowCommand command);
    ChainTxResult updateItemStatus(UpdateItemStatusCommand command);
    ChainBatchView getBatch(String batchIdOrHash);
    ChainItemView getItem(String itemCodeOrHash);
    ChainNfcView getNfcTag(String nfcUidOrHash);
}
```

命令对象名称可以按现有代码调整，但能力不能减少。

五、合约函数适配要求

按 `contract/BACKEND_INTEGRATION_CONTRACT.md` 逐一适配：

- `setWriter(address,bool)`
- `registerBatch(bytes32,bytes32,bytes32,uint256)`
- `closeBatch(bytes32,uint8,bytes32)`
- `bindItem(bytes32,bytes32,bytes32)`
- `bindNfcTag(bytes32,bytes32,bytes32)`
- `updateNfcStatus(bytes32,uint8,bytes32)`
- `appendTraceEvent(bytes32,bytes32,uint8,bytes32,uint256)`
- `appendSensorWindow(bytes32,bytes32,bytes32,bytes32,uint256,uint256)`
- `updateItemStatus(bytes32,uint8,bytes32)`
- `isWriter(address)`
- `batchExists(bytes32)`
- `itemExists(bytes32)`
- `nfcExists(bytes32)`
- `getBatch(bytes32)`
- `getItem(bytes32)`
- `getNfcTag(bytes32)`
- `getTraceEventCount(bytes32)`
- `getTraceEvent(bytes32,uint256)`
- `getSensorWindowCount(bytes32)`
- `getSensorWindow(bytes32,uint256)`

注意：Java wrapper 的真实方法名、参数类型、返回类型以 `backend/src/main/java/org/nongchanpin/contracts/AgriTrace.java` 为准，不要猜。

六、hash 和类型转换

必须实现工具类：

- `HashUtils` 或等价工具：字符串、canonical JSON -> `bytes32`。
- `Bytes32Utils` 或等价工具：hex string、byte[]、wrapper 类型之间转换。
- `TimeUtils` 或等价工具：Java 时间 -> Unix epoch seconds。

要求：

- 同一输入必须得到稳定 hash。
- 不要把明文 traceCode、NFC UID、设备编码直接作为链上参数。
- 对合约 `bytes32` 参数必须保证长度正确。
- 状态码和事件类型码必须与 `contract/README.md` 一致。

七、API 要求

在现有后端基础上提供或整理这些接口。接口路径可保持当前风格，但必须清楚、可测试。

链健康：

- `GET /api/chain/health`
- 返回 groupId、peers、contractAddress、chainEnabled、writerStatus、latestBlock 或可用链状态。

合约写入 smoke API：

- `POST /api/chain/writers`
- `POST /api/chain/batches`
- `POST /api/chain/batches/{batchCode}/close`
- `POST /api/chain/items`
- `POST /api/chain/nfc-tags`
- `POST /api/chain/items/{itemCode}/events`
- `POST /api/chain/batches/{batchCode}/sensor-windows`
- `POST /api/chain/items/{itemCode}/status`

合约查询 API：

- `GET /api/chain/batches/{batchCode}`
- `GET /api/chain/items/{itemCode}`
- `GET /api/chain/nfc-tags/{nfcUid}`
- `GET /api/chain/items/{itemCode}/events/{index}`
- `GET /api/chain/batches/{batchCode}/sensor-windows/{index}`

这些 API 是合约适配 smoke API，不等同最终业务 API。后续业务服务会基于它们整合 MySQL 明细。

八、配置要求

从配置读取：

```properties
network.peers[0]=127.0.0.1:20200
cryptoMaterial.certPath=conf
system.groupId=group0
system.hexPrivateKey=${SYSTEM_HEX_PRIVATE_KEY:}
contract.agriTraceAddress=0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94
```

如果执行环境更适合直接在本地配置中填写 `system.hexPrivateKey`，可以这样做以保证开发进度，但不要把真实值复制到 README、docs 或最终回复里。

如果你新增配置项，必须同步更新：

- `.env.example`
- `backend/README.md` 或根 README
- `contract/FISCO_DEPLOYMENT_RECORD.md` 的后端回填说明，如有必要

九、数据库关系

本阶段重点是链适配，不要求完成所有业务数据库 CRUD。

但如果当前项目已有 `database/schema.sql`，请检查 `chain_transaction` 是否能保存：

- business_type
- business_id_hash
- tx_hash
- block_number
- receipt_status
- contract_address
- event_name
- event_indexed_keys
- payload_hash
- chain_operator
- submitted_at
- confirmed_at
- error_message

如缺字段，只更新 schema，不做大规模业务表重构。

十、测试要求

至少补充以下测试或可运行检查：

- hash 工具测试：同一输入 hash 稳定，bytes32 长度正确。
- 状态码映射测试：事件类型和状态码符合合约文档。
- ChainService 参数转换测试：registerBatch、bindItem、appendTraceEvent 至少覆盖参数构造。
- 如果能连接本地链，并且 `SYSTEM_HEX_PRIVATE_KEY` 已配置，可运行 `/api/chain/health` 或服务层 health smoke test。

如果 Maven 需要下载依赖但网络不可用，请说明未运行原因；不要因此降低代码质量。

十一、不要做的事

本阶段不要：

- 写 Vue 前端。
- 重写智能合约。
- 伪造部署交易 hash 或区块高度。
- 因密钥洁癖阻塞真实链调用开发；开发期可以使用本地真实私钥，但不要外泄。
- 把证书正文、私钥正文、密码输出到最终回复。
- 做完整农产品业务 CRUD，除非是链适配必要的 DTO/VO。
- 删除 backend/ 现有官方示例基础。

十二、完成后的交付

最终回复必须包含：

- 修改/新增的关键文件。
- 哪些合约函数已经通过 wrapper 接入。
- 哪些 API 可用于 smoke test。
- 是否运行过测试或启动检查。
- 未能验证的内容和原因。
- 下一阶段应做什么，通常是“业务数据库和后端领域服务对齐合约状态”。
```
