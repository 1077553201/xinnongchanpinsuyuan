# 前端启动准备与缺口分析

> 历史快照：本文保留 2026-09-29 的调研结论，不代表当前实现。前端、身份与新种植模型已演进，小车不能直接作为固定设备绑定；当前缺口与优先级以 [未完成模块报告](UNFINISHED_MODULES_REPORT.md) 和 [首版交付计划](FIRST_TRIAL_RELEASE_PLAN.md) 为准。

最后更新：2026-09-29

## 一、结论摘要

- **后端 + 合约 + IoT 已可用**：REST 接口齐全，统一响应、异常处理、hash 校验、链状态闭环都已落地，前端可以直接对接。
- **前端为 0**：仓库内不存在 `frontend/`，没有任何前端代码或脚手架。
- **AI 模块后端也为 0**：只有 `trace.ai.*` 配置占位与 `ai_session` / `ai_message` 两张表，没有 `AiProvider`、`AiService`、`AiController`。
- **阻塞前端开工的只有 3 件事**：
  1. 后端**没有 CORS 配置**（前端 dev server 5173 直接请求 8080 会被浏览器拦截）→ 用 Vite dev 代理解决，后端零改动。
  2. 没有前端工程骨架 → 需创建 `frontend/`。
  3. 后端**没有登录鉴权**（无 JWT、无 Spring Security）→ 后台登录页只能先做占位。
- **环境就绪**：Node v22.22.2、npm 10.9.7，npm registry 可经系统代理访问（`npm ping` 通）。

## 二、项目整体构造（现状）

```text
D:\xinnongchanpin
├── backend/          Spring Boot 2.4 + Java 11 + MyBatis + FISCO SDK   ✅ 可运行
│   └── org.nongchanpin
│       ├── chain/        ChainService / AgriTraceChainService / ChainParameterBuilder  ✅
│       ├── contracts/    AgriTrace.java（官方 wrapper）                                ✅
│       ├── controller/   Product / Batch / TraceItem / TraceEvent / Nfc / Chain / Iot / Public  ✅
│       ├── service/      业务服务 + 链交易 + IoT 聚合/告警                              ✅ (AI 缺失)
│       ├── iot/          MqttTelemetryAdapter（Paho）                                  ✅
│       ├── mapper/       注解式 MyBatis Mapper                                          ✅
│       └── model/        entity / dto / vo / command / bo                               ✅
├── contract/         AgriTrace.sol + 部署记录 + 集成契约              ✅ 已部署
├── database/         schema.sql / seed.sql（17 张业务表 + iot_alert）  ✅
├── deploy/emqx/      EMQX docker-compose                              ✅
├── docs/             目标、环境、分阶段派工文档                         ✅
└── frontend/         ——  不存在                                       ❌ 待创建
```

各模块完成度：

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| 智能合约 | 100% | 已部署，地址 `0x0102e8b6...` |
| 后端基座 + 链适配 | 100% | hash、回执、状态机、工具类齐全 |
| 后端业务服务 | 100% | 产品/批次/单品/NFC/事件/链交易/公众查询 |
| IoT/MQTT | 90% | 遥测入库、窗口聚合、告警、EMQX 已就绪；缺网关密钥鉴权 |
| 前端 | 0% | 无代码 |
| AI 助手 | 0% | 后端也只有配置和表 |
| NFC 防伪深化 | 30% | 已有绑定/禁用；缺换绑、异常态、动态签名 |
| 公有链锚定 | 0% | 仅 `anchor_record` 表 |
| 部署与验收 | 20% | 缺顶层 README、启动脚本、演示数据 |

## 三、后端接口清单（前端可直接消费）

统一前缀与响应：所有接口返回 `{ code: "0", message: "", data: ... }`；异常时 `code` 为 `"400"` / `"500"` 并带对应 HTTP 状态码。

### 公众查询（无需鉴权）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/public/trace/{traceCode}` | 扫码/NFC 查询入口，返回产品、批次、单品、NFC、时间线、质检、环境摘要、链上校验、AI 状态 |

### 产品 / 批次

| 方法 | 路径 | 入参要点 |
| --- | --- | --- |
| POST | `/api/products` | `organizationId, productCode, productName, category, variety, originPlace, description` |
| GET | `/api/products`、`/api/products/{id}` | — |
| POST | `/api/batches` | `productId, organizationId, batchCode, productionArea, plantDate, harvestDate, metadata` → 触发 `registerBatch` |
| GET | `/api/batches`、`/api/batches/{id}` | — |
| POST | `/api/batches/{id}/close` | `status`(2=CLOSED/3=RECALL), `reasonPayload` → 触发 `closeBatch` |

### 单品码 / 溯源事件

| 方法 | 路径 | 入参要点 |
| --- | --- | --- |
| POST | `/api/items/generate` | `batchId, count(1..100)` → 生成 traceCode + `bindItem` |
| GET | `/api/items`、`/api/items/{id}` | 返回含 `traceCodePlain`、`traceUrl`（二维码内容） |
| POST | `/api/items/{itemId}/events` | `eventType, eventTime, location, operatorName, payload` → `appendTraceEvent` |
| GET | `/api/items/{itemId}/events` | 时间线 |

### NFC

| 方法 | 路径 | 入参要点 |
| --- | --- | --- |
| POST | `/api/nfc/bind` | `itemId, nfcUid, tagHash, payload` → `bindNfcTag` |
| POST | `/api/nfc/{id}/disable` | `reasonPayload` → `updateNfcStatus` |

### IoT

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/iot/telemetry/mock` | 真实 MQTT payload 格式的遥测 |
| GET/POST | `/api/iot/gateways`、`/api/iot/devices` | 网关/设备列表与登记 |
| GET | `/api/iot/readings` | `deviceCode, metric, limit` |
| GET | `/api/iot/windows` | `deviceCode, limit` |
| POST | `/api/iot/windows/aggregate` | 手动触发窗口聚合 |
| GET | `/api/iot/alerts` | `deviceCode, limit` |

### 链状态（后台「链上交易查看」页用）

| 方法 | 路径 |
| --- | --- |
| GET | `/api/chain/health` → `groupId, peers, contractAddress, chainEnabled, accountAddress, writerStatus, latestBlock, status` |
| GET | `/api/chain/batches/{batchCode}`、`/api/chain/items/{itemCode}` |
| GET | `/api/chain/items/{itemCode}/events/{index}` |
| GET | `/api/chain/batches/{batchCode}/sensor-windows/{index}` |

## 四、数据契约要点（前端必须遵守）

- **时间格式**：后端 `spring.jackson.date-format=yyyy-MM-dd HH:mm:ss`、时区 `GMT+8`。`LocalDate` 为 `yyyy-MM-dd`。前端提交 `eventTime` / `reportedAt` 必须用该格式字符串，否则反序列化失败。
- **空值策略**：`default-property-inclusion: non_null`，后端不会返回 `null` 字段，前端取值需做缺省处理。
- **状态枚举**（用于状态 chip 配色）：
  - `chain_status`：`PENDING` / `CONFIRMED` / `FAILED` / `CHAIN_DISABLED`
  - 单品 `qr_status`：`GENERATED`；`nfc_status`：`UNBOUND` / 已绑定
  - 批次关闭 `status`：`2=CLOSED`、`3=RECALL`
  - 事件类型 `1..11`：SOWING / FARMING / HARVEST / PROCESSING / QUALITY_TEST / WAREHOUSE_IN / WAREHOUSE_OUT / LOGISTICS / STORE_ARRIVAL / SALE / RECALL
- **公众页数据源**：`PublicTraceView` → `product / batch / item / nfc / timeline[] / qualityReports[] / environmentSummary[] / chainVerification{ contractAddress, latestTxHash, latestChainStatus, itemPayloadHashMatched, batchPayloadHashMatched, records[] } / ai{ available, provider }`。
- **边界展示**：`chainVerification` = 链上已验证事实；`ai.available=false` 时 AI 区块必须显示「未启用」，不能把 AI 输出混进事实区。

## 五、残缺部分清单

1. **前端（0%）** — 本阶段重点。9 个页面：登录占位、后台首页、产品/批次管理、单品码管理、传感器监控、NFC 绑定、链上交易查看、农场 AI 助手、公众查询页 `/t/:traceCode`。
2. **AI 助手（0%，含后端）** — 需补 `AiProvider` 接口 + disabled 实现 + `/api/ai/farm/chat`、`/api/ai/public/chat` + `ai_session`/`ai_message` 落库；前端只做入口与对话 UI，边界必须区分「链上事实」与「AI 建议」。
3. **NFC 防伪深化（30%）** — 换绑流程、标签不匹配/疑似异常态、动态签名预留。
4. **小车硬件（田间巡检小车 / 移动网关，已确认形态）** — 结论：**后端无需改造，零成本接入**。小车作为移动网关直接复用现有 MQTT 通道：
   - topic：`agri/{farmId}/{carCode}/telemetry`，`carCode` 即 `gatewayCode`
   - payload 沿用 `TelemetryRequest`：`deviceCode, metric, value, unit, reportedAt, signature, batchCode`
   - `farmId` / `gatewayCode` 缺省时由适配器从 topic 段解析（已实现 `enrichFromTopic`）
   - 首次上报自动登记网关与设备（`trace.iot.auto-register-*` 默认 `true`）；也可先调 `POST /api/iot/gateways`、`POST /api/iot/devices` 预登记
   - 数据落库、窗口聚合、异常告警、链上摘要全部复用现有 IoT 链路，无需新增代码
   - **唯一缺口**：小车位置（经纬度）当前无承载字段。若需要「巡检轨迹」，两种低成本方案：(a) 用 `metric=longitude` / `metric=latitude` 复用现有读数列；(b) 新增 `iot_track` 表存轨迹点。属可选增强，可在第五或第六阶段一并做
   - 前端「传感器监控」页需按 `gatewayCode` 区分固定网关与移动小车（同一套数据模型，仅展示分组不同）
5. **公有链锚定（0%）** — 仅预留表，V1 不实现。
6. **部署与验收（20%）** — 顶层 README、一键启动脚本、演示种子数据、端到端验收清单。

## 六、前端技术选型与目录结构

选型（与既有约定一致）：**Vue 3 + TypeScript + Vite + Composition API（`<script setup lang="ts">`）+ Vue Router + Pinia + Axios**。

```text
frontend/
├── index.html
├── package.json
├── vite.config.ts          # dev 代理 /api → http://127.0.0.1:8080
├── tsconfig.json
├── .env.development        # VITE_API_BASE=/api
├── .env.production
└── src/
    ├── main.ts
    ├── App.vue
    ├── router/index.ts     # /t/:traceCode 公众页；/admin/* 后台
    ├── api/
    │   ├── http.ts         # axios 实例：解包 CommonResponse、统一错误
    │   ├── public.ts       # 公众查询
    │   ├── product.ts / batch.ts / item.ts / event.ts / nfc.ts
    │   ├── iot.ts / chain.ts / ai.ts
    ├── types/              # 与后端 DTO/VO 对齐的 TS 类型
    ├── stores/             # 会话、后台状态
    ├── styles/             # design tokens（颜色/字体/间距）
    ├── components/         # 状态 chip、校验印章、农事历时间轴、指标条
    └── views/
        ├── public/TraceView.vue          # 移动优先
        └── admin/  Login / Dashboard / Products / Batches / Items /
                    IotMonitor / NfcBinding / ChainExplorer / AiFarmAssistant
```

**CORS 方案**：dev 用 Vite 代理（`/api` → `127.0.0.1:8080`），生产建议由 Nginx 同源反代；**不改后端**。

## 七、设计方向（frontend-design 输出）

**Brief**：农产品可信溯源。两个界面 —— 消费者移动端查询页（唯一任务：证明「这件产品的历史是真的、没被改过」）与高密度管理后台。

**反模式规避**：明确避开「米白 + 陶土红衬线」的通用农业模板、「近黑 + 荧光绿」的科技模板、「报纸细线密栏」模板。

**配色（6 个具名值）**

| Token | 值 | 用途 |
| --- | --- | --- |
| `--ink` | `#0F241D` 墨绿近黑 | 正文、深色表面 |
| `--field` | `#1E5B48` 田垄绿 | 主品牌色、导航 |
| `--mist` | `#E9EFEA` 晨雾 | 页面底色、浅表面（冷调，非暖米白） |
| `--seal` | `#BE3A2B` 印泥朱 | **唯一高风险色**，只用于校验印章与关键强调 |
| `--grain` | `#C79A34` 稻穗金 | 环境指标、数据高亮 |
| `--slate` | `#6B7A73` 石墨 | 次级文字、分隔线 |

**字体（3 个角色）**

- 显示体：`Noto Serif SC`（思源宋体）—— 标题与产品名，农事历/检验证书的文献感
- 正文字体：`Noto Sans SC`
- 数据字体：`JetBrains Mono` —— traceCode、payloadHash、区块号、传感器数值

**布局**

```text
公众查询页（移动优先，单列纵向）
┌──────────────────────────┐
│ 产品身份块 ＋ 链上校验印章 │  ← hero：先给结论
├──────────────────────────┤
│ 生长环境指标条（温度/湿度…）│
├──────────────────────────┤
│ 农事历时间轴               │
│  链下明细 ｜ 链上证据       │  ← 双态并列
├──────────────────────────┤
│ 质检摘要                   │
├──────────────────────────┤
│ AI 问答入口（未启用态）     │
└──────────────────────────┘

管理后台：左侧窄导航 + 顶部筛选条 + 高密度表格 + 状态 chip
```

**Signature（唯一记忆点）**：「**链上校验印章**」——一枚按 `CONFIRMED / PENDING / FAILED / CHAIN_DISABLED` 呈现四种形态的设计印章（SVG），配合「农事历双态行」（左侧链下描述，右侧链上 hash/区块/状态）。其余全部保持克制。

**质量底线**：响应式到移动端、键盘可见焦点、尊重 `prefers-reduced-motion`。

## 八、启动步骤

```bash
cd /d/xinnongchanpin
npm create vite@latest frontend -- --template vue-ts
cd frontend
npm install
npm install vue-router pinia axios
npm run dev        # 5173
```

后端同步启动（8080）：

```bash
export MVN="/c/Users/10775/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn"
cd backend && "$MVN" spring-boot:run
```

## 九、待确认

1. 前端起步范围与优先级（本轮决定：**先只出方案，暂不写代码**）。
2. `backend/src/main/resources/application.properties` 中的真实私钥是否改回环境变量读取（安全项）。

已确认：

- 「小车硬件」= 田间巡检小车（移动网关），复用现有 MQTT 通道，后端零改造；仅「巡检轨迹」为可选增强。
- 前端设计方向见第七节，待你确认后即可按第八节脚手架开工。
