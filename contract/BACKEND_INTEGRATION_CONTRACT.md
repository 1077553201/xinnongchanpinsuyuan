# AgriTrace 后端适配契约

本文档是后端工程师适配 ChainService 的接口契约。后端必须以部署后的 ABI、Java wrapper 和本文档中的返回字段为准，不要自行猜测 Solidity 返回值顺序。

## 基础约定

- Solidity 合约：`contract/AgriTrace.sol`
- 合约名：`AgriTrace`
- 目标链：FISCO BCOS 3.11
- Java wrapper：部署后使用 FISCO 控制台或 `contract2java.sh` 生成。
- 所有 `bytes32` 入参在 Java 侧建议使用 FISCO Java SDK wrapper 对应的 `byte[]`、`Bytes32` 或生成器给出的类型，以实际生成 wrapper 为准。
- 所有时间字段使用 Unix epoch seconds，Java 侧用 `BigInteger` 承载 `uint256`。
- 所有状态码使用 Java `int` 或 `byte` 生成，再按 wrapper 要求传入 `BigInteger` 或 `byte`，以实际 wrapper 为准。
- canonical JSON 必须字段排序稳定、空值规则稳定、时间格式稳定，再计算 `payloadHash`。

## 写入函数与 ChainService 方法

| 合约函数 | 建议 ChainService 方法 | 参数来源 |
| --- | --- | --- |
| `setWriter(address writer, bool allowed)` | `setWriter(String writerAddress, boolean allowed)` | 管理员输入；调用账户必须是 owner |
| `registerBatch(bytes32 batchIdHash, bytes32 productIdHash, bytes32 payloadHash, uint256 startedAt)` | `registerBatch(Batch batch)` | `batchIdHash` 来自批次业务编号；`productIdHash` 来自产品编号；`payloadHash` 来自批次 canonical JSON；`startedAt` 来自批次开始时间 |
| `closeBatch(bytes32 batchIdHash, uint8 status, bytes32 reasonHash)` | `closeBatch(String batchId, int status, Object reasonPayload)` | `batchIdHash` 来自批次业务编号；`status` 为 `2` 或 `3`；`reasonHash` 来自关闭/召回原因 canonical JSON |
| `bindItem(bytes32 itemCodeHash, bytes32 batchIdHash, bytes32 payloadHash)` | `bindItem(Item item)` | `itemCodeHash` 来自单品码/traceCode；`batchIdHash` 来自批次业务编号；`payloadHash` 来自单品 canonical JSON |
| `bindNfcTag(bytes32 itemCodeHash, bytes32 nfcUidHash, bytes32 payloadHash)` | `bindNfcTag(String itemCode, String nfcUid, Object bindPayload)` | `itemCodeHash` 来自单品码；`nfcUidHash` 来自 NFC UID；`payloadHash` 来自绑定记录 canonical JSON |
| `updateNfcStatus(bytes32 nfcUidHash, uint8 status, bytes32 reasonHash)` | `updateNfcStatus(String nfcUid, int status, Object reasonPayload)` | `nfcUidHash` 来自 NFC UID；`status` 为 `1/2/3`；`reasonHash` 来自原因 canonical JSON |
| `appendTraceEvent(bytes32 itemCodeHash, bytes32 eventIdHash, uint8 eventType, bytes32 payloadHash, uint256 occurredAt)` | `appendTraceEvent(TraceEvent event)` | `itemCodeHash` 来自单品码；`eventIdHash` 来自事件业务 ID；`eventType` 为 `1..11`；`payloadHash` 来自事件 canonical JSON；`occurredAt` 来自业务发生时间 |
| `appendSensorWindow(bytes32 batchIdHash, bytes32 windowIdHash, bytes32 deviceCodeHash, bytes32 payloadHash, uint256 windowStart, uint256 windowEnd)` | `appendSensorWindow(SensorWindow window)` | `batchIdHash` 来自批次编号；`windowIdHash` 来自传感器窗口 ID；`deviceCodeHash` 来自设备编码；`payloadHash` 来自窗口聚合 canonical JSON；`windowStart/windowEnd` 来自窗口边界 |
| `updateItemStatus(bytes32 itemCodeHash, uint8 status, bytes32 reasonHash)` | `updateItemStatus(String itemCode, int status, Object reasonPayload)` | `itemCodeHash` 来自单品码；`status` 为 `1..5`；`reasonHash` 来自原因 canonical JSON |

每个写入方法应先创建一条 `chain_transaction` 记录，状态为 `PENDING`。拿到回执后根据链上执行结果更新为 `CONFIRMED` 或 `FAILED`。

## 查询函数与 Java 返回值预期

实际 Java 类型以生成的 wrapper 为准。以下为语义类型和顺序约定：

### `isWriter(address account) returns (bool)`

| 序号 | 字段 | Java 语义类型 |
| --- | --- | --- |
| 1 | writer allowed | `Boolean` |

### `batchExists(bytes32 batchIdHash) returns (bool)`

返回 `Boolean`。

### `itemExists(bytes32 itemCodeHash) returns (bool)`

返回 `Boolean`。

### `nfcExists(bytes32 nfcUidHash) returns (bool)`

返回 `Boolean`。

### `getBatch(bytes32 batchIdHash)`

| 序号 | 字段 | Java 语义类型 |
| --- | --- | --- |
| 1 | `exists` | `Boolean` |
| 2 | `productIdHash` | `byte[]` / wrapper bytes32 类型 |
| 3 | `payloadHash` | `byte[]` / wrapper bytes32 类型 |
| 4 | `status` | `BigInteger` 或 `Integer` |
| 5 | `startedAt` | `BigInteger` |
| 6 | `closedAt` | `BigInteger` |
| 7 | `closeReasonHash` | `byte[]` / wrapper bytes32 类型 |
| 8 | `operator` | `String` address |

### `getItem(bytes32 itemCodeHash)`

| 序号 | 字段 | Java 语义类型 |
| --- | --- | --- |
| 1 | `exists` | `Boolean` |
| 2 | `batchIdHash` | `byte[]` / wrapper bytes32 类型 |
| 3 | `payloadHash` | `byte[]` / wrapper bytes32 类型 |
| 4 | `status` | `BigInteger` 或 `Integer` |
| 5 | `nfcUidHash` | `byte[]` / wrapper bytes32 类型 |
| 6 | `createdAt` | `BigInteger` |
| 7 | `updatedAt` | `BigInteger` |
| 8 | `statusReasonHash` | `byte[]` / wrapper bytes32 类型 |
| 9 | `operator` | `String` address |

### `getNfcTag(bytes32 nfcUidHash)`

| 序号 | 字段 | Java 语义类型 |
| --- | --- | --- |
| 1 | `exists` | `Boolean` |
| 2 | `itemCodeHash` | `byte[]` / wrapper bytes32 类型 |
| 3 | `payloadHash` | `byte[]` / wrapper bytes32 类型 |
| 4 | `status` | `BigInteger` 或 `Integer` |
| 5 | `reasonHash` | `byte[]` / wrapper bytes32 类型 |
| 6 | `boundAt` | `BigInteger` |
| 7 | `updatedAt` | `BigInteger` |
| 8 | `operator` | `String` address |

### `getTraceEventCount(bytes32 itemCodeHash) returns (uint256)`

返回 `BigInteger`。

### `getTraceEvent(bytes32 itemCodeHash, uint256 index)`

| 序号 | 字段 | Java 语义类型 |
| --- | --- | --- |
| 1 | `eventIdHash` | `byte[]` / wrapper bytes32 类型 |
| 2 | `eventType` | `BigInteger` 或 `Integer` |
| 3 | `payloadHash` | `byte[]` / wrapper bytes32 类型 |
| 4 | `occurredAt` | `BigInteger` |
| 5 | `recordedAt` | `BigInteger` |
| 6 | `operator` | `String` address |

### `getSensorWindowCount(bytes32 batchIdHash) returns (uint256)`

返回 `BigInteger`。

### `getSensorWindow(bytes32 batchIdHash, uint256 index)`

| 序号 | 字段 | Java 语义类型 |
| --- | --- | --- |
| 1 | `windowIdHash` | `byte[]` / wrapper bytes32 类型 |
| 2 | `deviceCodeHash` | `byte[]` / wrapper bytes32 类型 |
| 3 | `payloadHash` | `byte[]` / wrapper bytes32 类型 |
| 4 | `windowStart` | `BigInteger` |
| 5 | `windowEnd` | `BigInteger` |
| 6 | `recordedAt` | `BigInteger` |
| 7 | `operator` | `String` address |

## 事件落库到 chain_transaction

后端监听或解析交易回执中的事件后，建议至少落以下字段：

| 字段 | 来源 |
| --- | --- |
| `business_type` | 调用的 ChainService 方法或事件名 |
| `business_id_hash` | 事件中的主索引，如 `batchIdHash`、`itemCodeHash`、`nfcUidHash`、`windowIdHash` |
| `tx_hash` | 交易回执 |
| `block_number` | 交易回执 |
| `receipt_status` | 交易回执状态 |
| `contract_address` | 当前 `FISCO_CONTRACT_ADDRESS` |
| `event_name` | Solidity 事件名 |
| `event_indexed_keys` | indexed 参数 JSON |
| `payload_hash` | 事件中的 `payloadHash` 或 `reasonHash` |
| `chain_operator` | 事件中的 `operator` |
| `submitted_at` | 后端提交交易时间 |
| `confirmed_at` | 回执确认时间 |
| `error_message` | 链上 revert 原因或 SDK 异常 |

## 事件与主索引

| 事件 | 主索引 | 需要保存的关键字段 |
| --- | --- | --- |
| `WriterUpdated` | `writer` | `writer`、`allowed`、`operator` |
| `BatchRegistered` | `batchIdHash` | `batchIdHash`、`productIdHash`、`payloadHash`、`startedAt`、`operator` |
| `BatchClosed` | `batchIdHash` | `batchIdHash`、`status`、`reasonHash`、`operator` |
| `ItemBound` | `itemCodeHash` | `itemCodeHash`、`batchIdHash`、`payloadHash`、`operator` |
| `NfcTagBound` | `nfcUidHash` | `itemCodeHash`、`nfcUidHash`、`payloadHash`、`operator` |
| `NfcStatusUpdated` | `nfcUidHash` | `nfcUidHash`、`status`、`reasonHash`、`operator` |
| `TraceEventAppended` | `eventIdHash` | `itemCodeHash`、`eventIdHash`、`eventType`、`payloadHash`、`occurredAt`、`operator` |
| `SensorWindowAppended` | `windowIdHash` | `batchIdHash`、`windowIdHash`、`deviceCodeHash`、`payloadHash`、`windowStart`、`windowEnd`、`operator` |
| `ItemStatusUpdated` | `itemCodeHash` | `itemCodeHash`、`status`、`reasonHash`、`operator` |

## 后端状态转换建议

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 后端已提交或准备提交链交易，尚未拿到成功回执 |
| `CONFIRMED` | 交易回执成功，事件解析完成 |
| `FAILED` | SDK 调用异常、交易回执失败或合约 revert |
| `CHAIN_DISABLED` | 本地未配置链、合约地址缺失或开发环境主动关闭链写入 |

建议流程：

1. 业务数据先入库，生成 canonical JSON 和 hash。
2. 创建 `chain_transaction`，状态为 `PENDING`。
3. 调用 Java wrapper 写链。
4. 根据回执更新 `tx_hash`、`block_number`、`receipt_status`。
5. 解析事件并保存 indexed 字段和 `payloadHash`。
6. 成功更新为 `CONFIRMED`，失败更新为 `FAILED`。
7. 如果链配置缺失，业务可按产品策略进入 `CHAIN_DISABLED`，但必须标识该数据未上链。

## 部署产物回填后的后端配置项

后端开始适配前必须确认：

```dotenv
FISCO_CONTRACT_ADDRESS=0x...
FISCO_GROUP_ID=group0
FISCO_PEERS=127.0.0.1:20200
FISCO_SDK_CERT_PATH=replace_with_local_untracked_path
```

同时确认：

- `contract/artifacts/AgriTrace.abi` 存在。
- `contract/artifacts/AgriTrace.bin` 存在。
- Java wrapper 已放入后端真实包路径。
- 部署账户地址已记录，该地址是合约 `owner`。
- ChainService 使用的交易账户已由 owner 授权为 writer，或直接使用 owner 账户。

## 数据库字段对齐建议

本阶段不修改数据库 schema。后端适配时建议确认业务表或交易表能保存：

- 原始业务 ID 和对应 hash。
- canonical JSON 或其可重建来源。
- `payloadHash`、`reasonHash`。
- `txHash`、`blockNumber`、`receiptStatus`。
- `contractAddress`、`chainOperator`、`eventName`。
- `PENDING/CONFIRMED/FAILED/CHAIN_DISABLED` 状态。
