# 农产品可信溯源

面向农场管理与消费者查询的单品溯源系统。MySQL 保存业务明细，FISCO BCOS 保存摘要与状态，MQTT 接入设备采样；消费者通过二维码或 NFC 关联入口查询记录。

当前研发重点是稳定已有功能，交付首个可试用版，走通真实传感器、检测车、MQ 网关、数据库、区块链和前后端。执行 [首版交付计划与验收清单](docs/FIRST_TRIAL_RELEASE_PLAN.md)，未完成模块与调研依据见 [缺口报告](docs/UNFINISHED_MODULES_REPORT.md)。软件 QA 不等于实机或田间验收。

## 当前可用能力

- 产品与批次：台账搜索、筛选、分页、新建、详情、关闭和召回。
- 单品：批量生成、二维码本地编码与打印、流转事件录入、公众查询。
- 公众查询：实时读取批次/单品/事件链上摘要核验、事件完整性检查、召回提示和按批次展示环境快照。
- IoT：网关/设备登记、近期读数与趋势、窗口摘要、告警、模拟上报和手动聚合。
- NFC：新包装码逐件 UID 关联、停用留档及回执恢复，公众标签入口实时核验后进入二维码对应的同一产品页；普通 UID 不代表防克隆。详见 [包装 NFC 说明](docs/PACKING_NFC_STAGE.md)。
- 证据对账：按机构集中查看已冻结的生产、流转、质检和 NFC 操作，分页筛选、只读查链并显式补记已有链记录；未知结果不自动重发，原交易回执独立处理。详见 [集中对账说明](docs/EVIDENCE_RECONCILIATION_STAGE.md)。
- 原始回执：按已保存交易哈希核对实际回执、完整原调用与目标事件，显式保存核验结果并防止迟到回调覆盖；缺哈希保持未知，不自动重发。详见 [原始回执说明](docs/TRANSACTION_RECEIPT_STAGE.md)。

- 生产基础：地块登记、种植周期与历史、固定设备绑定和本轮监测；外观可切换晨光绿 / 柔光玻璃。
- 账号权限：真实会话登录，管理员 / 生产人员 / 只读人员，所属机构范围及新生产操作审计。
- 机构管理：管理员可搜索、新建、编辑和停启用机构，查看人员及业务关联，仅无关联空机构可逻辑删除；人员、产品、地块、仓库、订单及设备分配统一机构选项，历史名称保留。详见 [机构管理与关联检查](docs/ORGANIZATION_MANAGEMENT_STAGE.md)。
- 环境与农事：按种植周期配置环境规则并保留版本、安排农事及登记实际投入、记录环境异常处置；待办农事和未处置异常参与结束种植检查。
- 页面布局：桌面全宽管理台、统一侧栏与页头对齐，手机使用右侧导航抽屉；详见 [布局与外部访问说明](docs/FULL_WIDTH_MOBILE_LAYOUT.md)。
- 采收与包装：按种植周期分次采收、记录分级与损耗、完成数量核对，再按单一等级来源记录包装及未包装余量；尚未自动入库或赋码。
- 库存台账：仓库登记、按包装来源分仓分次入库、实际出库及库存损耗，核对完整包装件数、净重和分仓余量；销售出库可回查订单与运输。
- 仓库货物：库存台账顶部切换“按仓库查看”，跨种植周期查看本仓全部在库货物，支持搜索分页、已清空历史和整仓分类汇总，详见 [使用说明](docs/WAREHOUSE_STOCK_VIEW.md)。
- 包装赋码：从采收详情进入具体包装，一件完整包装一码，支持分次生成、数量约束、二维码预览和本页标签打印；扫码可查看原地块至包装的来源快照，赋码与存证分别办理，详见 [实施与升级说明](docs/PACKING_TRACE_CODES_STAGE.md)。
- 生产证据：包装码可独立预览并确认提交原周期至采收时点的规则、农事和环境摘要；公众实时核对批次、单品摘要及归属，展示缺失与截断范围。未知结果先核对不盲发，详见 [生产证据存证说明](docs/PACKING_PRODUCTION_EVIDENCE_STAGE.md)。
- 逐件流转：销售出库可选择具体包装码；运输收货汇总后分别核对逐件实收/拒收，公众只展示本码的关联事实。关联与逐件结果受实际数量约束，详见 [逐件流转说明](docs/ITEM_CIRCULATION_STAGE.md)。
- 流转存证：具体码可独立预览、确认提交及恢复流转版本，保留旧版并引用前版摘要；公众分别核验生产与流转，后补事实不会套用旧版成功结论。详见 [流转证据版本说明](docs/CIRCULATION_EVIDENCE_STAGE.md)。
- 来源质检：采收/包装样本报告与 PDF/PNG/JPG 附件一并留存，原件默认私有；经营方核对后分别公开资料和附件。撤回保留原公开记录并关闭文件公开下载，明确适用来源与未存证状态，详见 [质检报告与附件说明](docs/SOURCE_QUALITY_REPORTS_STAGE.md)。
- 销售订单：多明细、人民币精确金额、分次实际出库、停止未完成部分；销售出库与库存流水同事务保存，尚未接收款。
- 运输与交付：按实际销售出库建立运输单，分别登记实际发运、途中进展和异常、整数实收与拒收结果；拒收不自动退库或退款。

- 日常助手：连续自然对话、私有历史、实时执行轨迹、查询条件与记录明细；相同查询复用结果，需要办理时生成操作单，追问修改后旧单失效。执行仍需逐项确认，目前支持采收和运输事实，详见 [日常助手说明](docs/DAILY_ASSISTANT_STAGE.md)。原手动整理入口保留在“操作单与执行记录”。

后台现在需要登录，公众扫码查询不需要。日常对话需启用模型，本地已通过 MiMo 真实联调；其他 Agent 写入工具仍待扩展。当前面向本地研发与联调，尚未完成生产部署与田间硬件验收。

## 本地启动

环境：Java 11、Maven、Node.js/npm、MySQL 8、FISCO BCOS 3.11；MQTT 可选使用 `deploy/emqx/`。WSL 服务要能通过 Windows 上的实际监听地址/端口访问。

1. 从 `.env.example` 建立本地 `.env`，填写数据库、MQTT 等连接配置；FISCO 证书与私钥保持在忽略的本地目录/配置内。
2. 后端启动会按配置的 `DB_NAME` 自动创建缺失数据库和表，空用户库自动建立“初始农场”和管理员。首次启动前在本地 `.env` 设置 `INIT_ADMIN_USERNAME=admin`、`INIT_ADMIN_PASSWORD=你自己的12至128位密码`；已有用户时不创建或重置账号，无须补初始密码。数据库账号需要建库/建表/建视图权限；无建库权限时先创建数据库并设置 `DB_CREATE_DATABASE=false`。`seed.sql` 不会自动执行，不需要导入演示数据。
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

原阶段对应关系与实施历史见 [下一阶段优化计划](docs/NEXT_OPTIMIZATION_PLAN.md)，当前顺序以首版交付计划为准，暂停默认扩展业务更正、退款等模块。来源赋码、生产/流转证据、逐码质检、NFC、集中对账及原回执已有软件与 QA 基础，真实标签及实地试点仍待验收。报告替代说明见 [质检存证与替代记录](docs/QUALITY_EVIDENCE_STAGE.md)。

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

第十批迁移 `database/migrations/010_packing_trace_codes.sql` 新增包装来源与赋码三张表，可运行 `scripts/ApplyPackingTraceSchema.java`。旧码不自动补来源，新包装码不自动上链；后续生产、流转、质检及 NFC 增强还须依次完成相应迁移。

第十一批迁移 `database/migrations/011_packing_evidence.sql` 新增生产证据和存证任务两张表，可运行 `scripts/ApplyPackingEvidenceSchema.java`。部署本版代码前先升级；确认提交后冻结本版记录，只有实时读链核对一致才显示生产证据摘要一致。

第十二批迁移 `database/migrations/012_item_circulation.sql` 新增包装码出库关联及确认操作两张表，可运行 `scripts/ApplyItemCirculationSchema.java`。部署前升级；关联只补充具体包装身份，不重复扣库存，逐件收货不能超过运输单原始汇总。此阶段流转未上链。

第十三批迁移 `database/migrations/013_circulation_evidence.sql` 新增逐件流转版本表，可运行 `scripts/ApplyCirculationEvidenceSchema.java`。部署本版前升级；快照绑定生产来源、版本及前序摘要，未知结果先核对，不盲目重发。本版复用原合约，不需要重新部署合约。

第十四批迁移 `database/migrations/014_source_quality_reports.sql` 新增来源质检资料与附件两张表，可运行 `scripts/ApplySourceQualitySchema.java`。附件最大 5 MB，原件保存于数据库并随数据库备份；原件与资料摘要不表示已上链或已验证检测机构。部署本版前升级，新包装码不自动继承旧报告表中的模糊批次记录。

第十五批迁移 `database/migrations/015_quality_evidence.sql` 新增报告替代关系与逐码质检版本表，可运行 `scripts/ApplyQualityEvidenceSchema.java`。应先完成第十四批；保留已有报告、原件和生产/流转快照，无需重新部署合约。

第十六批迁移 `database/migrations/016_packing_nfc.sql` 新增包装 NFC 操作历史表，可运行 `scripts/ApplyPackingNfcSchema.java`。先完成此前来源与生产证据迁移；保留既有标签和所有证据，无需重新部署合约。NDEF URL 的实物写入须通过外部工具，系统页面提供同一前端站点的 `/n/{uidHash}` 地址。

第十七批迁移 `database/migrations/017_receipt_verification.sql` 为原交易增加回执核验时间及摘要，可运行 `scripts/ApplyReceiptVerificationSchema.java`，保留原哈希、提交信息和业务事实。核验标记保护已确认留档不被迟到回调覆盖；部署本版前升级，无需重新部署合约。

第十八批迁移 `database/migrations/018_rejection_handling.sql` 新增逐件拒收处置单与事件两张表，可运行 `scripts/ApplyRejectionHandlingSchema.java`。先完成此前迁移；只处理已明确逐件拒收的完整包装，不把隔离收货写成普通入库，不自动退款或发链交易。入口为“退回与隔离”和出库拒收包装码，详见 [拒收处置说明](docs/REJECTION_HANDLING_STAGE.md)。

第十九批迁移 `database/migrations/019_inventory_loss_correction.sql` 新增损耗调整表及当前有效库存视图，可运行 `scripts/ApplyLossCorrectionSchema.java`。部署前完成迁移；损耗单提供影响预览和逐版调整，原流水保留，库存按最新有效损耗统一计算。已有调整后不能单独回退为旧库存查询。详见 [损耗数量更正说明](docs/INVENTORY_LOSS_CORRECTION_STAGE.md)。

第二十批迁移 `database/migrations/020_inventory_quantity_correction.sql` 新增普通入出库调整表并升级当前库存视图，可运行 `scripts/ApplyMovementCorrectionSchema.java`。只开放同仓最近且无销售履约依赖的普通流水；其余提供阻止预览。部署前迁移，之后不得单独重跑第十九批覆盖视图或回退旧库存查询；原流水、损耗调整和证据保留，无需重部署合约。详见 [普通入出库更正说明](docs/ORDINARY_MOVEMENT_CORRECTION_STAGE.md)。

第二十一批迁移 `database/migrations/021_sales_order_replacement.sql` 新增销售订单替代关系表，可运行 `scripts/ApplySalesReplacementSchema.java`。未出库待履约订单支持预览、停止原单并建立替代单，原始资料和关系保留；已出库和交付依赖保持阻止，不退库、不退款、不发链交易。详见 [订单更正与替代说明](docs/SALES_ORDER_REPLACEMENT_STAGE.md)。

第二十二批迁移 `database/migrations/022_sales_dispatch_correction.sql` 新增销售出库更正表并升级当前库存视图，可运行 `scripts/ApplyDispatchCorrectionSchema.java`。仅开放本仓最近、尚无运输安排/逐件绑定/冻结流转证据的销售出库，同步库存及订单剩余数量，原出库保留；后续运输按有效数量读取，归零禁止运输与绑码。部署前迁移，之后不可单独回退旧销售查询或覆盖旧库存视图。详见 [销售出库更正说明](docs/SALES_DISPATCH_CORRECTION_STAGE.md)。

第二十三批迁移 `database/migrations/023_transport_plan_history.sql` 新增运输原计划快照、撤销/替代历史、全历史编码登记与当前计划视图，可运行 `scripts/ApplyTransportPlanSchema.java`。只开放无实际运输事实、逐件绑定或冻结证据的待发运安排，撤销不自动退库，替代保留原档案；迁移前先备份并核对旧有效数量。软件测试及只读联调已完成，新增动作写入验收仍待办理，详见 [运输安排版本说明](docs/TRANSPORT_PLAN_HISTORY_STAGE.md)。
