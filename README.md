# 农产品可信溯源

面向农场管理与消费者查询的单品溯源系统。MySQL 保存业务明细，FISCO BCOS 保存摘要与状态，MQTT 接入设备采样；消费者通过二维码或 NFC 关联入口查询记录。

## 当前可用能力

- 产品与批次：台账搜索、筛选、分页、新建、详情、关闭和召回。
- 单品：批量生成、二维码本地编码与打印、流转事件录入、公众查询。
- 公众查询：实时读取批次/单品/事件链上摘要核验、事件完整性检查、召回提示和按批次展示环境快照。
- IoT：网关/设备登记、近期读数与趋势、窗口摘要、告警、模拟上报和手动聚合。
- NFC：UID 关联、标签查询、停用及链状态反馈；交易列表支持筛选和详情。

- 生产基础：地块登记、种植周期与历史、固定设备绑定和本轮监测；外观可切换晨光绿 / 柔光玻璃。
- 账号权限：真实会话登录，管理员 / 生产人员 / 只读人员，所属机构范围及新生产操作审计。
- 环境与农事：按种植周期配置环境规则并保留版本、安排农事及登记实际投入、记录环境异常处置；待办农事和未处置异常参与结束种植检查。
- 页面布局：桌面全宽管理台、统一侧栏与页头对齐，手机使用右侧导航抽屉；详见 [布局与外部访问说明](docs/FULL_WIDTH_MOBILE_LAYOUT.md)。
- 采收与包装：按种植周期分次采收、记录分级与损耗、完成数量核对，再按单一等级来源记录包装及未包装余量；尚未自动入库或赋码。
- 库存台账：仓库登记、按包装来源分仓分次入库、实际出库及库存损耗，核对完整包装件数、净重和分仓余量；销售出库可回查订单与运输。
- 仓库货物：库存台账顶部切换“按仓库查看”，跨种植周期查看本仓全部在库货物，支持搜索分页、已清空历史和整仓分类汇总，详见 [使用说明](docs/WAREHOUSE_STOCK_VIEW.md)。
- 包装赋码：从采收详情进入具体包装，一件完整包装一码，支持分次生成、数量约束、二维码预览和本页标签打印；扫码可查看原地块至包装的来源快照，赋码与存证分别办理，详见 [实施与升级说明](docs/PACKING_TRACE_CODES_STAGE.md)。
- 生产证据：包装码可独立预览并确认提交原周期至采收时点的规则、农事和环境摘要；公众实时核对批次、单品摘要及归属，展示缺失与截断范围。未知结果先核对不盲发；尚未逐件关联销售运输，详见 [生产证据存证说明](docs/PACKING_PRODUCTION_EVIDENCE_STAGE.md)。
- 销售订单：多明细、人民币精确金额、分次实际出库、停止未完成部分；销售出库与库存流水同事务保存，尚未接收款。
- 运输与交付：按实际销售出库建立运输单，分别登记实际发运、途中进展和异常、整数实收与拒收结果；拒收不自动退库或退款。

- 日常助手：连续自然对话、私有历史、实时执行轨迹、查询条件与记录明细；相同查询复用结果，需要办理时生成操作单，追问修改后旧单失效。执行仍需逐项确认，目前支持采收和运输事实，详见 [日常助手说明](docs/DAILY_ASSISTANT_STAGE.md)。原手动整理入口保留在“操作单与执行记录”。

后台现在需要登录，公众扫码查询不需要。日常对话需启用模型，本地已通过 MiMo 真实联调；其他 Agent 写入工具仍待扩展。当前面向本地研发与联调，尚未完成生产部署与田间硬件验收。

## 本地启动

环境：Java 11、Maven、Node.js/npm、MySQL 8、FISCO BCOS 3.11；MQTT 可选使用 `deploy/emqx/`。WSL 服务要能通过 Windows 上的实际监听地址/端口访问。

1. 从 `.env.example` 建立本地 `.env`，填写数据库、MQTT 等连接配置；FISCO 证书与私钥保持在忽略的本地目录/配置内。
2. 新环境按 `database/schema.sql` 初始化 `agri_trace`。现有库先核对结构；不要为了演示重置数据库。`seed.sql` 是可选示例，不会由启动脚本自动执行。
3. 在仓库根目录启动后端：

```powershell
.\scripts\Start-Backend.ps1 -EnableMqtt
```

脚本将 `.env` 注入本次进程，优先使用 PATH 中的 Maven，再查找用户 Maven Wrapper 缓存；可用 `-MavenPath` 指定路径。`-CheckOnly` 仅检查配置加载与 Maven 定位，不启动服务。若 MQTT 不需要开启，可省略 `-EnableMqtt`，此时按 `.env` 的 `MQTT_ENABLED` 生效。

4. 在另一个终端启动前端：

```powershell
cd frontend
npm install
npm run dev
```

打开管理台 `http://127.0.0.1:5173/admin`。前端 `/api` 经 Vite 代理到后端 8080。修改 `.env` 后重启后端；若从 IDE 启动，需自行注入运行环境变量，Spring Boot 不会自动读取根目录 `.env`。

首次登录与升级见 [身份权限与监测归属](docs/IDENTITY_MONITORING_STAGE.md)。已有库先执行第二批增量迁移；首次管理员由本地脚本初始化，随机密码保存在忽略文件 `.local/initial-admin.json`，登录后可修改。脚本不覆盖已有账号。

开发服务默认监听 `0.0.0.0:5173`，在同一网络的手机或其他电脑上使用 `http://<开发电脑局域网IP>:5173` 访问；`0.0.0.0` 是监听地址，不是浏览器访问地址。API 仍由前端代理到本机后端。外部网络需已有路由转发或隧道及相应防火墙放行，本配置不会自动配置它们。二维码预览可指定手机可达的访问地址，不会改写已存证记录。

## 验证与目录

```powershell
# frontend/
npm test
npm run build
# backend/，在 Maven 可用的终端
mvn test
```

`frontend/` 为 Vue 3/TypeScript 页面；`backend/` 为 Spring Boot/MyBatis/FISCO 适配；`contract/` 为合约与部署说明；`database/` 为 SQL；`docs/FRONTEND_STAGE*.md` 记录各阶段事实与验证边界；`AGENTS.md` 为协作规范。

联调保留了 `QA-S2-*`、`QA-S3-*`、`QA-S5-*`、`QA-GROW-*`、`QA-ID-GROW-*`、`QA-UI-GROW-*`、`QA-HARVEST-*`、`QA-UI-HARVEST-*`、`QA-STOCK-*`、`QA-UI-STOCK-*`、`QA-WH-STOCK-*`、`QA-SALES-*`、`QA-UI-SALES-*`、`QA-TRANSPORT-*`、`QA-UI-TRANSPORT-*`、`QA-AGENT-*`、`QA-UI-AGENT-*` 测试记录，它们不代表真实产品、交易或硬件采集。链上记录不可当作普通测试文件清理；不要删除对应链下证据。

## 尚需独立完成

当前剩余任务与下一轮推进顺序见 [下一阶段优化计划](docs/NEXT_OPTIMIZATION_PLAN.md)：逐项对应原阶段 0–5，区分核心未完成、按试点确定与后续扩展，并列出依赖及验收条件。来源赋码及生产证据存证已具备基础，下一轮优先接入具体包装码的销售出库、运输及收货关联。

业务方向与分期见 [生产到销售及 Agent 规划](docs/PRODUCTION_TO_SALES_AGENT_PLAN.md)。本轮新增地块登记、种植周期开启/结束和历史档案，管理台顶部可切换“晨光绿 / 柔光玻璃”，外观选择保存在本机浏览器。

已有数据库升级时，执行 `database/migrations/001_production_foundation.sql`，仅新增 `farm_plot`、`planting_cycle`，不重新执行示例数据。新环境的 `database/schema.sql` 已包含这两张表。也可在仓库根目录用现有 MySQL JDBC 驱动运行：

```powershell
# 按本机实际安装位置填写 Java 和 MySQL JDBC 驱动路径；连接配置从 .env 读取
& '<Java 11目录>/bin/java.exe' '-Dfile.encoding=UTF-8' -cp '<mysql-connector-java.jar路径>' scripts/ApplyProductionSchema.java
```

实现范围与验证见 [生产基础与主题改造](docs/PRODUCTION_FOUNDATION_STAGE.md)、[身份权限与监测归属](docs/IDENTITY_MONITORING_STAGE.md)。第二批迁移为 `database/migrations/002_identity_monitoring.sql`，新增身份审计和监测来源表及三类角色，不自动重分配历史数据。

第三批迁移 `database/migrations/003_growing_operations.sql` 新增环境规则、样本判断、异常、农事任务和实际执行五张表。已有库完成前两批迁移后再执行；也可使用 `scripts/ApplyGrowingSchema.java`，用法同上述 JDBC 命令。新环境完整结构已同步。功能、数据边界和验证见 [环境规则与农事作业](docs/GROWING_OPERATIONS_STAGE.md)，设备反馈见 [待优化清单](docs/OPTIMIZATION_BACKLOG.md)。

第四批迁移 `database/migrations/004_harvest_packing.sql` 新增采收、分级及包装三张表，也可运行 `scripts/ApplyHarvestSchema.java`；仅新增表，不改写旧批次。详见 [采收与包装阶段记录](docs/HARVEST_PACKING_STAGE.md)。

第五批迁移 `database/migrations/005_inventory_ledger.sql` 新增仓库与库存流水两张表，也可运行 `scripts/ApplyInventorySchema.java`；不把历史包装自动记成已入库。详见 [库存台账阶段记录](docs/INVENTORY_LEDGER_STAGE.md)。

第六批迁移 `database/migrations/006_sales_orders.sql` 新增销售订单、明细与出库关联三张表，也可运行 `scripts/ApplySalesSchema.java`；不自动把旧出库变成销售履约。实现与验证见 [销售订单阶段记录](docs/SALES_ORDERS_STAGE.md)。

第七批迁移 `database/migrations/007_transport.sql` 新增运输单和事件两张表，也可运行 `scripts/ApplyTransportSchema.java`；不自动把旧出库标成已发运。实现、升级和边界见 [运输与交付阶段记录](docs/TRANSPORT_DELIVERY_STAGE.md)。

第八批迁移 `database/migrations/008_agent_workflow.sql` 新增日报、版本、确认和回执四张表，也可运行 `scripts/ApplyAgentSchema.java`；整理与修改草稿不自动执行业务。实现与边界见 [日报与 Agent 首版](docs/AGENT_WORKFLOW_STAGE.md)。

其他 Agent 写入工具与依赖编排仍需按规划实施，运输硬件自动采集、消费者身份签收、销售收款、退货退款、库存预占、调拨、盘点更正和散装销售也尚未接入。设备身份验签、小车任务、动态防克隆 NFC/换绑、全局写入幂等与异步对账、海量数据分页和生产部署也尚未完成。环境范围必须单独按实际依据配置，备注不自动成为规则；待执行农事、未处置环境异常、活动设备绑定和未完成分级核对的采收均参与结束种植检查。未完成机构隔离的旧模块暂限管理员访问。不要把链上摘要核验等同于实物质量或设备身份保证。

第九批迁移 `database/migrations/009_agent_conversations.sql` 新增私有对话和消息两张表，可运行 `scripts/ApplyChatSchema.java`，先完成前八批迁移，不重置旧业务。模型配置见 [日常助手说明](docs/DAILY_ASSISTANT_STAGE.md)。

第十批迁移 `database/migrations/010_packing_trace_codes.sql` 新增包装来源与赋码三张表，可运行 `scripts/ApplyPackingTraceSchema.java`。部署本版代码前先升级；旧码不自动补来源，新包装码不自动上链，NFC 与完整新业务证据后续接入。

第十一批迁移 `database/migrations/011_packing_evidence.sql` 新增生产证据和存证任务两张表，可运行 `scripts/ApplyPackingEvidenceSchema.java`。部署本版代码前先升级；确认提交后冻结本版记录，只有实时读链核对一致才显示生产证据摘要一致。
