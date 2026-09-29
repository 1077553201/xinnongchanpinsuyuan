# 农产品可信溯源 · 前端

当前为**设计稿阶段**：完整工程骨架 + 设计系统已就绪，页面按「公众查询页已完成、管理后台已出总览、其余为版式骨架」推进。

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
```

> 说明：本机 8080 属于系统保留端口段，绑定会报 `EACCES`，因此预览服务固定使用 8090。

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

`VerifySeal.vue` 是全站唯一记忆点。用检验章的语言承载「链上已验证」这一事实：边款环绕系统名，中心一字表明状态，四态以字、色、环线三重区分。

| 状态 | 字 | 色 | 环线 |
| --- | --- | --- | --- |
| `CONFIRMED` | 验 | 田垄绿 | 实线双环 |
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
| `/admin/chain` | 链上交易 | 已接链状态；交易列表待后端补接口 |
| `/admin/products`、`/items`、`/iot`、`/nfc`、`/ai` | 管理页 | 版式骨架 + 实现清单 |

## 与后端对接

- **跨域**：后端未配置 CORS，开发期统一走 Vite dev 代理（`/api` → `http://127.0.0.1:8080`），前端零跨域。生产建议由 Nginx 同源反代。
- **响应解包**：`api/http.ts` 统一解包 `{ code, message, data }`，`code !== '0'` 抛 `ApiError`。
- **数据契约**：
  - 时间格式必须是 `yyyy-MM-dd HH:mm:ss`（GMT+8），`LocalDate` 为 `yyyy-MM-dd`
  - 后端 `default-property-inclusion: non_null`，不返回空字段，前端取值需做缺省处理
  - `chain_status` 四态：`PENDING` / `CONFIRMED` / `FAILED` / `CHAIN_DISABLED`
- **布局陷阱**：公共页容器限宽 520px，栅格必须用**容器查询**（`@container`）而非视口媒体查询，否则桌面端会误入多列布局被挤窄。

## 已知缺口（待后续阶段）

- 登录鉴权：后端无 `AiProvider` 之外的鉴权模块，登录页无法真实校验
- 链上交易列表：后端已写 `chain_transaction` 但没有查询接口，需要新增
  `GET /api/chain/transactions?businessType=&businessId=&limit=`
- AI 助手：后端 AI 模块为空，前端只做入口与未启用态
- 其余后台页面：见各页骨架内的实现清单
