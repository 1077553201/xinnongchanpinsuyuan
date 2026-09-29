# AgriTrace 部署清单

本阶段不部署合约、不生成真实私钥、不复制证书、不写入节点密码。本清单用于用户手动部署到本地 FISCO BCOS 3.11 节点后回填信息。

## 部署前确认

- FISCO BCOS 3.11 节点已运行。
- 节点 RPC/SDK 端口可访问，例如 `.env.example` 中的 `127.0.0.1:20200`。
- 控制台或部署工具可用。
- SDK 证书目录可用，但证书正文不要提交到仓库。
- 已确认实际 `groupId`，默认可以从 `group0` 开始核对。
- 已确认部署账户地址和账户来源。
- 部署账户有足够权限和余额执行部署交易。
- 使用 `contract/AgriTrace.sol` 作为部署源码。
- 如果使用 FISCO 控制台，确认 Solidity 0.8.x 编译配置可用。

## 推荐部署产物路径

部署或编译完成后，建议把产物放到以下位置：

```text
contract/artifacts/AgriTrace.abi
contract/artifacts/AgriTrace.bin
backend/src/main/java/com/xinnong/trace/chain/contract/AgriTrace.java
```

如果当前后端 Java 包名与上述路径不一致，以后端真实包名为准，但必须在回填记录中写清楚实际路径。

## 部署后必须回填的信息

| 项目 | 示例/位置 | 说明 |
| --- | --- | --- |
| 合约地址 | `.env` 的 `FISCO_CONTRACT_ADDRESS` | 只写入本地 `.env`，不要写入 `.env.example` |
| 部署交易 hash | `docs/FISCO_DEPLOYMENT_RECORD.md` | 用于追踪部署交易 |
| 部署区块高度 | `docs/FISCO_DEPLOYMENT_RECORD.md` | 用于确认部署所在区块 |
| ABI 文件路径 | `contract/artifacts/AgriTrace.abi` | 后端适配和事件解析需要 |
| bin 文件路径 | `contract/artifacts/AgriTrace.bin` | 复核部署字节码需要 |
| Java wrapper 文件路径 | `backend/src/main/java/com/xinnong/trace/chain/contract/AgriTrace.java` | 后端 ChainService 调用需要 |
| 部署账户地址 | `docs/FISCO_DEPLOYMENT_RECORD.md` | 该地址是合约 owner |
| `groupId` | `.env` 的 `FISCO_GROUP_ID` | 与节点配置一致 |
| peers | `.env` 的 `FISCO_PEERS` | 例如 `127.0.0.1:20200` |
| SDK 证书路径 | `.env` 的 `FISCO_SDK_CERT_PATH` | 本地未跟踪路径 |

## 建议创建部署记录

手动部署后建议新增：

```text
docs/FISCO_DEPLOYMENT_RECORD.md
```

推荐内容：

```markdown
# FISCO BCOS 部署记录

- 合约：AgriTrace
- 源码：contract/AgriTrace.sol
- 部署时间：
- groupId：
- peers：
- 部署账户地址：
- 合约地址：
- 部署交易 hash：
- 部署区块高度：
- ABI 路径：contract/artifacts/AgriTrace.abi
- bin 路径：contract/artifacts/AgriTrace.bin
- Java wrapper 路径：
- contract2java 工具路径：
- 备注：
```

## 后端回填配置

只在本地 `.env` 或本地未跟踪配置中写真实值：

```dotenv
FISCO_GROUP_ID=group0
FISCO_PEERS=127.0.0.1:20200
FISCO_SDK_CERT_PATH=replace_with_local_untracked_path
FISCO_CONTRACT_ADDRESS=0x...
```

不要把真实私钥、keystore 密码、证书正文或节点密码写入仓库。

## 验收点

- 合约源码能被 FISCO BCOS 3.11 控制台或部署工具编译。
- `AgriTrace.abi`、`AgriTrace.bin` 和 Java wrapper 已生成。
- 部署回执状态成功。
- 合约地址、交易 hash、区块高度和部署账户地址已记录。
- 后端工程师可以根据 `BACKEND_INTEGRATION_CONTRACT.md` 开始适配 ChainService。
