# 农产品可信溯源

面向农场管理与消费者查询的单品溯源系统。MySQL 保存业务明细，FISCO BCOS 保存摘要与状态，MQTT 接入设备采样；消费者通过二维码或 NFC 关联入口查询记录。

## 当前可用能力

- 产品与批次：台账搜索、筛选、分页、新建、详情、关闭和召回。
- 单品：批量生成、二维码本地编码与打印、流转事件录入、公众查询。
- 公众查询：实时读取批次/单品/事件链上摘要核验、事件完整性检查、召回提示和按批次展示环境快照。
- IoT：网关/设备登记、近期读数与趋势、窗口摘要、告警、模拟上报和手动聚合。
- NFC：UID 关联、标签查询、停用及链状态反馈；交易列表支持筛选和详情。

登录鉴权和 AI Provider 尚未接入，页面明确为未启用状态。本仓库当前面向本地研发与联调，不应把开发入口当作已完成认证的生产后台。

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
npm run dev -- --host 127.0.0.1 --strictPort
```

打开管理台 `http://127.0.0.1:5173/admin`。前端 `/api` 经 Vite 代理到后端 8080。修改 `.env` 后重启后端；若从 IDE 启动，需自行注入运行环境变量，Spring Boot 不会自动读取根目录 `.env`。

手机扫码需要手机可达的前端地址及网络；默认开发服务仅监听本机。二维码预览可指定访问地址，但不会自动开放网络，也不会改写已存证记录。

## 验证与目录

```powershell
# frontend/
npm test
npm run build
# backend/，在 Maven 可用的终端
mvn test
```

`frontend/` 为 Vue 3/TypeScript 页面；`backend/` 为 Spring Boot/MyBatis/FISCO 适配；`contract/` 为合约与部署说明；`database/` 为 SQL；`docs/FRONTEND_STAGE*.md` 记录各阶段事实与验证边界；`AGENTS.md` 为协作规范。

联调保留了 `QA-S2-*`、`QA-S3-*`、`QA-S5-*` 测试记录，它们不代表真实产品或硬件采集。链上记录不可当作普通测试文件清理；不要删除对应链下证据。

## 尚需独立完成

账号权限与生产部署、AI 服务提供方、设备身份验签、动态防克隆 NFC/换绑、全局写入幂等与异步对账、海量数据的服务端分页。不要把链上摘要核验等同于实物质量或设备身份保证。
