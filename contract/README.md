# AgriTrace 智能合约

`AgriTrace.sol` 是农产品可信溯源系统第一阶段的核心合约，目标链为 FISCO BCOS 3.11。合约只保存可验证摘要、索引、状态码、时间戳和链上操作者，不保存业务全文。

## 合约职责

- 注册农产品批次摘要。
- 绑定单品码摘要到批次。
- 绑定 NFC UID 摘要到单品。
- 追加单品溯源事件摘要。
- 追加批次传感器窗口摘要。
- 更新批次、单品和 NFC 状态。
- 发出可被后端和链上浏览器追踪的事件。

## 不上链的数据

- 图片、视频、附件、质检报告全文。
- 传感器原始连续数据。
- 个人隐私、手机号、地址等敏感信息。
- 数据库明文主键、自增 ID。
- 原始二维码内容、原始 NFC UID、原始设备编码。
- 业务 JSON 明文。

这些内容应存储在 MySQL、对象存储或本地文件系统中，链上只保存 canonical JSON 或业务标识的 `bytes32` 哈希。

## Hash 字段含义

| 字段 | 含义 | 推荐来源 |
| --- | --- | --- |
| `batchIdHash` | 批次业务 ID 的哈希 | 后端批次编号规范化后哈希 |
| `productIdHash` | 产品业务 ID 的哈希 | 后端产品编号规范化后哈希 |
| `itemCodeHash` | 单品追溯码哈希 | 单品码或 traceCode 规范化后哈希 |
| `nfcUidHash` | NFC UID 哈希 | NFC UID 规范化后哈希 |
| `eventIdHash` | 溯源事件唯一 ID 哈希 | 事件业务 ID 规范化后哈希 |
| `windowIdHash` | 传感器窗口唯一 ID 哈希 | 窗口 ID 或窗口元数据规范化后哈希 |
| `deviceCodeHash` | 设备编码哈希 | 设备编码规范化后哈希 |
| `payloadHash` | 业务明细摘要 | canonical JSON 的哈希 |
| `reasonHash` | 状态变更原因摘要 | canonical JSON 或原因码的哈希 |

## 状态码

### BatchStatus

| 值 | 名称 | 含义 |
| --- | --- | --- |
| `1` | `ACTIVE` | 批次有效 |
| `2` | `CLOSED` | 批次正常关闭 |
| `3` | `RECALL` | 批次召回 |

### ItemStatus

| 值 | 名称 | 含义 |
| --- | --- | --- |
| `1` | `CREATED` | 单品已创建 |
| `2` | `ACTIVE` | 单品流转中 |
| `3` | `SOLD` | 单品已售出 |
| `4` | `RECALL` | 单品召回 |
| `5` | `VOID` | 单品作废 |

### ChainEventType

| 值 | 名称 | 含义 |
| --- | --- | --- |
| `1` | `SOWING` | 播种 |
| `2` | `FARMING` | 农事管理 |
| `3` | `HARVEST` | 采收 |
| `4` | `PROCESSING` | 加工 |
| `5` | `QUALITY_TEST` | 质检 |
| `6` | `WAREHOUSE_IN` | 入库 |
| `7` | `WAREHOUSE_OUT` | 出库 |
| `8` | `LOGISTICS` | 物流 |
| `9` | `STORE_ARRIVAL` | 到店 |
| `10` | `SALE` | 销售 |
| `11` | `RECALL` | 召回 |

### NfcStatus

| 值 | 名称 | 含义 |
| --- | --- | --- |
| `1` | `ACTIVE` | 有效 |
| `2` | `REPLACED` | 已替换 |
| `3` | `DISABLED` | 已停用 |

## 写入函数

| 函数 | 说明 |
| --- | --- |
| `setWriter(address writer, bool allowed)` | owner 授权或取消授权 writer |
| `registerBatch(bytes32 batchIdHash, bytes32 productIdHash, bytes32 payloadHash, uint256 startedAt)` | 注册批次摘要，默认状态为 `ACTIVE` |
| `closeBatch(bytes32 batchIdHash, uint8 status, bytes32 reasonHash)` | 关闭或召回批次，`status` 只能是 `CLOSED` 或 `RECALL` |
| `bindItem(bytes32 itemCodeHash, bytes32 batchIdHash, bytes32 payloadHash)` | 绑定单品到已存在批次，默认状态为 `CREATED` |
| `bindNfcTag(bytes32 itemCodeHash, bytes32 nfcUidHash, bytes32 payloadHash)` | 绑定 NFC 到已存在单品，默认 NFC 状态为 `ACTIVE` |
| `updateNfcStatus(bytes32 nfcUidHash, uint8 status, bytes32 reasonHash)` | 更新 NFC 状态 |
| `appendTraceEvent(bytes32 itemCodeHash, bytes32 eventIdHash, uint8 eventType, bytes32 payloadHash, uint256 occurredAt)` | 追加单品溯源事件 |
| `appendSensorWindow(bytes32 batchIdHash, bytes32 windowIdHash, bytes32 deviceCodeHash, bytes32 payloadHash, uint256 windowStart, uint256 windowEnd)` | 追加传感器窗口摘要 |
| `updateItemStatus(bytes32 itemCodeHash, uint8 status, bytes32 reasonHash)` | 更新单品状态 |

## 查询函数

| 函数 | 说明 |
| --- | --- |
| `isWriter(address account)` | 查询账户是否具备写权限，owner 恒为 true |
| `batchExists(bytes32 batchIdHash)` | 查询批次是否存在 |
| `itemExists(bytes32 itemCodeHash)` | 查询单品是否存在 |
| `nfcExists(bytes32 nfcUidHash)` | 查询 NFC 是否存在 |
| `getBatch(bytes32 batchIdHash)` | 查询批次固定字段 |
| `getItem(bytes32 itemCodeHash)` | 查询单品固定字段 |
| `getNfcTag(bytes32 nfcUidHash)` | 查询 NFC 固定字段 |
| `getTraceEventCount(bytes32 itemCodeHash)` | 查询单品事件数量 |
| `getTraceEvent(bytes32 itemCodeHash, uint256 index)` | 按索引查询单品事件 |
| `getSensorWindowCount(bytes32 batchIdHash)` | 查询批次传感器窗口数量 |
| `getSensorWindow(bytes32 batchIdHash, uint256 index)` | 按索引查询传感器窗口 |

## 链上事件

| 事件 | 说明 |
| --- | --- |
| `WriterUpdated(address indexed writer, bool allowed, address indexed operator)` | writer 权限变化 |
| `BatchRegistered(bytes32 indexed batchIdHash, bytes32 indexed productIdHash, bytes32 payloadHash, uint256 startedAt, address indexed operator)` | 批次注册 |
| `BatchClosed(bytes32 indexed batchIdHash, uint8 status, bytes32 reasonHash, address indexed operator)` | 批次关闭或召回 |
| `ItemBound(bytes32 indexed itemCodeHash, bytes32 indexed batchIdHash, bytes32 payloadHash, address indexed operator)` | 单品绑定 |
| `NfcTagBound(bytes32 indexed itemCodeHash, bytes32 indexed nfcUidHash, bytes32 payloadHash, address operator)` | NFC 绑定 |
| `NfcStatusUpdated(bytes32 indexed nfcUidHash, uint8 status, bytes32 reasonHash, address indexed operator)` | NFC 状态更新 |
| `TraceEventAppended(bytes32 indexed itemCodeHash, bytes32 indexed eventIdHash, uint8 eventType, bytes32 payloadHash, uint256 occurredAt, address operator)` | 溯源事件追加 |
| `SensorWindowAppended(bytes32 indexed batchIdHash, bytes32 indexed windowIdHash, bytes32 indexed deviceCodeHash, bytes32 payloadHash, uint256 windowStart, uint256 windowEnd, address operator)` | 传感器窗口追加 |
| `ItemStatusUpdated(bytes32 indexed itemCodeHash, uint8 status, bytes32 reasonHash, address indexed operator)` | 单品状态更新 |

## 与后端、数据库的关系

后端负责生成业务数据、canonical JSON、哈希和交易请求。数据库保存业务明细、原始传感器数据、附件路径、链交易状态、交易哈希、区块高度和回执状态。合约保存摘要后，后端用交易回执和事件内容回填 `chain_transaction`，并在公众查询时把链上固定字段与链下明细重新计算的哈希做一致性校验。

## 安全约束

- `owner` 为部署账户，默认具备 writer 权限。
- 只有 `owner` 可以调用 `setWriter`。
- `owner` 或已授权 writer 可以写入业务摘要。
- 同一 `batchIdHash` 不能重复注册。
- 同一 `itemCodeHash` 不能重复绑定。
- 同一 `nfcUidHash` 不能重复绑定到多个单品。
- 同一单品只能绑定一个 NFC UID。
- 同一 `eventIdHash` 不能重复写入。
- 同一 `windowIdHash` 不能重复写入。
- 查询函数公开，不要求权限。
