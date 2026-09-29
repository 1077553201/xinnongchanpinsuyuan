# EMQX 开发环境

本目录提供农产品溯源系统开发阶段使用的 MQTT Broker 环境。开发默认使用 EMQX，生产环境请替换为实际网关接入方案。

## 端口规划

| 端口 | 用途 |
| --- | --- |
| `1883` | MQTT |
| `8083` | MQTT over WebSocket |
| `18083` | EMQX Dashboard |

## 启动

```bash
cd deploy/emqx
docker compose up -d
```

Dashboard 默认地址：<http://127.0.0.1:18083>，默认账号 `admin` / `public`。首次登录后请修改密码，且不要把真实密码写入仓库。

## 对接后端

启动后在后端本地 `.env` 中设置：

```dotenv
MQTT_ENABLED=true
MQTT_HOST=127.0.0.1
MQTT_PORT=1883
```

后端启动后会订阅：

- `agri/+/+/telemetry`：遥测数据，进入 `iot_sensor_reading` 并触发窗口聚合。
- `agri/+/+/status`：网关状态，当前仅记录日志。
- `agri/+/+/event`：网关事件，当前仅记录日志。

broker 未启动时不会阻塞后端启动，MQTT 适配器会记录告警并保持禁用。

## 调试发送示例

```bash
# 使用 mosquitto_pub 或任意 MQTT 客户端发送一条遥测
mosquitto_pub -h 127.0.0.1 -p 1883 -t agri/1/GW1/telemetry \
  -m '{"deviceCode":"DEV1","metric":"temperature","value":26.5,"unit":"celsius","reportedAt":"2026-09-29 10:03:00"}'
```

`farmId` 和 `gatewayCode` 缺省时会从 topic 的 `agri/{farmId}/{gatewayCode}/telemetry` 段解析。
