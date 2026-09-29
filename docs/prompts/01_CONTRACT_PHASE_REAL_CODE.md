# 第一阶段派工提示词：智能合约真实代码

最后更新：2026-07-07

将下面整段提示词交给执行代码生成的 AI。本阶段只生成合约和部署交接材料，不继续扩展后端业务代码。

```text
你是一名资深 Solidity/FISCO BCOS 智能合约工程师。请在 D:\xinnongchanpin 工作区内执行第一阶段：生成农产品可信溯源系统的真实智能合约代码和部署交接材料。

重要背景：

- 当前后端已经由另一个 AI 按上一版提示词生成到 backend/，并且基于 FISCO BCOS 官方 spring-boot-crud 示例做了初步改造。
- 现在总控顺序调整为“智能合约优先”。不要继续扩展后端业务逻辑。
- 后端下一步必须根据你生成的合约 ABI、事件、Java wrapper、合约地址和部署交易信息来适配 ChainService。
- 用户会手动把合约部署到本地 FISCO BCOS 3.11 节点；你只需要生成合约源码、接口说明、部署清单和回填清单。

开始前必须读取并遵守：

1. docs/PROJECT_GOAL.md
2. docs/PREPARATION.md
3. docs/prompts/00_ORCHESTRATOR_RULES.md
4. .env.example
5. .gitignore

一、阶段目标

创建或更新以下文件：

```text
contract/
  AgriTrace.sol
  README.md
  DEPLOYMENT_CHECKLIST.md
  BACKEND_INTEGRATION_CONTRACT.md
```

如 `contract/` 不存在，请创建。不要删除 backend/、database/ 或 docs/ 中已有内容。

本阶段不要部署合约，不要生成真实私钥，不要复制证书，不要写入 FISCO 节点密码。

二、合约设计原则

- 合约名称：`AgriTrace`。
- 目标链：FISCO BCOS 3.11。
- Solidity 版本：优先使用 FISCO BCOS 3.x 控制台可编译的 Solidity 0.8.x 写法；不要引入外部库。
- 链上只存摘要和索引，不存图片、质检报告全文、传感器原始连续数据、个人隐私、数据库明文主键。
- 所有业务标识优先使用 `bytes32` hash：
  - `batchIdHash`
  - `productIdHash`
  - `itemCodeHash`
  - `nfcUidHash`
  - `eventIdHash`
  - `windowIdHash`
  - `deviceCodeHash`
  - `payloadHash`
- 合约要有 owner 和 writer 权限：
  - owner 可授权/取消授权 writer。
  - writer 可写入批次、单品、事件、传感器窗口、NFC 绑定。
  - 查询函数公开。
- 合约必须防重复：
  - 同一 `batchIdHash` 不能重复注册。
  - 同一 `itemCodeHash` 不能重复绑定。
  - 同一有效 `nfcUidHash` 不能绑定多个单品。
  - 同一 `eventIdHash` 不能重复写入。
  - 同一 `windowIdHash` 不能重复写入。
- 合约必须发出事件，供后端和链上浏览器追踪。

三、状态和值约定

不要依赖中文字符串上链。使用 `uint8` 状态码和事件类型码，并在 README 中写清楚枚举含义。

建议状态：

```text
BatchStatus:
1 ACTIVE
2 CLOSED
3 RECALL

ItemStatus:
1 CREATED
2 ACTIVE
3 SOLD
4 RECALL
5 VOID

ChainEventType:
1 SOWING
2 FARMING
3 HARVEST
4 PROCESSING
5 QUALITY_TEST
6 WAREHOUSE_IN
7 WAREHOUSE_OUT
8 LOGISTICS
9 STORE_ARRIVAL
10 SALE
11 RECALL

NfcStatus:
1 ACTIVE
2 REPLACED
3 DISABLED
```

四、必须实现的写入函数

函数名和核心参数要稳定，便于后端生成 Java wrapper 后直接调用。可以增加必要字段，但不要删掉以下能力。

```solidity
function setWriter(address writer, bool allowed) external;

function registerBatch(
    bytes32 batchIdHash,
    bytes32 productIdHash,
    bytes32 payloadHash,
    uint256 startedAt
) external;

function closeBatch(
    bytes32 batchIdHash,
    uint8 status,
    bytes32 reasonHash
) external;

function bindItem(
    bytes32 itemCodeHash,
    bytes32 batchIdHash,
    bytes32 payloadHash
) external;

function bindNfcTag(
    bytes32 itemCodeHash,
    bytes32 nfcUidHash,
    bytes32 payloadHash
) external;

function updateNfcStatus(
    bytes32 nfcUidHash,
    uint8 status,
    bytes32 reasonHash
) external;

function appendTraceEvent(
    bytes32 itemCodeHash,
    bytes32 eventIdHash,
    uint8 eventType,
    bytes32 payloadHash,
    uint256 occurredAt
) external;

function appendSensorWindow(
    bytes32 batchIdHash,
    bytes32 windowIdHash,
    bytes32 deviceCodeHash,
    bytes32 payloadHash,
    uint256 windowStart,
    uint256 windowEnd
) external;

function updateItemStatus(
    bytes32 itemCodeHash,
    uint8 status,
    bytes32 reasonHash
) external;
```

五、必须实现的查询函数

查询函数要方便后端校验和公众查询聚合。优先返回固定字段，避免返回超大数组。

```solidity
function isWriter(address account) external view returns (bool);

function batchExists(bytes32 batchIdHash) external view returns (bool);
function itemExists(bytes32 itemCodeHash) external view returns (bool);
function nfcExists(bytes32 nfcUidHash) external view returns (bool);

function getBatch(bytes32 batchIdHash) external view returns (...);
function getItem(bytes32 itemCodeHash) external view returns (...);
function getNfcTag(bytes32 nfcUidHash) external view returns (...);

function getTraceEventCount(bytes32 itemCodeHash) external view returns (uint256);
function getTraceEvent(bytes32 itemCodeHash, uint256 index) external view returns (...);

function getSensorWindowCount(bytes32 batchIdHash) external view returns (uint256);
function getSensorWindow(bytes32 batchIdHash, uint256 index) external view returns (...);
```

你可以决定 `returns (...)` 的具体返回字段，但必须在 `BACKEND_INTEGRATION_CONTRACT.md` 中完整列出，后端会严格按这个文件适配。

六、必须发出的链上事件

至少包含：

```solidity
event WriterUpdated(address indexed writer, bool allowed, address indexed operator);
event BatchRegistered(bytes32 indexed batchIdHash, bytes32 indexed productIdHash, bytes32 payloadHash, uint256 startedAt, address indexed operator);
event BatchClosed(bytes32 indexed batchIdHash, uint8 status, bytes32 reasonHash, address indexed operator);
event ItemBound(bytes32 indexed itemCodeHash, bytes32 indexed batchIdHash, bytes32 payloadHash, address indexed operator);
event NfcTagBound(bytes32 indexed itemCodeHash, bytes32 indexed nfcUidHash, bytes32 payloadHash, address operator);
event NfcStatusUpdated(bytes32 indexed nfcUidHash, uint8 status, bytes32 reasonHash, address indexed operator);
event TraceEventAppended(bytes32 indexed itemCodeHash, bytes32 indexed eventIdHash, uint8 eventType, bytes32 payloadHash, uint256 occurredAt, address operator);
event SensorWindowAppended(bytes32 indexed batchIdHash, bytes32 indexed windowIdHash, bytes32 indexed deviceCodeHash, bytes32 payloadHash, uint256 windowStart, uint256 windowEnd, address operator);
event ItemStatusUpdated(bytes32 indexed itemCodeHash, uint8 status, bytes32 reasonHash, address indexed operator);
```

七、README 要写清楚

`contract/README.md` 至少包含：

- 合约职责。
- 不上链的数据类型。
- hash 字段含义。
- 状态码和事件类型码。
- 写入函数列表。
- 查询函数列表。
- 链上事件列表。
- 与后端、数据库的关系。
- 安全约束：owner/writer、重复写入限制、NFC 唯一绑定。

八、部署清单

`contract/DEPLOYMENT_CHECKLIST.md` 至少包含：

- 用户手动部署前需要确认的内容：
  - FISCO 节点已运行。
  - 控制台或部署工具可用。
  - SDK 证书可用。
  - `group0` 或实际 groupId。
  - 部署账户。
- 部署后必须回填到仓库的信息：
  - 合约地址。
  - 部署交易 hash。
  - 部署区块高度。
  - ABI 文件路径。
  - bin 文件路径。
  - Java wrapper 文件路径。
  - 部署账户地址。
- 建议回填文件位置：
  - `contract/artifacts/AgriTrace.abi`
  - `contract/artifacts/AgriTrace.bin`
  - `backend/src/main/java/com/xinnong/trace/chain/contract/AgriTrace.java`
  - 本地 `.env` 的 `FISCO_CONTRACT_ADDRESS`
  - `docs/FISCO_DEPLOYMENT_RECORD.md`

九、后端适配契约文档

`contract/BACKEND_INTEGRATION_CONTRACT.md` 必须写给后端工程师看，至少包含：

- 每个合约函数对应的后端 ChainService 方法。
- 每个参数的来源：
  - 数据库字段。
  - canonical JSON hash。
  - 单品码 hash。
  - NFC UID hash。
  - 传感器窗口 hash。
- 每个返回值的 Java 类型预期。
- 每个事件后端需要落库到 `chain_transaction` 的字段。
- 后端状态转换建议：
  - PENDING
  - CONFIRMED
  - FAILED
  - CHAIN_DISABLED
- 部署产物回填后，后端需要修改的配置项：
  - `FISCO_CONTRACT_ADDRESS`
  - `FISCO_GROUP_ID`
  - `FISCO_PEERS`
  - `FISCO_SDK_CERT_PATH`

十、不要做的事

本阶段不要：

- 修改 backend/ 业务代码。
- 修改 database/schema.sql，除非只是在文档中指出字段对齐建议。
- 创建前端代码。
- 部署合约。
- 写入真实私钥、真实证书、真实密码。
- 把原始传感器连续数据写上链。

十一、质量要求

- Solidity 代码要有清晰注释，但不要写冗长废话。
- 使用 `require` 给出可读错误信息。
- 结构体字段要稳定，避免后续轻易破坏 ABI。
- 事件参数要有足够 indexed 字段，便于按 batch、item、event、device 查询。
- 如果 FISCO 控制台或编译器不可用，不要伪造 ABI/bin；只生成源码和部署说明，并在最终回复说明未编译。

十二、最终回复格式

完成后请说明：

- 创建/修改的文件。
- 合约包含的核心能力。
- 没有执行部署或编译的原因。
- 用户手动部署后需要带回哪些文件和信息。
- 下一阶段后端适配应从哪里开始。
```
