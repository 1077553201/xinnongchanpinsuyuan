# 总控制师分阶段派工规则

最后更新：2026-07-07

本文件用于约束后续所有代码生成提示词。每次只下发一个阶段，阶段完成、验收、修正后，再进入下一阶段。

## 总控原则

- 真实代码优先：每个阶段都要产出可维护、可继续接力的真实工程代码，不做只为演示的玩具实现。
- 合约先行：先生成农产品溯源核心智能合约，并由用户手动部署到本地 FISCO BCOS 3.11 节点；后端必须根据合约 ABI、Java wrapper、合约地址和事件定义适配。
- 后端基底：后端使用 FISCO BCOS 官方 Maven SpringBoot 示例 `spring-boot-crud` 作为基础，不从零手写模板；如果后端已经生成，应暂停继续扩展，等待合约产物回填后再适配。
- 一次一阶段：不要让执行 AI 同时铺开所有模块。每个阶段都有清晰输入、输出、验收标准。
- 不绕开计划：执行 AI 必须先读取 `docs/PROJECT_GOAL.md`、`docs/PREPARATION.md`、`.env.example` 和 `.gitignore`。
- 不泄露敏感信息：任何阶段都不得把数据库密码、FISCO 私钥、keystore 密码、证书正文写入仓库。
- 不破坏已有工作：执行 AI 不得删除或重置用户已有文件；遇到冲突时先兼容，无法兼容再说明。
- 外部依赖可降级但不能偷懒：FISCO、MySQL、MQTT、AI provider 未配置时，可以提供明确的 disabled/profile 机制保证工程可启动；但业务边界、接口、配置和适配层必须按真实接入设计。
- 每阶段必须更新文档：阶段完成后要补充 README 或 docs 中对应的运行说明、配置说明和已知限制。

## 阶段顺序

1. 智能合约：生成 `AgriTrace.sol`、接口说明、事件说明、部署清单和后端适配清单。
2. 后端合约适配：基于 FISCO 官方 `spring-boot-crud` 示例和当前已生成后端代码，按真实合约接口改造 ChainService、数据库状态和 API。
3. 后端业务服务：产品、批次、单品、NFC、溯源事件、chain_transaction、公众查询聚合和链上 hash 校验。
4. IoT/MQTT 与传感器数据：EMQX 开发环境、网关鉴权、遥测入库、窗口摘要和异常告警。
5. NFC 与单品防伪查询深化：标签换绑、异常状态、公众查询体验和防伪策略。
6. Vue 3 管理后台与公众查询页：后台业务操作、移动端查询页、链上事实和 AI 建议边界。
7. AI 助手：农场管理助手、消费者助手、会话记录、知识边界、安全限制。
8. 验收与部署：README、部署脚本、测试脚本、演示数据、端到端验收。

## 每阶段交付格式

执行 AI 完成后必须报告：

- 创建/修改的关键文件。
- 已实现的真实能力。
- 运行过的测试或构建命令。
- 未能验证的内容和原因。
- 下一阶段需要的输入。

## 当前阶段入口

第一至第四阶段已经完成（智能合约、后端合约适配、后端业务服务、IoT/MQTT 传感器数据）。当前应执行第五阶段：

```text
docs/prompts/05_NFC_ANTI_COUNTERFEIT.md
```

如果优先做前端，可先执行第六阶段：

```text
docs/prompts/06_VUE_FRONTEND.md
```

## 阶段入口记录

第一阶段使用：

```text
docs/prompts/01_CONTRACT_PHASE_REAL_CODE.md
```

第二阶段使用：

```text
docs/prompts/02_BACKEND_CONTRACT_ADAPTATION.md
```

第三阶段使用：

```text
docs/prompts/03_BACKEND_DOMAIN_SERVICES.md
```

第四阶段使用：

```text
docs/prompts/04_IOT_MQTT_SENSOR.md
```
