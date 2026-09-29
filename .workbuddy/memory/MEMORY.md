# 项目长期约定

## 构建与测试（重要）

本机 `mvn` 不在 PATH，且 `backend/.mvn/wrapper/maven-wrapper.jar` 缺失。可用本机已存在的 Maven 发行版直接构建：

```bash
export MVN="/c/Users/10775/.m2/wrapper/dists/apache-maven-3.9.16/0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0/bin/mvn"
cd /d/xinnongchanpin/backend
"$MVN" -o test        # 离线，依赖已在 ~/.m2/repository 缓存
"$MVN" compile        # 需要新依赖时用在线模式
```

新增依赖后首次需在线构建一次拉取，之后可离线。

## 代码风格约定

- 后端包名 `org.nongchanpin`，基于 FISCO 官方 spring-boot-crud 示例增量改造。
- 服务：`@Service` + `@RequiredArgsConstructor`（Lombok 构造注入），写操作加 `@Transactional`。
- Mapper：MyBatis 注解式（`@Select/@Insert/@Update`），`@Options(useGeneratedKeys=true, keyProperty="id")`；`map-underscore-to-camel-case` 已开启。
- 实体：`model/entity` 下纯 `@Data` 类。
- 配置：`@Data @Configuration @ConfigurationProperties(prefix="...")`（参照 `ContractConfig`）。
- 控制器：返回 `CommonResponse.ok(...)`，入参 `@Valid @RequestBody`；异常交给 `GlobalExceptionHandler`，业务校验抛 `IllegalArgumentException`。
- 上链模式：`ChainTransactionService.createPending(...)` → `ChainWriteSupport.submit(tx, () -> chainService.xxx(cmd))` → 回写业务表 `chain_status`。
- 哈希：`PayloadService.map()/payloadHashWithPrefix()/requestHash()`；链上只写 bytes32 摘要，不写明文。
- 错误信息用英文，与既有代码一致。

## 安全规则

- 私钥、证书正文、数据库密码不得写入 README、docs 或提交文件，只放本地 `.env`（已 gitignore）。
- `.env`、`*.crt`、`*.key`、`target/`、`node_modules/` 不纳入版本管理。

## 阶段计划

8 阶段：1 合约、2 后端合约适配、3 后端业务服务、4 IoT/MQTT 传感器、5 NFC 防伪、6 Vue 前端、7 AI 助手、8 验收部署。已完成 1-4。每阶段在 `docs/prompts/0X_*.md` 留有派工文档，阶段完成后同步更新 `docs/PREPARATION.md` 与 `docs/prompts/00_ORCHESTRATOR_RULES.md`。
