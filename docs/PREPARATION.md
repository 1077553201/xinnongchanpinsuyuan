# 前提准备与环境收集

最后更新：2026-07-07

## 当前状态

- 工作区：`D:\xinnongchanpin`
- 当前仓库状态：工作区初始为空，创建文档前还不是 git 仓库。
- 当前阶段：项目目标和前提准备落盘，尚未初始化后端、前端、合约和数据库工程。

## 已确认环境

| 项目 | 当前结果 | 说明 |
| --- | --- | --- |
| Java | 11.0.29 | 可作为 Spring Boot 后端开发运行环境。 |
| Maven | PATH 中仍无 `mvn`，但 `~/.m2/wrapper/dists/apache-maven-3.9.16` 下存在可用发行版 | 可用绝对路径直接运行构建与测试；`~/.m2/repository` 依赖缓存完整。 |
| Node.js | v24.12.0 | 可用于 Vue 3/Vite 前端开发。 |
| npm | 11.6.2 | 可用于安装前端依赖。 |
| MySQL 客户端 | 8.0.45 | 客户端可用。 |
| MySQL 端口 | `127.0.0.1:3306` 可连 | 用户选择使用现有账号。 |
| Docker | 已安装 | Docker daemon 当前未运行。 |
| MQTT Broker | 未发现 `mosquitto` 命令 | 开发阶段计划使用 EMQX/Docker。 |
| Solidity 编译器 | 未发现 `solc` 命令 | 合约 Java 类优先使用 FISCO 控制台工具生成。 |
| FISCO SDK 端口 | `127.0.0.1:20200` 可连 | 节点目录和证书路径待用户提供。 |

## 当前顺序调整

后端基础代码已经由执行 AI 按上一版提示词生成到 `backend/`，并保留了 FISCO 官方示例的 Maven Wrapper 和 SDK 示例结构。现在先暂停继续扩展后端业务，下一步优先生成智能合约。

当前最新状态：

- `contract/AgriTrace.sol` 已生成。
- 合约已部署，当前记录文件实际位于 `contract/FISCO_DEPLOYMENT_RECORD.md`。
- 当前合约地址：`0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94`。
- CNS：`AgriTrace 1.0`。
- 部署账户地址：`0xd8d242c3b2c751458471573d5934bd0888a29f48`。
- Java wrapper 已存在于 `contract/java/AgriTrace.java`，当前后端也已有 `backend/src/main/java/org/nongchanpin/contracts/AgriTrace.java`。
- ABI/bin 已存在于 `backend/src/main/resources/abi/` 和 `backend/src/main/resources/bin/`。
- SDK 证书目录已存在，开发阶段可以留在本机用于真实链调用。
- `backend/src/main/resources/application.properties` 曾出现真实十六进制私钥，现已改为 `system.hexPrivateKey=${SYSTEM_HEX_PRIVATE_KEY:}`；如果本地调试必须临时写回真实值，可以为了开发进度这样做，但不要把真实值写入 README、docs 或最终回复。

新顺序：

1. 生成 `contract/AgriTrace.sol`、合约接口说明和部署清单。
2. 用户手动部署到本地 FISCO BCOS 3.11 节点。
3. 将 ABI、bin、Java wrapper、合约地址、部署交易哈希和区块高度拉回仓库。
4. 后端再根据真实合约接口适配 ChainService、交易记录和数据库字段。

当前下一步：

```text
docs/prompts/03_BACKEND_DOMAIN_SERVICES.md
```

第二阶段后端合约适配已完成：

- 已形成 `ChainService` 边界和 `AgriTraceChainService`。
- 已提供 `/api/chain/**` smoke API。
- 已补齐 hash、bytes32、时间工具和状态码。
- 已接入 `AgriTrace.java` wrapper 的核心函数。
- 已新增离线测试，但 Maven wrapper 缺少 `.mvn/wrapper/maven-wrapper.jar`，`mvnw.cmd test` 暂未跑通。

第三阶段目标：

- 恢复或明确后端可测试构建路径。
- 实现产品、批次、单品码、NFC、溯源事件和 `chain_transaction` 业务持久化。
- 串起 MySQL + ChainService + 链交易状态回填。
- 完成公众查询聚合和链上 hash 校验。

第三阶段已完成，并已恢复可测试构建路径：使用本机 `~/.m2/wrapper/dists` 下的 Maven 3.9.16 运行 `mvn -o test`，当前 11 个既有测试全部通过。

第四阶段已完成（IoT/MQTT 传感器接入、窗口摘要与异常告警）：

- 新增 MQTT 适配器 `org.nongchanpin.iot.MqttTelemetryAdapter`，`trace.mqtt.enabled=true` 时订阅 telemetry/status/event，broker 不可达不阻塞启动。
- 新增配置类 `MqttProperties`（`trace.mqtt`）、`IotProperties`（`trace.iot`，含窗口分钟数与阈值映射）。
- 新增实体与 Mapper：`IotGatewayEntity`、`IotDeviceEntity`、`IotSensorReadingEntity`、`IotAlertEntity`，并扩展 `IotDataWindowMapper`。
- 新增服务：`IotDeviceService`、`IotTelemetryService`、`IotWindowAggregationService`、`IotAnomalyService`、`IotQueryService`。
- 新增 `IotController`，提供 telemetry/mock、gateways、devices、readings、windows、windows/aggregate、alerts 接口。
- 传感器到链上摘要闭环打通：`iot_sensor_reading` -> 固定窗口聚合 -> `iot_data_window` -> `appendSensorWindow`；未关联批次的窗口标记 `CHAIN_DISABLED`。
- `database/schema.sql` 新增 `iot_alert` 表，并为读数/窗口补充 `batch_id`、`gateway_code`。
- `backend/pom.xml` 增加 Eclipse Paho MQTT 客户端依赖。
- 新增 `deploy/emqx/docker-compose.yml` 作为 EMQX 开发环境。
- 新增测试：`IotWindowAggregationServiceTest`、`IotTelemetryServiceTest`、`IotAnomalyServiceTest`；全部 23 个测试通过。

当前下一步：

```text
docs/prompts/05_NFC_ANTI_COUNTERFEIT.md 或 docs/prompts/06_VUE_FRONTEND.md
```

## 官方后端模板来源

后端工程不要从零手写 Spring Boot 模板。第一阶段必须先克隆 FISCO BCOS 官方 Maven SpringBoot 示例，再基于它扩展业务能力。

- 官方文档：<https://fisco-bcos-doc.readthedocs.io/zh-cn/latest/docs/sdk/java_sdk/spring_boot_crud.html>
- GitHub：<https://github.com/FISCO-BCOS/spring-boot-crud.git>
- Gitee 备用：<https://gitee.com/FISCO-BCOS/spring-boot-crud>

官方示例已包含 Maven Wrapper，适合当前本机 `mvn` 不在 PATH 的情况。后续执行 AI 应优先使用示例中的 `mvnw` 编译，而不是要求系统全局安装 Maven。

## 待用户提供

### FISCO BCOS

- 节点或控制台目录路径。
- SDK 证书所在目录。
- `peers` 地址列表。
- `groupId`，默认先按 `group0` 或节点实际配置确认。
- 账户私钥或 keystore 的存放方式。
- `contract2java.sh` 或 FISCO 控制台路径。

注意：私钥、证书正文和真实账户密码不要写入文档。后续只写到本地 `.env` 或本地未跟踪配置中。

### MySQL

- 数据库 host，默认可先使用 `127.0.0.1`。
- 数据库 port，默认可先使用 `3306`。
- 目标库名，建议 `agri_trace`。
- 用户名。
- 密码提供方式，建议写入本地 `.env`。
- 当前账号是否允许创建数据库、建表和初始化数据。
- 字符集，建议 `utf8mb4`。

### MQTT/EMQX

- Docker Desktop 是否已启动。
- EMQX 开发端口规划：
  - MQTT：`1883`
  - Dashboard：`18083`
  - WebSocket：`8083`
- 设备账号规则。
- 网关编号规则。
- MQTT topic 最终格式。
- 消息签名或设备密钥字段。

### AI

- 优先使用本地模型还是云端模型。
- AI 服务访问方式。
- 是否需要知识库资料导入。
- 农场管理助手和消费者助手的回答边界。

V1 默认原则：AI 只做解释、摘要、建议和问答，不直接写链、不直接修改业务数据、不直接下发设备控制命令。

## 推荐前置动作

1. 安装或准备 Maven：

```powershell
mvn -version
```

如果暂时不装 Maven，可以在后端初始化时加入 Maven Wrapper。

2. 启动 Docker Desktop，准备 EMQX：

```powershell
docker --version
docker ps
```

Docker daemon 正常后，再规划 EMQX 容器启动命令。

3. 提供 FISCO 节点或控制台目录后，检查：

```powershell
Get-ChildItem <fisco-console-or-node-path> -Recurse -File
```

需要确认配置文件、证书目录、控制台工具和合约转 Java 工具。

4. 准备本地 `.env`：

```powershell
Copy-Item .env.example .env
```

然后只在 `.env` 填真实密码、证书路径和私钥相关配置。

## 技能与协作工具

已纳入后续协作基准的技能：

- `vue`：Vue 3 Composition API、TypeScript、`<script setup lang="ts">`。
- `frontend-design`：后台和公众扫码页的视觉方向与交互质量。
- `crafting-effective-readmes`：后续生成面向开发、部署和演示的 README。
- `find-skills`：继续发现适合规划、测试、架构审查和前端优化的技能。
- `web-design-guidelines`：前端完成后做 UI、UX 和可访问性审查。

后续可考虑安装的技能：

- `writing-plans`：复杂多阶段实施计划。
- `improve-codebase-architecture`：后端和前端模块边界审查。
- `webapp-testing`：使用 Playwright 验证本地 Web 应用。

参考：

- <https://www.skills.sh/>
- <https://www.skills.sh/obra/superpowers/writing-plans>
- <https://www.skills.sh/mattpocock/skills/improve-codebase-architecture>
- <https://www.skills.sh/vercel-labs/agent-skills/web-design-guidelines>
- <https://www.skills.sh/anthropics/skills/webapp-testing>

## 安全规则

- 不把真实数据库密码写入 Markdown 文档。
- 开发阶段优先跑通真实链调用，不因本地存在私钥或证书而阻塞开发。
- 不把 FISCO 私钥、keystore 密码、证书正文写入 README、docs 或最终回复。
- `.env`、本地上传文件和构建产物不纳入版本管理；本地 SDK 证书是否提交由用户按开发便利性决定。
- `.env.example` 只保留变量名和占位值。
- README 只写配置说明，不写真实敏感值。

## 验收清单

- `docs/PROJECT_GOAL.md` 存在，且覆盖项目目标、技术路线、可信数据策略、IoT、NFC、AI 和里程碑。
- `docs/PREPARATION.md` 存在，且覆盖本机环境事实、缺口和需要继续收集的信息。
- `.env.example` 存在，且不包含真实密码、私钥或证书正文。
- `.gitignore` 存在，且忽略 `.env`、证书目录、构建产物和依赖目录。
- 后续拿到 FISCO 路径和 MySQL 账号后，可以继续初始化后端、前端、合约和数据库脚手架。
