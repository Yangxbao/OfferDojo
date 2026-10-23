# 启动失败排查手册

按"现象 → 原因 → 解决"组织。前四条是本仓库开发过程中真实遇到的坑（见 docs/TESTING.md）。

## 0. 先做三个检查

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

`DemoDataInitializer` 只在**对应表为空**时写入。如果你之前启动过（产生过数据），不会再重复写入。想重置：清空数据库（或删除 demo 模式下的 `backend/data/` 目录）后重启。
