# 农产品可信溯源 · 前端

产品批次、单品码、二维码预览/打印、事件录入、公众实时链上核验、IoT、NFC 和链交易列表已接入真实接口。AI 与登录页面明确为未启用态。完整运行说明见[仓库 README](../README.md)，阶段记录见 `docs/FRONTEND_STAGE1.md` 至 `FRONTEND_STAGE6.md`。

## 技术栈

Vue 3 + TypeScript + Vite 5 + Vue Router 4 + Pinia + Axios，全部使用 Composition API 与 `<script setup lang="ts">`。

## 启动

```bash
cd frontend
npm install
npm run dev          # 5173，接口代理到后端 8080
```

后端 + MySQL 未启动时，可用内置预览数据服务查看真实版式：

```bash
npm run mock         # 预览数据服务，8090
npm run dev:mock     # 5173，接口代理到 8090
```

其它命令：

```bash
npm run build        # vue-tsc 类型检查 + 生产构建
npm run type-check   # 仅类型检查
npm test             # 状态与校验结论回归测试（Node 内置测试运行器）
```

> 说明：预览数据服务固定使用 8090，避免与后端 8080 冲突；`npm run dev` 始终代理到 8080。

## 目录结构

```text
frontend/
├── mock/preview-server.mjs      # 零依赖预览数据服务（仅开发用）
├── src/
│   ├── api/                     # http.ts（解包 CommonResponse）+ 各模块接口
│   ├── components/              # GlassPanel / StatusChip / VerifySeal / MetricBar / TraceTimeline / AmbientField
│   ├── layouts/AdminLayout.vue  # 后台外壳：玻璃侧栏 + 顶栏
│   ├── router/index.ts
│   ├── styles/                  # tokens.css / base.css / glass.css
│   ├── types/                   # 与后端 entity / VO 对齐的类型
│   └── views/
│       ├── public/TraceView.vue          # /t/:traceCode（已完成）
│       ├── admin/DashboardView.vue       # 总览（接真实接口）
│       ├── admin/ChainExplorerView.vue   # 链上状态（接真实接口）
│       ├── admin/StagePlaceholder.vue    # 其余后台页的版式骨架
│       ├── admin/LoginView.vue           # 登录占位
│       └── NotFoundView.vue
```

## 设计系统 · 晨光玻璃（Morning-Glass）

主题取自农产品检验的真实场景：**温室玻璃、检验玻片、晨光透射**。玻璃只承载功能表面，全场仅保留一处高强度柔光。

### 配色（6 个具名基色）

| Token | 值 | 用途 |
| --- | --- | --- |
| `--ink` | `#0E211B` | 墨绿近黑，正文与深色表面 |
| `--field` | `#1E5B48` | 田垄绿，主品牌、导航、确认态 |
| `--mist` | `#E9EFEA` | 晨雾，浅表面（冷调，非暖米白） |
| `--seal` | `#BE3A2B` | 印泥朱，**唯一高风险色**，仅用于校验印章与告警 |
| `--grain` | `#C79A34` | 稻穗金，数据高亮与待确认态 |
| `--slate` | `#6B7A73` | 石墨，次级文字 |

背景是自上而下的晨光渐层（`--dawn-top/mid/bottom`）加三枚缓慢漂移的柔光光斑（`AmbientField`）。

### 字体（三个角色）

- 显示体 `Noto Serif SC`：标题与产品名，农事历与检验证书的文献感
- 正文字体 `Noto Sans SC`
- 数据字体 `JetBrains Mono`：traceCode、payloadHash、区块号、传感器数值

均带中文本地回退（宋体 / 微软雅黑 / Consolas），外网字体不可达时仍可正常阅读。

### 玻璃基元（`styles/glass.css`）

`.glass` / `.glass-strong` / `.glass-quiet` 三个表面层级，配 `.glass-pad*` 内边距、`.glass-interactive` 悬浮、`.glow-field|warm|seal` 柔光、`.skeleton` 载入骨架。阴影只用两层：一层柔散 + 一层顶部内高光。

### Signature：链上校验印章

`VerifySeal.vue` 用检验章表达存证状态；确认交易不等于实时核验业务明细。公众页以 `assessment` 显示独立的摘要核对结论，标题、印章和明细状态保持一致。

| 状态 | 字 | 色 | 环线 |
| --- | --- | --- | --- |
| `CONFIRMED` | 存 | 田垄绿 | 实线双环 |
| `PENDING` | 待 | 稻穗金 | 虚线环 + 呼吸动效 |
| `FAILED` | 误 | 印泥朱 | 带缺口 |
| `CHAIN_DISABLED` | 未 | 石墨 | 细虚线 |

配套的第二个装置是**农事历双态行**：每条记录左侧链下业务明细、右侧链上证据（状态章 + payloadHash），把「链下明细 / 链上存证」的边界画在版面上。

## 页面与状态

| 路由 | 页面 | 状态 |
| --- | --- | --- |
| `/t/:traceCode` | 公众查询页（移动优先） | 已完成 |
| `/login` | 登录 | 占位（后端无鉴权模块） |
| `/admin` | 总览 | 已接真实接口 |
| `/admin/chain` | 链上交易 | 真实交易筛选、详情及关联业务入口 |
| `/admin/products` | 产品与批次 | 已完成真实创建、关闭、召回及链上状态联调 |
| `/admin/items` | 单品码与事件 | 已实现生成、二维码预览/打印和溯源事件录入 |
| `/admin/iot` | 传感器监控 | 网关/设备登记、读数、趋势、窗口、告警、模拟采样与手动聚合 |
| `/admin/nfc` | NFC 管理 | 标签查询、一次绑定、停用与上链状态 |
| `/admin/ai` | 农场助手 | 明确未启用，无模拟问答 |

## 与后端对接

- **跨域**：后端未配置 CORS，开发期统一走 Vite dev 代理（`/api` → `http://127.0.0.1:8080`），前端零跨域。生产建议由 Nginx 同源反代。
- **响应解包**：`api/http.ts` 统一解包 `{ code, message, data }`，`code !== '0'` 抛 `ApiError`。
- **数据契约**：
  - 时间格式必须是 `yyyy-MM-dd HH:mm:ss`（GMT+8），`LocalDate` 为 `yyyy-MM-dd`
  - 后端 `default-property-inclusion: non_null`，不返回空字段，前端取值需做缺省处理
  - `chain_status` 四态：`PENDING` / `CONFIRMED` / `FAILED` / `CHAIN_DISABLED`
- **总览口径**：产品、批次、单品数量来自现有列表长度；告警为最近最多 6 条记录，包含已处理记录，不代表待处理总数。各查询独立显示加载、错误及重试。
- **验证边界**：公众接口实时读取批次、单品和事件摘要并检查事件数量。只有 `verificationSource=LIVE_CHAIN`、必需记录完整、交易确认、摘要明确一致时显示「链上摘要核验通过」；读取失败、未知、禁用、失败、待确认及不一致均不得显示通过。环境记录仅限当前批次，仍须区别交易确认与逐窗口摘要核验。见 [第 4 阶段](../docs/FRONTEND_STAGE4.md)。
- **连接配置**：Spring Boot 默认不会自动读取仓库根目录 `.env`。从 IDE 启动后端时，需在运行配置中注入 `DB_*` 等变量；更新文件后应重启后端。密码不写入命令行或文档。
- **布局陷阱**：公共页容器限宽 520px，栅格必须用**容器查询**（`@container`）而非视口媒体查询，否则桌面端会误入多列布局被挤窄。

## 已知缺口（待后续阶段）

- 登录鉴权：后端尚无账号校验与权限模块，开发入口不是生产认证。
- AI 助手：尚未接入 Provider，页面只展示真实的未启用状态。
- 设备身份验签、动态防克隆 NFC/换绑、全局写入幂等、异步对账和大数据分页仍需独立完善。
