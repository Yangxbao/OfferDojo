# 启动失败排查手册

按"现象 → 原因 → 解决"组织。前几条是本仓库开发过程中真实遇到的坑（见 docs/TESTING.md）。

## 0. 最省事的方式：用仓库自带的一键启动脚本

```cmd
:: 在项目根目录（jobsim-ai）下：
start-backend.cmd      :: 零依赖演示模式（自动探测 JDK 21，无需配置 JAVA_HOME）
start-frontend.cmd     :: 前端（首次自动 npm install）
start-mysql.cmd        :: 可选：启动本机独立 MySQL（3307）并以 MySQL 模式启动后端
```

脚本只在本机探测 JDK 21（含 `C:\Users\<你>\.jdks\` 下 IDEA 下载的 JDK），不会改你的系统环境变量。

## 0.1 后端报 `UnsupportedClassVersionError: class file version 65.0 ... up to 61.0`

**现象**：`mvn spring-boot:run` 报"加载主类时出现 LinkageError"，提示 class file version 65.0 只支持到 61.0。
**原因**：65 = Java 21 编译的类，61 = Java 17 运行时。你的 Maven 正在用 JDK 17 跑 JDK 21 编译的代码。
**解决**：运行 `start-backend.cmd`（自动切到 JDK 21）；或在当前 CMD 窗口临时执行
`set JAVA_HOME=C:\Users\<你>\.jdks\<某个JDK21目录>` 后再 `mvn spring-boot:run`；
长期方案是安装 Temurin 21 并把 JAVA_HOME 指过去。
**对照表**：class file 61=Java 17、65=Java 21。看到 65/61 就一定是 JDK 版本错配。

## 0.2 Windows 批处理三个经典坑（写自己的 .cmd 脚本前必读）

本仓库的启动脚本开发时真实踩过，全部有据可查：

1. **`setlocal` 会连同"当前目录"一起保存，`endlocal` 时一并还原**——
   如果你在 `setlocal` 之后 `cd`，又在 `endlocal` 之后执行命令，目录会被悄悄还原。
   表现：Maven 在错误的目录执行，报 `No plugin found for prefix 'spring-boot'`。
   修法：`endlocal` 之后再 `cd` 一次。
2. **批处理文件必须是 GBK（ANSI）或纯 ASCII，且必须 CRLF 换行**——
   UTF-8 中文注释或 LF 换行会让 cmd 解析错乱（出现"不是内部或外部命令"、`&&` 语法错误等）。
   本仓库脚本保持纯 ASCII + CRLF（`.gitattributes` 强制 `*.cmd eol=crlf`）。
3. **不要在 Maven 模块目录里放 `.mvn/wrapper/maven-wrapper.properties` 再用系统 Maven**——
   本机实测（Maven 3.9.4）：该文件存在时 `mvn spring-boot:run` 的插件前缀解析会失败，
   删除即恢复。因此本仓库没有内置 Maven Wrapper，直接使用系统 Maven。

## 0.2b H2 报 `Database may be already in use` / `The file is locked`

**现象**：启动报 `JdbcSQLNonTransientConnectionException: Database may be already in use ... The file is locked: ...jobsim-demo.mv.db`。
**原因**：demo 模式的 H2 是文件数据库，同一时刻只允许一个进程打开。上一个后端实例还在运行（窗口没关或后台残留）时再启动就会撞锁。
**解决**：关掉旧的后端窗口（或 `netstat -ano | findstr :8090` 找到 PID 后 `taskkill /F /PID <PID>`），再重新启动。仓库已做两层防护：demo 连接串启用了 `AUTO_SERVER=TRUE`（误开双实例时自动共享而不是报错），启动脚本也会预检 8090 端口并给出明确提示。

## 0.3 先做三个检查

```bash
# 1. 后端起没起来（默认 8090）
curl http://localhost:8090/actuator/health        # 期望 {"status":"UP"}

# 2. 前端 dev server 起没起来（5173 被占会自动用 5174，看启动日志输出哪个）
curl -I http://localhost:5173/

# 3. 端口被谁占了（Windows）
netstat -ano | findstr :8090
tasklist | findstr <PID>
```

## 1. 后端启动报 `Table 'jobsim.sys_user' doesn't exist`

**现象**：日志里 4 张表建了，`sys_user` 没建，启动失败。
**原因**：SQL 脚本头部注释用了 `#` 前缀。Spring 的脚本解析器不认 `#` 注释，把"注释头 + 第一条建表语句"合并成一条语句，而 MySQL 把整条当成 `#` 行注释"静默成功"。
**解决**：脚本注释一律用 `-- `（仓库脚本已修复）。自己写 SQL 初始化脚本时不要用 `#` 注释。

## 2. Windows 下 SQL 脚本执行结果诡异 / 中文乱码

**原因**：Windows 默认编码可能是 GBK（`native.encoding=GBK`），Spring 读取 UTF-8 脚本会错位。
**解决**：`application-*.yml` 已显式设置 `spring.sql.init.encoding: UTF-8`。自己加脚本时保留这项配置。

## 3. 浏览器登录 403，但 curl / Swagger 正常

**原因**：CORS。本项目用 Vite 代理转发 `/api`，理论上无跨域；但如果你**直连**后端端口调试，或 Vite 端口被占自动换到 5174 等端口且你的 CORS 配置只写死了 5173，就会被 Spring 拒绝。
**解决**：仓库默认 `CORS_ALLOWED_ORIGINS=http://localhost:*,http://127.0.0.1:*`（放行任意本地端口）。生产环境请收紧为具体域名。

## 4. 集成测试报 `Could not resolve placeholder 'jobsim.jwt.secret'`

**原因**：`src/test/resources/application.yml` 会**整体覆盖**主配置文件，不是合并。
**解决**：测试配置里补全应用启动所需的全部配置项（仓库已补）。新增配置项时记得同步测试配置。

## 5. 端口冲突

- 后端 8090 被占：启动报 `Port 8090 was already in use`。解决：`netstat -ano | findstr :8090` 找到进程结束，或设置环境变量 `SERVER_PORT=8091` 并同步改 `frontend/vite.config.ts` 里 proxy target。
- 前端 5173 被占：Vite 会自动用 5174（看启动日志），无需处理；但注意浏览器要访问日志里打印的端口。

## 6. MySQL 连接失败

| 报错关键字 | 原因 | 解决 |
| --- | --- | --- |
| `Communications link failure` | MySQL 没启动 / 端口不对 | 确认服务在跑；docker 方式 `docker compose up -d` 后等 healthcheck 通过 |
| `Access denied for user` | 账号密码不对 | 检查 `.env` 的 `MYSQL_USERNAME/MYSQL_PASSWORD` 与数据库实际授权一致 |
| `Unknown database 'jobsim'` | 库没建 | `CREATE DATABASE jobsim DEFAULT CHARACTER SET utf8mb4;`（docker compose 会自动建） |

## 7. 前端页面一片空白 / 接口全 401

- 看 `frontend` 终端有没有报错；`npm run type-check` 过不过
- 401 说明 token 缺失或过期：重新登录；`localStorage.clear()` 后刷新可强制回登录页
- 确认后端在跑且端口与 `vite.config.ts` 的 proxy target 一致

## 8. Maven 依赖下载失败

```bash
# 确认网络可达
curl -I https://repo.maven.apache.org/maven2/

# 公司网络/代理场景：配置 Maven 镜像（阿里云）
# %USERPROFILE%\.m2\settings.xml 的 <mirrors> 添加：
# <mirror><id>aliyun</id><mirrorOf>central</mirrorOf>
#   <url>https://maven.aliyun.com/repository/public</url></mirror>
```

## 9. JDK 版本不对

项目要求 Java 21。`java -version` 低于 21 时：
- IDEA：Project Structure → SDK 选 21（没有就 Download JDK → Temurin 21）
- 命令行：`export JAVA_HOME=<JDK21 路径>`（Windows 设置系统环境变量）

## 10. 首次启动演示数据没出现

`DemoDataInitializer` 对演示账号只在**用户表为空**时写入；对内置任务是**按任务编码增量播种**——题库文件（`demo/demo-tasks*.json`）里新增的题目会在重启时自动补进已有库，已存在的任务不会被改动或重复插入。想彻底重置：清空数据库（或删除 demo 模式下的 `backend/data/` 目录）后重启。
