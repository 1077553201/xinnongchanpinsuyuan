# FISCO BCOS 部署记录

最后更新：2026-07-07

## AgriTrace 1.0

| 项目 | 内容 |
| --- | --- |
| 合约名称 | `AgriTrace` |
| CNS | `AgriTrace 1.0` |
| 源码路径 | `contract/AgriTrace.sol` |
| 合约地址 | `0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94` |
| 部署用户 | `mhhh` |
| 部署账户地址 | `0xd8d242c3b2c751458471573d5934bd0888a29f48` |
| groupId | `group0`，如实际不是 `group0` 请回填修正 |
| Java wrapper 路径 | `D:\xinnongchanpin\contract\java\AgriTrace.java` |
| SDK 证书目录 | `D:\xinnongchanpin\contract\sdk` |
| ABI 输出 | 已由部署结果输出，建议另存为 `contract/artifacts/AgriTrace.abi` |
| bin 输出 | 已由部署结果输出，建议另存为 `contract/artifacts/AgriTrace.bin` |
| 部署交易 hash | 待回填 |
| 部署区块高度 | 待回填 |
| 部署时间 | 待回填 |
| contract2java 工具路径 | 待回填 |

## 已确认本地文件

- `contract/java/AgriTrace.java`
- `contract/sdk/ca.crt`
- `contract/sdk/sdk.crt`
- `contract/sdk/sdk.key`

## 后端适配回填项

后端适配前，请确认本地 `.env` 或后端本地配置包含：

```dotenv
FISCO_CONTRACT_ADDRESS=0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94
FISCO_GROUP_ID=group0
FISCO_PEERS=127.0.0.1:20200
FISCO_SDK_CERT_PATH=D:\xinnongchanpin\contract\sdk
```

如果实际 `groupId`、`peers` 或证书目录不同，以本地节点配置为准。

当前后端默认读取：

- `backend/src/main/resources/application.properties` 中的 `contract.agriTraceAddress`
- 环境变量 `SYSTEM_HEX_PRIVATE_KEY`
- `backend/src/main/resources/conf` 下的 SDK 证书

部署交易 hash、部署区块高度和部署时间仍待真实回填，后端适配阶段不伪造这些值。

## 安全备注

- 本记录只保存证书目录路径，不保存证书正文、私钥正文、账户密码或节点密码。
- `contract/sdk/*.crt` 和 `contract/sdk/*.key` 属于本地敏感文件，当前 `.gitignore` 已忽略 `*.crt` 与 `*.key`，不要提交到远程仓库。
- 合约部署账户 `0xd8d242c3b2c751458471573d5934bd0888a29f48` 是 `AgriTrace` 的 owner；后端写链账户需要由 owner 调用 `setWriter(address,bool)` 授权，或直接使用 owner 账户。
