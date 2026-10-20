# 农产品可信溯源后端

本后端基于 FISCO BCOS 官方 `spring-boot-crud` Maven Spring Boot 示例增量改造，当前阶段已按部署后的 `AgriTrace` 合约提供链上 smoke API。

## 当前合约配置

- 合约：`AgriTrace`
- CNS：`AgriTrace 1.0`
- 合约地址：`0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94`
- 默认 groupId：`group0`
- 默认 peer：`127.0.0.1:20200`
- SDK 证书目录：`backend/src/main/resources/conf`

`backend/src/main/resources/application.properties` 读取以下关键配置：

```properties
network.peers[0]=127.0.0.1:20200
cryptoMaterial.certPath=conf
system.groupId=group0
system.hexPrivateKey=${SYSTEM_HEX_PRIVATE_KEY:}
contract.agriTraceAddress=0x0102e8b6fc8cdf9626fddc1c3ea8c1e79b3fce94
```

开发期真实链调用需要本机提供 `SYSTEM_HEX_PRIVATE_KEY`，两种方式任选其一：

1. 在 IDE 运行配置或系统环境变量中设置 `SYSTEM_HEX_PRIVATE_KEY`；
2. 在 `backend/src/main/resources/application-local.properties` 中写 `system.hexPrivateKey=...`。
   该文件已被 `.gitignore` 忽略；本项目默认 profile 为 `local`，会自动覆盖 `application.properties` 中的同名配置。

不要把真实私钥、证书正文、证书密码写入 README、docs 或提交记录。

## 链上 smoke API

- `GET /api/chain/health`
- `POST /api/chain/writers`
- `POST /api/chain/batches`
- `POST /api/chain/batches/{batchCode}/close`
- `POST /api/chain/items`
- `POST /api/chain/nfc-tags`
- `POST /api/chain/nfc-tags/{nfcUid}/status`
- `POST /api/chain/items/{itemCode}/events`
- `POST /api/chain/batches/{batchCode}/sensor-windows`
- `POST /api/chain/items/{itemCode}/status`
- `GET /api/chain/batches/{batchCode}`
- `GET /api/chain/items/{itemCode}`
- `GET /api/chain/nfc-tags/{nfcUid}`
- `GET /api/chain/items/{itemCode}/events/{index}`
- `GET /api/chain/batches/{batchCode}/sensor-windows/{index}`

这些接口只用于合约适配 smoke test。最终业务接口会在后续阶段结合 MySQL 明细、交易表状态和权限模型再封装。

## 业务 API

前端后续阶段已补 `GET /api/nfc`、`GET /api/nfc/{id}`、`GET /api/iot/status`、`GET /api/chain/transactions` 和交易详情接口。根目录 `scripts/Start-Backend.ps1` 会加载本地 `.env`，支持 `-EnableMqtt` 开启真实消费。详见仓库 README 与 `docs/FRONTEND_STAGE5.md`、`FRONTEND_STAGE6.md`。

产品与批次管理补充约定：编码重复返回 HTTP 409（`code: "409"`）；产品/批次请求校验正整数 ID 和字段长度；批次机构必须与产品一致，采收日期不得早于种植日期。已关闭或召回的批次不能再次关闭/召回。摘要序列化支持 Java 日期类型，`LocalDate` 使用稳定的 `yyyy-MM-dd` 文本。

第三阶段新增了业务服务 API，会写入 MySQL 并按动作创建 `chain_transaction`：

- `POST /api/products`
- `GET /api/products`
- `GET /api/products/{id}`
- `POST /api/batches`
- `GET /api/batches`
- `GET /api/batches/{id}`
- `POST /api/batches/{id}/close`
- `POST /api/items/generate`
- `GET /api/items`
- `GET /api/items/{id}`
- `POST /api/nfc/bind`
- `POST /api/nfc/{id}/disable`
- `POST /api/items/{itemId}/events`
- `GET /api/items/{itemId}/events`
- `GET /api/public/trace/{traceCode}`

已串起链状态闭环的动作：

- 创建批次 -> `registerBatch`
- 关闭/召回批次 -> `closeBatch`
- 生成单品码 -> `bindItem`
- 绑定 NFC -> `bindNfcTag`
- 禁用 NFC -> `updateNfcStatus`
- 追加溯源事件 -> `appendTraceEvent`

## IoT 与 MQTT（第四阶段）

传感器数据流：

```text
传感器 -> 边缘网关 -> MQTT(EMQX) -> 后端 IotTelemetryService -> iot_sensor_reading
  -> IotWindowAggregationService 固定窗口聚合 -> iot_data_window -> appendSensorWindow 摘要上链
  -> IotAnomalyService 阈值告警 -> iot_alert
```

新增业务 API：

- `POST /api/iot/telemetry/mock`：按真实 MQTT payload 格式接收遥测，入库并触发聚合与告警。
- `POST /api/iot/gateways` / `GET /api/iot/gateways`：网关登记与查询。
- `POST /api/iot/devices` / `GET /api/iot/devices`：设备登记与查询。
- `GET /api/iot/readings`：原始读数查询，支持 `deviceCode`、`metric`、`limit`。
- `GET /api/iot/windows`：窗口摘要查询，支持 `deviceCode`、`limit`。
- `POST /api/iot/windows/aggregate`：手动触发指定时间段的窗口聚合。
- `GET /api/iot/alerts`：告警查询，支持 `deviceCode`、`limit`。

遥测 payload 字段：`farmId`、`gatewayCode`、`deviceCode`、`metric`、`value`、`unit`、`reportedAt`、`signature`、`batchCode`。其中 `batchCode` 可选，用于把传感器窗口摘要锚定到具体批次。

窗口摘要策略：

- 按 `deviceCode + metric` 以 `trace.iot.window-minutes`（默认 5 分钟）对齐到固定时间桶聚合。
- 摘要包含 `minValue`、`maxValue`、`avgValue`、`sampleCount`，归一化到 6 位小数后计算 canonical `data_hash`，保证同一批数据哈希稳定。
- `data_hash` 唯一索引保证窗口幂等，重复聚合不会产生重复记录。
- 只有窗口关联到批次（`batchCode`）时才调用 `appendSensorWindow` 上链；未关联批次的窗口标记为 `CHAIN_DISABLED`，不伪造已上链。

异常告警：

- 阈值来自 `trace.iot.alerts.{metric}.min/max`，按 metric 名称匹配。
- 越上界生成 `THRESHOLD_HIGH`，越下界生成 `THRESHOLD_LOW`，写入 `iot_alert`，状态为 `OPEN`。

MQTT 接入：

- `trace.mqtt.enabled=true` 时启动 Paho 订阅 `telemetry/status/event` 三类 topic。
- broker 不可达时只记录告警，不影响后端启动。
- 开发环境 EMQX 见 `deploy/emqx/`。

## 数据库配置

默认读取本机 MySQL：

```properties
spring.datasource.url=jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/${DB_NAME:agri_trace}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
public.traceBaseUrl=${PUBLIC_TRACE_BASE_URL:http://127.0.0.1:5173/t}
```

`database/schema.sql` 中的 `trace_item.trace_code_plain` 仅用于开发期公众查询入口和本地闭环，不作为链上参数；链上仍只写 `traceCode` 的 `bytes32` hash。

## 运行与测试

```powershell
cd backend
$env:SYSTEM_HEX_PRIVATE_KEY="replace_with_local_private_key"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

如果未设置 `SYSTEM_HEX_PRIVATE_KEY`，写链接口会返回 `CHAIN_DISABLED`，避免误报已上链；查询和健康检查取决于本地 FISCO 节点和证书是否可用。

当前工作区的 Maven Wrapper 缺少 `.mvn/wrapper/maven-wrapper.jar`，且本机 `mvn` 不在 PATH。恢复 wrapper jar 或安装 Maven 后再执行完整 `mvnw.cmd test`。

## 原始示例说明

# spring-boot-crud

本示例项目基于Java SDK + Maven + SpringBoot方式来调用智能合约。

## 前置条件

搭建FISCO BCOS 单群组区块链（Air版本），具体步骤[参考这里](https://fisco-bcos-doc.readthedocs.io/zh_CN/latest/docs/tutorial/air/build_chain.html) 。

**注意：** 节点rc4版本以后才支持Table的CRUD接口，之前的版本只能用KV接口的功能。

### 获取源码

```shell
# 直接从github克隆代码
git clone https://github.com/FISCO-BCOS/spring-boot-crud.git

# 若网络很慢，可从gitee克隆代码
git clone https://gitee.com/FISCO-BCOS/spring-boot-crud
```

### 配置节点证书

将节点所在目录`nodes/${ip}/sdk`下的ca.crt、sdk.crt和sdk.key文件拷贝到项目的`src/main/resources/conf`目录下供SDK使用(**FISCO BCOS 2.1以前，证书为ca.crt、node.crt和node.key**):

设节点路径为`~/fisco/nodes/127.0.0.1`，则可使用如下命令拷贝SDK证书:

```bash
# 进入项目路径
$ cd spring-boot-crud
# 创建证书存放路径
$ mkdir src/main/resources/conf
# 拷贝SDK证书
$ cp ~/fisco/nodes/127.0.0.1/sdk/* src/main/resources/conf/
```

### 设置配置文件

`spring-boot-crud`包括SDK配置文件（位于`src/main/resources/applicationContext.xml`路径）和WebServer配置文件(位于`src/main/resources/application.yml`路径)。

需要根据区块链节点的IP和端口相应配置`applicationContext.xml`的`network.peers`配置项，具体如下：

```xml
...
<property name="network">
    <map>
        <entry key="peers">
            <list>
                <value>127.0.0.1:20200</value>
                <value>127.0.0.1:20201</value>
            </list>
        </entry>
        <entry key="defaultGroup" value="group0" />
    </map>
</property>
...
```

项目中关于SDK配置的详细说明请[参考这里](https://fisco-bcos-documentation.readthedocs.io/zh_CN/latest/docs/sdk/java_sdk/configuration.html)。

WebServer主要配置了监听端口，默认为`45000`，具体如下：

```yml
server:
  #端口号
  port: 45000
```

### 编译和安装项目

可以使用IDEA导入并编译并安装该项目，也可使用提供的`mvnw`脚本在命令行编译项目如下：

```shell
# 编译项目
$ bash mvnw compile

# 安装项目，安装完毕后，在target/目录下生成fisco-bcos-spring-boot-crud-0.0.1-SNAPSHOT.jar的jar包
$ bash mvnw install
```

### 启动spring-boot-crud服务

**方法一：**

打开IDEA导入并编译该项目，编译成功后，运行`AppApplication.java`即可启动spring boot服务。

**方法二：**

使用`bash mvnw install`生成的jar包`target/fisco-bcos-spring-boot-crud-0.0.1-SNAPSHOT.jar`启动spring-boot-crud服务：

```bash
# 启动spring-boot-crud(启动成功后会输出create client for group success的日志)
$ java -jar ./target/fisco-bcos-spring-boot-crud-0.0.1-SNAPSHOT.jar
```

## 访问spring boot web服务

### 访问用户信息上链API(CRUD insert)

`spring-boot-crud`基于CRUD insert接口实现了用户信息上链的API，将`Person`类型的用户信息上链，API声明如下：

```java
@Data
public class Person {
    private String name;
    private String age;
    private String tel;
}
@PostMapping("/insert")
public ResponseData insert(@RequestBody Person person)  {
    crudClient.insert(person.getName(),person.getAge(), person.getTel());
    return ResponseData.success("新增成功");
}
```

**使用curl工具访问接口如下**：

```bash
# 这里假设WebServer监听端口为45000
# 将用户fisco的信息上链，其中name为fisco, age为6，tel为123456789
$ curl -H "Content-Type: application/json" -X POST --data '{"name":"fisco", "age":"6", "tel":"123456789"}' http://localhost:45000/insert
# 返回新增成功的信息
{"code":200,"msg":"新增成功","data":null}
```

### 访问链上查询用户信息API(CRUD select)

`spring-boot-crud`基于CRUD select接口实现了链上查询用户信息的API，基于用户名查询用户信息，API声明如下：

```java
@GetMapping("/query/{name}")
public ResponseData query(@PathVariable("name") String name) throws Exception {
    return ResponseData.success(crudClient.query(name));
}
```

**使用curl工具访问接口如下**：

```bash
# 这里假设WebServer监听端口为45000
# 查询用户名为fisco的用户信息
$ curl http://localhost:45000/query/fisco
# 返回用户fisco的具体信息
{"code":200,"msg":null,"data":{"value1":"fisco","value2":"6","value3":"123456789","size":3}}
```

### 访问用户信息链上更新API(CRUD update)

`spring-boot-crud`基于CRUD update接口实现了用户信息链上更新的API，API声明如下：

```java
@PutMapping("/update")
public ResponseData update(@RequestBody Person person){
    crudClient.edit(person.getName(),person.getAge(), person.getTel());
    return ResponseData.success("修改成功");
}
```

**使用curl工具访问接口如下**：

```bash
# 这里假设WebServer监听端口为45000
# 更新fisco用户的信息，将其age修改为10，tel修改为123
$ curl -H "Content-Type: application/json" -X PUT --data '{"name":"fisco", "age":"10", "tel":"123"}' http://localhost:45000/update
# 返回成功信息
{"code":200,"msg":"修改成功","data":null}

# 再次查询fisco信息：
$ curl http://localhost:45000/query/fisco
# 返回信息如下，用户fisco的age成功修改为10，tel成功修改为123
{"code":200,"msg":null,"data":{"value1":"fisco","value2":"10","value3":"123","size":3}}
```

### 访问用户信息删除API(CRUD delete)

`spring-boot-crud`基于CRUD delete接口实现了用户信息链上删除的API，该API根据用户名删除用户信息，API声明如下：

```java
@DeleteMapping("/remove/{name}")
public ResponseData remove(@PathVariable("name") String name){
    crudClient.remove(name);
    return ResponseData.success("删除成功");
}
```

**使用curl工具访问接口如下**：

```bash
# 这里假设WebServer监听端口为45000
# 删除用户fisco的信息：
$ curl -X DELETE http://localhost:45000/remove/fisco
# 返回删除成功的提示
{"code":200,"msg":"删除成功","data":null}

# 再次查询fisco信息：
$ curl http://localhost:45000/query/fisco
# 此时已经无法查询到用户fisco的信息
{"code":200,"msg":null,"data":{"value1":"fisco","value2":"","value3":"","size":3}}
```

### 访问用户信息上链API(KV set)

`spring-boot-crud`基于KV set接口实现了用户信息上链的API，将`Person`类型的用户信息上链，API声明如下：

```java
@Data
public class Person {
    private String name;
    private String age;
    private String tel;
}
@PostMapping("/set")
public ResponseData set(@RequestBody Person person) {
    kvClient.set(person.getName(), person.getAge());
    return ResponseData.success("新增成功");
}
```

**使用curl工具访问接口如下**：

```bash
# 这里假设WebServer监听端口为45000
# 将用户fisco的信息上链，其中name为fisco, age为6
$ curl -H "Content-Type: application/json" -X POST --data '{"name":"fisco", "age":"6"}' http://localhost:45000/set
# 返回新增成功的信息
{"code":200,"msg":"新增成功","data":null}
```

### 访问链上查询用户信息API(KV get)

`spring-boot-crud`基于KV get接口实现了链上查询用户信息的API，基于用户名查询用户信息，API声明如下：

```java
@GetMapping("/get/{name}")
public ResponseData get(@PathVariable("name") String name) throws Exception {
    return ResponseData.success(kvClient.get(name));
}
```

**使用curl工具访问接口如下**：

```bash
# 这里假设WebServer监听端口为45000
# 查询用户名为fisco的用户信息
$ curl http://localhost:45000/get/fisco
# 返回用户fisco的具体信息
{"code":200,"msg":null,"data":{"value1":"true","value2":"6","size":2}}
```

## 贡献代码

- 我们欢迎并非常感谢您的贡献，请参阅[代码贡献流程](https://mp.weixin.qq.com/s/hEn2rxqnqp0dF6OKH6Ua-A)和[代码规范](./CONTRIBUTING_CN.md) 。
- 如项目对您有帮助，欢迎star支持！
## 加入我们

**FISCO BCOS开源社区**是国内活跃的开源社区，社区长期为机构和个人开发者提供各类支持与帮助。已有来自各行业的数千名技术爱好者在研究和使用FISCO BCOS。如您对FISCO BCOS开源技术及应用感兴趣，欢迎加入社区获得更多支持与帮助。

![](https://media.githubusercontent.com/media/FISCO-BCOS/LargeFiles/master/images/QR_image.png)

## 相关链接

- 了解FISCO BCOS项目，请参考[FISCO BCOS文档](https://fisco-bcos-doc.readthedocs.io/zh_CN/latest/) 。
- 了解Java SDK项目，请参考[Java SDK文档](https://fisco-bcos-doc.readthedocs.io/zh_CN/latest/docs/develop/sdk/java_sdk/index.html) 。
- 了解spring boot，请参考[Spring Boot官网](https://spring.io/guides/gs/spring-boot/) 。
