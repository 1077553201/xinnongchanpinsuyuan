# 第四阶段派工提示词：IoT/MQTT 传感器数据与窗口摘要

最后更新：2026-09-29

将下面整段提示词交给执行代码生成的 AI。本阶段在第三阶段业务服务和链状态闭环成果之上，接入真实传感器数据通路、窗口摘要和异常告警。

```text
你是一名资深 Java 后端工程师和农业物联网平台工程师。请在 D:\xinnongchanpin 工作区内执行第四阶段：IoT/MQTT 传感器数据接入、窗口摘要与异常告警。

重要背景：

- 智能合约 `AgriTrace` 已生成并部署。
- 第二阶段已完成后端 FISCO BCOS 合约适配。
- 第三阶段已完成后端业务服务：产品、批次、单品、NFC、溯源事件、chain_transaction、公众查询聚合。
- 当前后端基于 FISCO BCOS 官方 spring-boot-crud 示例改造，包名主要是 `org.nongchanpin`。
- 已有 `ChainService.appendSensorWindow(AppendSensorWindowCommand)` 能力，但尚无传感器数据来源。
- 当前数据库脚本：`database/schema.sql`，已包含 `iot_gateway`、`iot_device`、`iot_sensor_reading`、`iot_data_window`。
- 当前 `application.yml` 已预留 `trace.mqtt.*` 配置但没有对应实现。

开始前必须读取并遵守：

1. docs/PROJECT_GOAL.md
2. docs/PREPARATION.md
3. docs/prompts/00_ORCHESTRATOR_RULES.md
4. docs/prompts/03_BACKEND_DOMAIN_SERVICES.md
5. contract/BACKEND_INTEGRATION_CONTRACT.md
6. backend/README.md
7. backend/pom.xml
8. database/schema.sql
9. backend/src/main/java/org/nongchanpin/chain/ChainService.java

一、阶段目标

- 打通 传感器 -> 网关 -> MQTT -> 后端 -> MySQL -> 窗口摘要 -> 链上摘要 的数据通路。
- 实现固定时间窗口聚合，生成稳定 dataHash 并调用 `appendSensorWindow`。
- 实现阈值异常告警。
- 提供 EMQX 开发环境和可测试的 mock telemetry 接口。
- 链路未配置时明确标记 `CHAIN_DISABLED`，不伪造上链成功。

二、技术选择

- MQTT 客户端：Eclipse Paho（`org.eclipse.paho.client.mqttv3`）。
- 沿用现有 MyBatis 注解 Mapper、Lombok、统一响应、全局异常处理风格。
- 不引入新的重型框架，不改变已有包结构风格。

三、数据模型

沿用 `iot_gateway`、`iot_device`、`iot_sensor_reading`、`iot_data_window`，并新增：

- `iot_alert`：告警记录。
- `iot_sensor_reading.batch_id`、`iot_data_window.batch_id`、`iot_data_window.gateway_code`：用于把窗口摘要锚定到批次和网关。

四、必须实现的接口

- `POST /api/iot/telemetry/mock`
- `POST /api/iot/gateways` / `GET /api/iot/gateways`
- `POST /api/iot/devices` / `GET /api/iot/devices`
- `GET /api/iot/readings`
- `GET /api/iot/windows`
- `POST /api/iot/windows/aggregate`
- `GET /api/iot/alerts`

五、窗口摘要与告警规则

- 按 `deviceCode + metric` 以 `trace.iot.window-minutes` 对齐固定时间桶。
- 摘要字段：`minValue`、`maxValue`、`avgValue`、`sampleCount`，统一 6 位小数归一化后计算 canonical `data_hash`。
- `data_hash` 唯一索引保证幂等。
- 仅当窗口关联批次时调用 `appendSensorWindow`，状态按 `CONFIRMED/FAILED/CHAIN_DISABLED` 回写。
- 阈值来自 `trace.iot.alerts.{metric}.min/max`，越界生成 `THRESHOLD_HIGH/THRESHOLD_LOW` 告警。

六、MQTT 接入

- `trace.mqtt.enabled=true` 时启动 Paho 客户端，订阅 `telemetry/status/event`。
- 支持 `+` 和 `#` 通配匹配，支持从 topic 解析 `farmId`、`gatewayCode`。
- broker 不可达时记录告警并保持禁用，不阻塞后端启动。
- 提供 `deploy/emqx/docker-compose.yml`。

七、测试要求

至少补充：

- `IotWindowAggregationServiceTest`：聚合统计正确、`data_hash` 稳定、有批次时上链、无批次时 `CHAIN_DISABLED`、按 hash 幂等。
- `IotTelemetryServiceTest`：遥测入库、metric 归一化、批次解析、未登记网关拒绝。
- `IotAnomalyServiceTest`：越上界/下界生成告警，范围内不生成告警。

八、不要做的事

- 写 Vue 前端。
- 重写 `AgriTrace.sol` 或重新生成 wrapper。
- 做 AI provider 真实调用。
- 做公有链真实锚定。
- 把传感器原始连续数据写到链上。
- 删除第三阶段已跑通的业务服务代码。

九、完成后的交付

最终回复必须说明：

- 新增/修改的关键文件。
- 已实现的 IoT 能力和 API。
- 传感器到链上摘要的闭环是否成立。
- 测试/构建运行结果。
- 下一阶段建议，通常是 NFC 与单品防伪查询深化，或 Vue 3 管理后台与公众查询页。
```

## 本阶段实际落地记录

第四阶段已完成，关键实现：

- 配置类：`MqttProperties`、`IotProperties`。
- MQTT 适配器：`org.nongchanpin.iot.MqttTelemetryAdapter`。
- 实体/Mapper：`IotGatewayEntity`、`IotDeviceEntity`、`IotSensorReadingEntity`、`IotAlertEntity` 及对应 Mapper。
- 服务：`IotDeviceService`、`IotTelemetryService`、`IotWindowAggregationService`、`IotAnomalyService`、`IotQueryService`。
- 控制器：`IotController`。
- 开发环境：`deploy/emqx/docker-compose.yml`。
- 测试：`IotWindowAggregationServiceTest`、`IotTelemetryServiceTest`、`IotAnomalyServiceTest`。

下一阶段入口：NFC 与单品防伪查询深化，或 Vue 3 管理后台与公众查询页。
