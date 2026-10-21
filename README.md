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
- 库存台账：仓库登记、按包装来源分仓分次入库、实际出库及库存损耗，核对完整包装件数、净重和分仓余量；出库尚未关联销售单或运输。

后台现在需要登录，公众扫码查询不需要。AI Agent 执行仍未接入。当前面向本地研发与联调，尚未完成生产部署与田间硬件验收。

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

联调保留了 `QA-S2-*`、`QA-S3-*`、`QA-S5-*`、`QA-GROW-*`、`QA-ID-GROW-*`、`QA-UI-GROW-*`、`QA-HARVEST-*`、`QA-UI-HARVEST-*`、`QA-STOCK-*`、`QA-UI-STOCK-*` 测试记录，它们不代表真实产品或硬件采集。链上记录不可当作普通测试文件清理；不要删除对应链下证据。

## 尚需独立完成

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

销售、运输交付、Agent 确认执行仍需按规划实施，库存退货、调拨、盘点更正和散装销售也尚未接入。设备身份验签、小车任务、动态防克隆 NFC/换绑、全局写入幂等与异步对账、海量数据分页和生产部署也尚未完成。环境范围必须单独按实际依据配置，备注不自动成为规则；待执行农事、未处置环境异常、活动设备绑定和未完成分级核对的采收均参与结束种植检查。未完成机构隔离的旧模块暂限管理员访问。不要把链上摘要核验等同于实物质量或设备身份保证。
