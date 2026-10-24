# PROJECT_HANDOFF —— 项目进度交接文档

> 本文档写给「接手这个项目的下一个会话/开发者」。读完这一份，即可无损接手。
> 最后更新：2026-10-24。仓库：https://github.com/Yangxbao/OfferDojo （CI 常绿）

## 一、项目是什么

**OfferDojo**（曾用名 jobsim-ai，本地文件夹仍叫 `jobsim-ai`，不影响运行）：面向大学生的 Java 后端岗位实战模拟练习平台。

核心闭环：**选任务 → 写答案 → AI 结构化评估（分维度打分/亮点/问题/边界/建议/追问）→ 追问模拟面试 → 错题复盘 → 能力画像 → 简历素材**。

所有统计来自真实数据；无 AI API Key 时全平台可用（内置 MockAiProvider）；"AI 辅助开发"是对外口径（用户明确要求：项目名不含 AI、介绍写"由仓库所有者在 AI 辅助下完成"）。

## 二、技术栈（版本已核实，勿随意升降）

- Java 21（本机构建用 `C:\Users\MR\.jdks\jbr-21.0.11`，即 JetBrains Runtime 21，自带 javac；CI 用 Temurin 21）
- Spring Boot 3.5.16 / MyBatis-Plus 3.5.17（+ mybatis-plus-jsqlparser）/ springdoc 2.8.17 / jjwt 0.12.6 / H2 2.3.232 / MySQL 8
- Vue 3.5 + Vite 7 + TS 5.8 + Element Plus 2.14 + Pinia 3 + Vue Router 4 + ECharts 5.6 + Vitest 3
- 后端端口 **8090**（8080 被用户机器其他进程占用）；前端 5173（被占自动 5174）

## 三、本地路径与启动

```
项目根：C:\Users\MR\.zcode\workspace\draft-b89b03c0-bcde-4060-bdbd-b66102ebe73b\jobsim-ai
```

启动：项目根下 `start-backend.cmd`（自动探测 JDK 21、默认 demo 模式=H2 零依赖）+ `start-frontend.cmd`；`start-mysql.cmd` 可选（本机 3307 独立 MySQL，MYSQL_BIN 指向 D:\mysql-8.0.34-winx64\bin）。手动方式见 README。

演示账号：admin/admin123（管理员）、student/student123。登录页有一键填入。

验证命令：`cd backend && mvn test`（21/21，含 11 用例全流程集成测试）+ `cd frontend && npm test && npm run type-check && npm run build`（6/6）。

## 四、功能现状（全部已实现并验证）

1. **任务中心**：220 道模拟任务（基础 43/进阶 116/挑战 52/企业实战 EXPERT 9），13 类目，题库为 `backend/src/main/resources/demo/demo-tasks*.json`（11 个文件），**按编码增量播种**（老库重启自动补新题）。
2. **学习路线图**：10 阶段（从第 1 阶段开始！）`demo/roadmap.json`，每阶段=先学知识+按序任务+里程碑，引用 92 个任务编码（改动编码后须校验），进度按「已提交且完成评估」计。
3. **八股复习区**：85 题在 `bagu/bagu-*.json`（3 个文件），增量播种；每题=一句话总结+大白话+要点+示例+追问+**版本适用性标注（version_note）**；前端有自测模式（遮答案自评，存 localStorage `jobsim.bagu.mastery`）。
4. **AI 评估**：AiProvider 接口双实现（Mock=关键词覆盖率确定性评分+引用答案原句；Remote=OpenAI 兼容 /chat/completions，JDK HttpClient，失败自动降级 MOCK_FALLBACK 并留痕）。评估结果结构化入库。
5. **追问模拟面试**：`followup_dialog` 表 + `/api/my/evaluations/{id}/dialog`（GET/POST），FollowupContext 携带任务标题+原答案摘要+历史对话；报告页与记录页有对话组件。
6. **求职闭环**：`/api/my/resume-material`（基于真实练习生成简历草稿）、`/api/jd/match`（JD 文本按标签/关键词命中推荐任务+八股），UI 在能力分析页底部。
7. **评估可追溯**：task_evaluation 有 prompt_version（当前 v1）+ raw_response（Mock=评分明细 JSON / Remote=模型原文）。
8. **报告导出**：前端 `utils/exportReport.ts` 生成 Markdown 下载。
9. **工作台/练习记录/能力分析（雷达+趋势+薄弱环节）/后台管理（任务 CRUD+统计+AI 日志）** 全部可用。
10. 完成文档：README + docs/{ARCHITECTURE,DATABASE,API,TESTING,LEARNING_GUIDE,TROUBLESHOOTING,ROADMAP,INTERVIEW_OUTLINE}.md + 7 张真实截图。

## 五、关键代码位置

- 统一响应/异常：`common/`（ApiResponse、GlobalExceptionHandler）
- 认证：`security/`（JwtService、JwtAuthenticationFilter）+ `config/SecurityConfig`（/api/admin/** 需 ADMIN）
- AI 适配层：`service/ai/`（AiProvider、MockAiProvider、RemoteAiProvider、EvaluationContext/Result、FollowupContext/Feedback）
- 播种器：`bootstrap/`（DemoDataInitializer、BaguDataInitializer、**SchemaMigrationRunner**——三者 @Order 顺序是本项目最重要的坑，见下）
- 路线图：`service/RoadmapService`（读 roadmap.json，按编码解析任务+用户进度）
- 前端页面：`views/`（Dashboard/TaskList/TaskDetail/Bagu/Roadmap/Records/RecordDetail/Analysis/admin/*）

## 六、真实踩过的坑（不要重蹈）

1. **Runner 执行顺序**：ApplicationRunner 默认按类名字母序 → BaguDataInitializer(B) 先于 SchemaMigrationRunner(S) 执行，老库播种时新列未加而炸（用户真实报障）。已用 @Order(HIGHEST_PRECEDENCE/LOWEST_PRECEDENCE) 修复。**给老库加列必须放 SchemaMigrationRunner**。
2. **SQL 脚本注释必须 `-- ` 且文件 UTF-8 + `spring.sql.init.encoding: UTF-8`**：`#` 注释会被 Spring 脚本解析合并进第一条语句，MySQL 静默跳过建表（sys_user 事故）；Windows 默认 GBK 会错位。
3. **cmd 批处理**：保持纯 ASCII + CRLF（.gitattributes 强制 `*.cmd eol=crlf`）；`setlocal` 会连工作目录一起保存，`endlocal` 后要重新 `cd`。
4. **H2 文件锁**：demo 库同一时刻一个进程；连接串已加 `AUTO_SERVER=TRUE`，脚本预检 8090 端口并提示 PID。
5. **CORS**：用 origin pattern `http://localhost:*`（Vite 端口会自动漂移）。
6. **推 GitHub**：用户网络对 github.com 间歇阻断（api.github.com 正常）。push 失败时用 `git -c http.proxy=http://127.0.0.1:7897 push origin main`（用户 Clash 类代理端口 7897，未开则等恢复重试）。代理 push 报错但对象可能已上去，先 `gh api repos/Yangxbao/OfferDojo/commits/main --jq .sha` 核对再重试。
7. **不要留下运行中的实例**：验证完杀掉 8090 进程（用户会自己 start-backend.cmd，端口/H2 会冲突）。当前状态：**8090 空闲，无残留**。

## 七、用户偏好（务必遵守）

- 诚实第一：不虚报功能、不造假数据、做不完的如实写进 docs/ROADMAP.md
- 对外表述："个人全栈学习项目，AI 辅助开发"（不说"AI 写的"，项目名不含 AI）
- 中文交流与文档；给用户的启动操作要"一键化"
- 改动必须：真实运行验证 → 提交推送 → 确认 CI 绿 → 汇报时附真实验证结果

## 八、未完成清单（用户已提出，详见 docs/ROADMAP.md）

- **参考解法库 + 题解对比区**（用户很想要；内容工程量大，建议按高频题分批）
- **错题本 SRS 间隔重复**（八股自评已做，1/3/7 天调度待做）
- 任务↔八股题目级互链（类目级跳转已有）
- 评估人工复核标记、追问对话的收敛策略与终面评语、SSE 流式、prompt 外置、Testcontainers、Flyway（替代 SchemaMigrationRunner）、refresh token

## 九、新会话快速上手

```bash
# 读本文档后，先跑测试确认基线：
cd backend && mvn test        # 期望 21/21
cd frontend && npm test       # 期望 6/6
# 启动体验：项目根 start-backend.cmd + start-frontend.cmd
```

改题库/加题：新增 JSON 文件（demo-tasks-07-xxx.json 或 bagu-xxx.json）→ 重启即增量入库；改完跑 `node` 校验 JSON + `mvn test`。
改功能：遵循现有分层（Controller 不写业务/Service 持事务/Mapper 只查数），新表两份 schema 都要加，老库补列进 SchemaMigrationRunner。
