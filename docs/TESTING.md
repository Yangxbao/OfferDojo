# 测试说明与实际结果

## 测试构成

### 后端（JUnit 5 + Spring Boot Test）

| 测试类 | 类型 | 覆盖内容 |
| --- | --- | --- |
| `security/JwtServiceTest` | 单元 | token 签发-解析往返、非法 token、篡改 token、过期 token |
| `service/ai/MockAiProviderTest` | 单元 | 关键词覆盖率驱动的确定性评分：好答案 > 差答案、结构化字段齐全、低分维度进入问题列表、无提示数据的通用路径 |
| `service/AbilityCalculatorTest` | 单元 | 六维度聚合（均值/次数）、空数据不出错 |
| `FullFlowIntegrationTest` | **集成（8 个用例）** | 完整业务流程：未登录 401 → 注册 → 登录 → /me → 任务列表（≥12）与详情 → 两次保存草稿（覆盖式）→ 提交触发评估（结构化字段断言）→ 工作台与能力分析反映真实数据 → 历史记录与归属校验 → 普通用户访问管理接口 403 → 管理员登录与后台统计 |

集成测试使用 H2 内存库（MySQL 兼容模式）+ 与主代码同一套建表脚本，覆盖真实的 HTTP、Security 过滤器链、MyBatis-Plus、分页与事务，不依赖任何外部服务，任何机器 `mvn test` 直接可跑。

### 前端（Vitest）

| 测试文件 | 覆盖内容 |
| --- | --- |
| `utils/format.test.ts` | 时间格式化、难度/状态/类目/评估来源映射、分数颜色分级 |
| `stores/auth.test.ts` | Pinia 认证状态：未登录、setAuth 后登录态与管理员判断、logout 清理 |

另有 `npm run type-check`（vue-tsc 全量类型检查）与 `npm run build`（生产构建）作为门禁。

## 实际运行结果

在本机（Windows 11，JDK 21 = JetBrains Runtime 21.0.11，Maven 3.9.4，Node 24）实际执行：

```text
后端：mvn test
  Tests run: 18, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS

前端：npx vue-tsc --noEmit
  （无错误输出，退出码 0）

前端：npm test
  Test Files  2 passed (2)
  Tests       6 passed (6)

前端：npm run build
  ✓ built in 12.20s
```

CI（GitHub Actions）在每次 push / PR 时运行相同的后端与前端检查，徽章见 README 顶部。

## 真实环境验证（MySQL 模式）

除自动化测试外，还在本机用 MySQL 8.0.34（独立实例，端口 3307）做了手工冒烟验证：

1. 全新数据库启动 → 自动建 5 张表 + 写入演示账号与 52 个任务（题库文件 `demo/demo-tasks*.json`，按编码增量播种）
2. 学生账号登录 → 浏览任务 → 提交答案 → 返回结构化评估（总分 3.5，维度/问题/边界/建议/追问/下一步任务齐全，来源标注 MOCK）
3. 补交 3 份高质量答案 → 评分 8.5 / 9.4 / 9.5，工作台累计完成 4/12，能力雷达与薄弱环节随之变化
4. 学生访问 `/api/admin/overview` → 403；管理员登录 → 总览统计正确
5. 浏览器实际走通登录 → 工作台 → 任务中心 → 评估报告 → 能力分析 → 后台页面，README 截图均来自此次真实运行

## 运行测试时发现并修复的问题（真实记录）

| 问题 | 现象 | 根因 | 修复 |
| --- | --- | --- | --- |
| 编译错误 ×2 | `PageResult` 引用不存在的包；DTO 缺 import | 笔误 | 删除无用 import / 补 `java.time.LocalDateTime` |
| 集成测试上下文启动失败 | `Could not resolve placeholder 'jobsim.jwt.secret'` | 测试的 application.yml 整体覆盖主配置，缺 jobsim 配置 | 测试配置补全所需配置项 |
| 任务列表断言失败 | 一页只有 10 条，断言 ≥12 | 默认分页大小为 10 | 测试显式 `size=20` |
| MySQL 模式缺 `sys_user` 表 | 其余 4 张表正常，唯一约束表缺失 | SQL 脚本头部注释用了 `#` 前缀，Spring 脚本解析将"注释头+第一条建表语句"合并，MySQL 将整条视为 `#` 行注释静默跳过 | 注释统一改为 `-- ` 前缀；同时为 `spring.sql.init` 显式指定 `encoding: UTF-8`（Windows 默认 GBK 有多字节风险） |
| 浏览器登录 403 | curl 正常、浏览器被拒 | Vite 端口被占自动改用 5174，CORS 白名单只放行了 5173 | 改用 origin pattern `http://localhost:*` |
| 前端测试文件报 TS2307 | 找不到模块 | 测试文件相对路径写错（`../auth` 应为 `./auth`） | 修正路径 |

这些坑都已写入 [TROUBLESHOOTING.md](TROUBLESHOOTING.md)，也是真实的调试记录。
