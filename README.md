# flowable-demo

Spring Boot 3 整合 Flowable 7 的可运行示例：以「员工请假 → 部门经理审批 → 通过 / 驳回」为主线，完整演示 BPMN 2.0 流程定义、自动部署、流程启动、待办查询、审批办理、运行状态与历史轨迹查询。

本项目已在 **Windows 10 / JDK 17.0.12 / Maven 3.9.3 / MySQL 8.0.45** 环境下真实执行 `mvn clean package`、启动应用，并完成 **29 项端到端断言**（全部通过），不是纸上推演。

## 1、项目简介

业务系统通过 REST API 调用嵌入式 Flowable 流程引擎，MySQL 同时保存业务表和引擎的 `ACT_*` 表：

- **流程定义**：`resources/processes/leave-approval.bpmn20.xml`，由 Spring Boot Starter 启动时自动部署；
- **动态审批人**：BPMN 用 `flowable:assignee="${approverId}"`，审批人由启动流程时的变量指定；
- **分支判断**：排他网关按 `approved` 布尔变量走「审批通过」或「审批驳回」两个结束事件；
- **历史追溯**：`history-level: audit` 下可查流程实例、任务轨迹和流程变量；
- **工程化治理**：业务表唯一键保证幂等、统一响应体、全局异常处理把参数/权限错误映射为 4xx 而非 500。

> 相对参考文档额外补强的部分：并发幂等（唯一索引 + 事务）、`startUserId` 写入历史、统一响应与异常处理、历史查询接口。接口路径与核心 Flowable API 用法与文档一致。

## 2、技术栈及版本

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17.0.12 | 运行环境 |
| Spring Boot | 3.5.4 | `spring-boot-starter-parent`，Flowable 7.2.0 官方升级目标版本 |
| Flowable | 7.2.0 | `flowable-spring-boot-starter-process`（传递 `flowable-engine:7.2.0`） |
| MySQL | 8.0.45 | 持久化业务表与 `ACT_*` 引擎表 |
| MySQL Connector/J | 9.3.0 | 由 Spring Boot 3.5.4 BOM 管理，实测可正常连接 MySQL 8.0.45 |
| Maven | 3.9.3 | 构建 |
| 内嵌 Tomcat | 10.1.43 | Spring Boot 3.5.4 自带 |
| 应用端口 | 8080 | 可在 `application.yml` 修改 |
| 可视化页面 | 原生 HTML / CSS / JS | `src/main/resources/static/`，零框架、零构建、同源 fetch |

> 版本依据：Flowable 7.2.0 官方 Release Notes 明确「Upgrade to Spring Boot 3.5.4」；Flowable 8.0.0 明确「Flowable 8 is now based on Spring Framework 7 and Spring Boot 4. There is no support for Spring Boot 3 anymore」。因此 Spring Boot 3 项目应固定 7.2.0，不要引入 8.x。

## 3、快速开始

### 3.1 环境要求

- JDK 17+、Maven 3.8+、MySQL 8.0+（本机 3306 可连）；
- 8080 端口未被占用；
- 具备建库权限的数据库账号。

### 3.2 初始化数据库

用 DBA 账号执行 `db/init.sql`（建库 + 可选的最小权限账号）：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p < db/init.sql
```

```sql
CREATE DATABASE IF NOT EXISTS flowable_demo
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

其余两张表由应用启动时自动创建，无需手工执行：

- `leave_request`（业务表）：`spring.sql.init.mode=always` 执行 `resources/schema.sql`；
- `ACT_*`（引擎表，实测 47 张）：`flowable.database-schema-update=true` 由引擎自动建。

### 3.3 配置连接

`application.yml` 默认使用本机 `root / 123456`，与同目录其它 *-demo 项目一致；也可用环境变量覆盖（推荐）：

```powershell
$env:DB_USER = "flowable_app"
$env:DB_PASSWORD = "你的口令"
```

```yaml
spring:
  datasource:
    username: ${DB_USER:root}
    password: ${DB_PASSWORD:123456}
```

### 3.4 构建与启动

```bash
cd D:\project\flowable-demo
mvn clean package -DskipTests
java -jar target/flowable-demo-1.0.0.jar
```

启动日志中出现以下三行即表示就绪（实测约 6.5 秒）：

```text
o.f.e.impl.db.ProcessDbSchemaManager : performing create on engine ...
o.f.s.c.DefaultAutoDeploymentStrategy : Deploying resources [.../leave-approval.bpmn20.xml] ... SpringBootAutoDeployment
c.e.f.FlowableDemoApplication : Started FlowableDemoApplication in 6.555 seconds
```

### 3.5 验证

以下命令为 Windows CMD 写法（`^` 为续行符）；PowerShell 请改用反引号续行，或直接写成单行。

```bash
# 1. 确认流程定义已自动部署
curl http://localhost:8080/api/process/definitions

# 2. 发起请假申请
curl -X POST http://localhost:8080/api/leave/start ^
  -H "Content-Type: application/json" ^
  -d "{\"businessKey\":\"LR20260001\",\"applicantId\":\"user001\",\"approverId\":\"manager001\",\"days\":3,\"reason\":\"家庭事务\"}"

# 3. 查询审批人待办
curl "http://localhost:8080/api/leave/tasks?userId=manager001"

# 4. 办理审批（替换 {taskId}）
curl -X POST http://localhost:8080/api/leave/tasks/{taskId}/complete ^
  -H "Content-Type: application/json" ^
  -d "{\"operatorId\":\"manager001\",\"approved\":true,\"comment\":\"同意\"}"

# 5. 查看运行状态与历史
curl http://localhost:8080/api/process/instances/{processInstanceId}/status
curl http://localhost:8080/api/process/instances/{processInstanceId}/history/tasks
curl http://localhost:8080/api/process/instances/{processInstanceId}/history/variables
```

成功响应统一为 `ApiResult`：

```json
{"code":0,"message":"ok","data":{"processInstanceId":"ffba19a9-..."},"timestamp":1795136094311}
```

失败响应带 HTTP 状态码与具体原因，例如：

```json
{"code":409,"message":"该业务单号已存在，请勿重复提交","data":null,"timestamp":...}
```

### 3.6 打开请求流程可视化页面

应用启动后，浏览器直接访问：

```text
http://localhost:8080/
```

页面会用一列「请求流水」把「查流程定义 → 发起申请 → 查待办 → 办理审批 → 查状态 → 查轨迹 → 查历史变量」逐条画出来，每张卡片固定展示触发操作、请求地址与方法、请求参数、加载状态、响应结果、错误信息六个环节；支持单步执行、一键串联和 5 个异常场景。

- 逐步操作与截图说明：`docs/请求流程可视化页面操作指南.md`
- 慢速演示（便于观察加载态）：`http://localhost:8080/?slow=1`

## 4、工程结构

```text
flowable-demo/
├── pom.xml                                   # Boot 3.5.4 + Flowable 7.2.0
├── README.md
├── db/init.sql                               # 建库 + 最小权限账号（首次手工执行）
├── docs/                                     # 教程、页面操作指南与截图
│   ├── SpringBoot3整合Flowable实战教程.md
│   ├── 请求流程可视化页面操作指南.md
│   └── images/                               # 逐步操作截图（14 张）
├── tools/
│   └── capture_flow.py                       # Playwright 逐步截图脚本（可复现）
└── src/main/
    ├── java/com/example/flowabledemo/
    │   ├── FlowableDemoApplication.java      # 启动类
    │   ├── common/
    │   │   ├── ApiResult.java                # 统一响应体
    │   │   └── GlobalExceptionHandler.java   # 全局异常 → 4xx
    │   ├── controller/
    │   │   ├── LeaveController.java          # 发起/待办/审批
    │   │   └── ProcessQueryController.java   # 定义/状态/历史
    │   ├── dto/                              # 请求 DTO 与视图 VO（7 个）
    │   ├── repository/
    │   │   └── LeaveRequestRepository.java   # 业务表 JdbcTemplate 访问
    │   └── service/
    │       ├── LeaveWorkflowService.java     # 发起、待办、办理（事务）
    │       ├── ProcessDefinitionService.java # 流程定义查询
    │       └── ProcessQueryService.java      # 运行状态、历史任务、历史变量
    └── resources/
        ├── application.yml
        ├── schema.sql                        # 业务表 DDL（自动执行）
        ├── static/                           # 请求流程可视化页面（零依赖静态资源）
        │   ├── index.html                    # 控制台 + 步骤总览 + 请求流水
        │   ├── app.css
        │   └── app.js                        # fetch 调用 /api，渲染六个环节
        └── processes/
            └── leave-approval.bpmn20.xml     # BPMN 2.0 流程定义（自动部署）
```

## 5、关键配置说明

| 配置 | 值 | 作用 |
| --- | --- | --- |
| `flowable.database-schema-update` | `true` | 开发期允许引擎自动创建/升级 `ACT_*` 表；生产改为 `false` 并走受控升级脚本 |
| `flowable.history-level` | `audit` | 保存流程实例、活动、任务、变量历史；需要更细记录可评估 `full` |
| `flowable.async-executor-activate` | `false` | 示例无定时器/异步节点，先关闭；用到异步 Service Task 时再启用 |
| `flowable.process-definition-location-prefix` | `classpath*:/processes/` | 自动部署扫描目录 |
| `flowable.process-definition-location-suffixes` | `'**.bpmn20.xml,**.bpmn'` | 自动部署文件后缀，YAML 引号必须闭合 |
| `spring.sql.init.mode` | `always` | 每次启动执行 `schema.sql`（`CREATE TABLE IF NOT EXISTS`，可重复） |
| `logging.charset.console` | `UTF-8` | Windows 控制台中文日志不乱码（CMD 另需 `chcp 65001`） |

## 6、接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/process/definitions` | 查询最新版本流程定义（验证自动部署） |
| POST | `/api/leave/start` | 发起请假申请，返回 `processInstanceId` |
| GET | `/api/leave/tasks?userId=` | 查询指定用户的待办任务 |
| POST | `/api/leave/tasks/{taskId}/complete` | 办理审批，`approved` 决定通过/驳回 |
| GET | `/api/process/instances/{id}/status` | 查询是否仍在运行、开始/结束时间、发起人 |
| GET | `/api/process/instances/{id}/history/tasks` | 查询历史任务轨迹 |
| GET | `/api/process/instances/{id}/history/variables` | 查询历史流程变量 |

请求体：

| 接口 | 字段 | 约束 |
| --- | --- | --- |
| `/api/leave/start` | `businessKey` / `applicantId` / `approverId` | 必填，最长 64 |
| | `days` | 1~365 |
| | `reason` | 必填，最长 512 |
| `/complete` | `operatorId` | 必填，必须等于任务 `assignee` |
| | `approved` | boolean，网关分支条件 |
| | `comment` | 可选，最长 512 |

## 7、核心实现说明

- **幂等**：`leave_request.business_key` 建唯一索引，发起流程时「先插业务表、再启动引擎、再回写 `process_instance_id`」三步在同一 `@Transactional` 内。并发重复提交由数据库唯一约束拦截（`DuplicateKeyException` → HTTP 409），比「先查询再启动」的预检查可靠。
- **发起人**：`identityService.setAuthenticatedUserId(applicantId)` 后启动流程，`ACT_HI_PROCINST.START_USER_ID_` 才会记录发起人；用完在 `finally` 中清空 ThreadLocal。
- **权限**：`taskService.createTaskQuery().taskAssignee(operatorId)` 限定只能办理分配给自己的任务；`operatorId` 仅用于教程演示，生产必须从登录态获取。
- **分支**：`taskService.complete(taskId, variables)` 写入 `approved` 变量，引擎随即计算排他网关条件表达式进入对应结束事件。
- **事务**：业务表与 Flowable 共用同一个 `DataSource` / 事务管理器，业务状态与流程状态一起提交或回滚。

## 8、踩坑备忘

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| YAML 启动报扫描不到 BPMN / 解析异常 | `process-definition-location-suffixes` 单引号未闭合（参考文档原稿有此笔误） | 写成 `'**.bpmn20.xml,**.bpmn'`，两端引号都要有 |
| `No process definition ... for key 'leaveApproval'` | BPMN 的 `process id` 与 `startProcessInstanceByKey` 参数不一致，或文件后缀/路径不对 | 核对 `id="leaveApproval"`、`processes/` 目录、部署日志 |
| 审批后网关报「无出线」 | `approved` 变量缺失或不是 Boolean | 完成任务时显式写入 `approved`，检查条件表达式 |
| 历史 `startUserId` 为 null | 未设置认证用户 | 启动前调用 `setAuthenticatedUserId`，用完清理 |
| 参数错误返回 500 | 未做全局异常处理 | `GlobalExceptionHandler` 把校验/业务异常映射为 400/409 |
| 重复提交生成多个流程实例 | 只做「先查再启动」的预检查 | 业务表唯一键 + 事务兜底 |
| `ACT_*` 表结构版本异常 | 引擎版本与库表版本不一致 | 核对 Flowable 版本、Schema 升级脚本，生产用受控升级 |
| Windows 控制台中文乱码 | Logback 跟随系统 GBK 代码页 | `logging.charset.console: UTF-8`，CMD 下 `chcp 65001` |
| 浏览器访问页面后日志刷 `NoResourceFoundException` ERROR 堆栈 | 兜底的 `@ExceptionHandler(Exception.class)` 把静态资源 404 当成服务端故障 | 显式处理 `NoResourceFoundException` 返回 404，并在页面声明内联 favicon |

## 9、生产化建议

1. **关闭自动 DDL**：完成受控初始化/升级后把 `database-schema-update` 设为 `false`；应用账号只保留必要 DML 权限。
2. **身份与权限**：`userId` / `operatorId` 一律从 Spring Security Principal 或 JWT 获取，禁止信任前端传入；查询待办、认领、办理、查看历史都要做数据权限校验。
3. **流程发布治理**：用受控发布替代 classpath 自动部署，记录 BPMN 文件、版本、发布人、审批与回滚预案；流程 Key 保持稳定，已运行实例继续绑定旧版本。
4. **异步与幂等**：使用定时器或异步 Service Task 时启用 `async-executor-activate`，Delegate 必须幂等；外部通知走 Outbox / 异步作业，不要假设数据库事务能回滚外部系统。
5. **历史与数据**：按审计要求评估 `audit` / `full`；待办查询必须分页；流程变量不放超大 JSON、附件二进制、口令或令牌。
6. **多实例部署**：所有实例共享同一数据库，合理配置异步作业锁与时钟同步；监控 `ACT_RU_*`、`ACT_HI_*`、作业积压与连接池。
7. **不要手改引擎表**：业务扩展信息放自己的业务表，通过 `businessKey` / `processInstanceId` / `taskId` 关联。

## 10、请求流程可视化页面

页面是 `src/main/resources/static/` 下的三份静态资源，由 Spring Boot 直接托管，**零前端框架、零构建、零 CDN**，`java -jar` 启动后访问 <http://localhost:8080/> 即可。

它把请假审批的 7 次请求按真实顺序渲染成一列「请求流水」，每张卡片固定展示六个环节：

| 环节 | 展示内容 |
| --- | --- |
| 1 触发操作 | 该步由哪个按钮触发、依赖哪一步的数据 |
| 2 请求地址与方法 | GET / POST 徽标 + 完整 URL（含 Query、路径变量） |
| 3 请求参数 | 实际发送的 JSON 或查询参数 |
| 4 加载状态 | 请求中的进度条，完成后显示耗时 |
| 5 响应结果 | HTTP 状态码 + 格式化 JSON |
| 6 错误信息 | 失败时的红色错误框与 `ApiResult.message` |

交互能力：

- **单步执行**：点任一节点只发该步请求，便于对照接口文档；
- **一键运行**：按 1→7 串行执行，自动传递 `processInstanceId` / `taskId`，并自动更换业务单号避免幂等冲突；
- **异常场景**：内置 5 个按钮，演示参数校验 400、重复单号 409、空 `userId` 400、越权办理 400、实例不存在 400；
- **慢速演示**：`?slow=1` 把每次请求人为延后 1.2 秒，便于观察「加载状态」；
- **网络错误**：把接口基址改成不可达地址，可看到 `Failed to fetch` 与服务端 4xx 的区别。

实现要点（`static/app.js`）：

- `STEPS` 数组声明 7 个步骤的 `path / params / body / after`，`after` 负责把上一步返回值写入上下文；
- 统一的 `execute()` 负责 `fetch` → 计时 → 渲染成功/失败卡片 → 更新步骤状态与汇总；
- JSON 语法高亮用正则分词后逐段上色，不引入任何高亮库；
- 请求全部同源，不需要 CORS 配置，也没有跨域预检。

逐步操作说明与 14 张实测截图见 `docs/请求流程可视化页面操作指南.md`。

## 11、参考资料

- Flowable Open Source 文档：https://www.flowable.com/open-source/docs/
- Flowable Spring Boot 文档：https://www.flowable.com/open-source/docs/bpmn/ch05a-Spring-Boot/
- Flowable GitHub Releases（7.2.0 / 8.0.0 版本依据）：https://github.com/flowable/flowable-engine/releases
- BPMN 2.0 规范：https://www.omg.org/spec/BPMN/2.0/
