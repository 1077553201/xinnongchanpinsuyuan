# 农产品可信溯源系统代码生成总控提示词

最后更新：2026-07-07

> 注意：本文件保留为早期草稿。当前正式代码生成请优先使用 `docs/prompts/00_ORCHESTRATOR_RULES.md` 和 `docs/prompts/03_BACKEND_DOMAIN_SERVICES.md`。后续阶段也统一放在 `docs/prompts/` 下。

下面提示词用于交给代码生成代理，让它按照本仓库已经落盘的计划开始生成代码。

```text
你是一名资深全栈工程师、区块链应用工程师和物联网系统工程师。请在 D:\xinnongchanpin 工作区内，按照已有项目计划开始研发“农产品可信溯源系统”。

开始前必须先读取并遵守这些文件：

1. docs/PROJECT_GOAL.md
2. docs/PREPARATION.md
3. .env.example
4. .gitignore

一、项目目标

建设一个面向真实农产品生产、流通和消费查询的可信溯源系统：

- 单品级溯源：每件农产品一个唯一 traceCode。
- 二维码和 NFC 共存：扫码和 NFC 触碰进入同一个 /t/{traceCode} 查询页。
- 区块链：使用本地已部署的 FISCO BCOS 3.11 节点，只做 SDK 连接、合约部署和业务存证，不做链节点部署。
- 后端：基于 FISCO 官方 Spring Boot Maven 示例思路研发，Java 11。
- 前端：Vue 3 + TypeScript + Composition API + <script setup lang="ts">。
- 数据库：MySQL 8，业务明细链下保存。
- IoT：真实硬件传感器通过边缘网关 + MQTT 接入，开发阶段优先 EMQX/Docker。
- AI：提供农场管理助手和消费者助手，但 AI 只做摘要、解释、建议和问答，不直接写链、不直接修改业务数据、不直接控制设备。
- 公有链：V1 只预留 AnchorService 和 anchor_record，不真实发送公链交易。

二、硬性约束

- 不要把数据库密码、FISCO 私钥、keystore 密码、证书正文写入仓库。
- 不要提交 .env、本地证书、本地上传文件、node_modules、target、dist。
- 不要假设 FISCO 节点目录已经在仓库内；如果需要证书或 console 路径，先在文档中标记待用户提供。
- 不要部署 FISCO BCOS 节点，节点已经部署完成。
- 当前系统 mvn 不在 PATH 中，因此优先创建 Maven 项目文件和说明；只有在 Maven 可用或用户允许安装/下载时才运行 Maven 构建。
- Docker 已安装但 daemon 可能未运行；EMQX 相关先提供 docker compose 或说明，不强行启动失败流程。
- 先实现可运行骨架和清晰边界，再逐步接真实链、真实设备和真实 AI provider。
- 若发现用户已有文件变更，必须保留并兼容，不要删除或重置。

三、推荐工程结构

请按以下结构初始化或补齐项目：

backend/
  pom.xml
  src/main/java/com/xinnong/trace/
  src/main/resources/
frontend/
  package.json
  index.html
  src/
contract/
  AgriTrace.sol
database/
  schema.sql
  seed.sql
docs/
  README or setup docs as needed

四、首次编码冲刺目标

请完成“最小可信骨架”，不要一次性把所有复杂功能写满：

1. 合约
   - 创建 contract/AgriTrace.sol。
   - 支持批次注册、单品码绑定、NFC 哈希绑定、业务事件摘要追加、传感器窗口摘要追加、基础查询事件。
   - 链上只存 hash、状态、时间、操作者和索引，不存图片、报告全文、原始传感器流。

2. 数据库
   - 创建 database/schema.sql。
   - 表至少覆盖：组织、用户、产品、批次、单品码、NFC 标签、溯源事件、传感器设备、网关、传感器读数、传感器窗口摘要、质检报告、链交易记录、AI 会话、AI 消息、anchor_record。
   - 使用 utf8mb4。
   - 所有表包含 create_time、update_time，关键业务表包含 status。

3. 后端
   - 创建 Spring Boot Maven 工程骨架。
   - 包名使用 com.xinnong.trace。
   - 先实现配置、统一响应、异常处理、基础实体/DTO、Controller/Service/Repository 分层。
   - 配置项从环境变量读取，对应 .env.example。
   - 提供这些最小接口：
     - GET /api/health
     - GET /api/chain/health
     - POST /api/products
     - POST /api/batches
     - POST /api/items/generate
     - POST /api/items/{itemId}/events
     - POST /api/iot/telemetry/mock
     - POST /api/nfc/bind
     - GET /api/public/trace/{traceCode}
     - POST /api/ai/farm/chat
     - POST /api/ai/public/chat
   - FISCO SDK 连接可以先封装接口和 stub 实现；真实证书路径未提供时，系统应能以 disabled/mock 模式启动。

4. 前端
   - 创建 Vue 3 + TypeScript + Vite 工程骨架。
   - 使用 Composition API 和 <script setup lang="ts">。
   - 页面至少包含：
     - 登录占位页
     - 管理后台首页
     - 产品/批次管理
     - 单品码管理
     - 传感器监控
     - NFC 绑定
     - 链上交易查看
     - 农场 AI 助手
     - 公众查询页 /t/:traceCode
   - 后台界面偏高密度业务工具；公众查询页移动端优先。
   - UI 文案必须区分“链上已验证事实”和“AI 建议”。

5. 文档
   - 创建 README.md，面向开发者和未来维护者。
   - README 包含项目定位、技术栈、目录结构、环境准备、敏感信息规则、启动说明、待补 FISCO/MySQL/MQTT 信息。
   - 不在 README 中写真实密码、私钥、证书正文。

五、实现偏好

- 后端优先保持简单清晰，不要过早引入复杂微服务。
- 数据访问层可以使用 MyBatis 或 MyBatis-Plus；若无法确认依赖下载，先保持 SQL 和接口边界清楚。
- 业务事件和传感器窗口摘要必须使用稳定 JSON 规范化后再计算 hash，避免同一业务数据因为字段顺序不同导致 hash 不一致。
- 单品码不要使用可猜测自增号，使用随机高熵 token；数据库和链上以 hash 作为验证核心。
- NFC V1 采用同页查询和 UID/标签ID 绑定，不实现动态签名防克隆芯片逻辑，只预留扩展字段。
- AI provider 使用可替换接口。未配置 AI provider 时，返回明确的 mock/disabled 响应，不影响主流程运行。
- 公有链锚定只实现接口、表和 mock service，不发送真实交易。

六、验收标准

完成后请执行可行的本地检查：

- 列出新增文件。
- 检查文档、.env.example 和配置文件没有真实密码、私钥或证书正文。
- 如果能运行构建，则运行前端 build 和后端 test/package。
- 如果 Maven 或依赖不可用，要明确说明没有运行的原因。
- 确认最小业务闭环在代码结构上成立：
  产品 -> 批次 -> 单品码 -> 事件 -> 公众查询 -> 链上摘要校验接口。

七、交付格式

最终回复请简洁说明：

- 已创建/修改的关键文件。
- 当前可运行能力。
- 未能验证的内容及原因。
- 下一步最值得做的事情。
```

## 可选：分阶段执行提示词

如果一次性生成太多代码，可以把上面的总控提示词拆成以下冲刺：

### Sprint 1：工程骨架

```text
请只完成工程骨架：backend、frontend、contract、database、README。后端和前端允许 mock，不接真实 FISCO、不接真实 MySQL、不启动 EMQX。重点是目录、配置、类型、接口边界和能继续扩展的结构。
```

### Sprint 2：数据库与后端领域模型

```text
请基于 docs/PROJECT_GOAL.md 和 database/schema.sql 完成后端领域模型、DTO、Service、Controller 和统一响应。暂时使用 mock repository 或内存实现也可以，但接口和数据库字段必须对齐。
```

### Sprint 3：FISCO 合约与 SDK 接入

```text
请完成 AgriTrace.sol、合约事件设计、Java SDK 服务封装和 chain health。证书路径、peers、groupId 从环境变量读取；未配置时使用 mock chain service，不能阻塞系统启动。
```

### Sprint 4：Vue 前端体验

```text
请完成 Vue 3 + TypeScript 前端页面骨架和 API client。后台偏管理工具，公众 /t/:traceCode 页面移动端优先，必须展示链上校验状态、NFC 状态、环境摘要和 AI 问答入口。
```

### Sprint 5：IoT、NFC、AI 与验收

```text
请补齐 MQTT mock 接入、传感器窗口摘要、NFC 绑定流程、AI provider 接口、README 启动说明和端到端验收清单。
```
