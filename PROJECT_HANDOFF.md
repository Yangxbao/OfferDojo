# PROJECT_HANDOFF —— 项目进度交接文档

> 写给「接手这个项目的下一个会话 / AI / 开发者」。读完这一份即可无损接手。
> 最后更新：2026-10-24（第三版：含开源学习资源中心）。仓库：https://github.com/Yangxbao/OfferDojo（CI 常绿，HEAD=f7646f0）

## 一、项目是什么

**OfferDojo**（本地文件夹仍叫 `jobsim-ai`，勿改名）：面向大学生的 Java 后端岗位实战模拟练习平台。

核心闭环：**选任务 → 写答案 → AI 结构化评估 → 追问模拟面试 → 错题复盘 → 能力画像 → 简历素材**；外环：**三条方向学习路线 → 知识点 → 开源资源中心 → 实战任务 → 学习计划**。

对外口径：个人全栈学习项目、AI 辅助开发（项目名不含 AI）。所有统计来自真实数据；无 AI Key 全平台可用（MockAiProvider）。

## 二、技术栈（勿随意升降级）

Java 21（本机构建用 `C:\Users\MR\.jdks\jbr-21.0.11`，CI 用 Temurin 21）· Spring Boot 3.5.16 · MyBatis-Plus 3.5.17（+jsqlparser）· springdoc 2.8.17 · jjwt 0.12.6 · H2 2.3.232 / MySQL 8 · Vue 3.5 + Vite 7 + TS 5.8 + Element Plus 2.14 + Pinia 3 + ECharts 5.6 + Vitest 3。后端端口 **8090**，前端 5173（占用自动 5174）。

## 三、路径与启动

项目根：`C:\Users\MR\.zcode\workspace\draft-b89b03c0-bcde-4060-bdbd-b66102ebe73b\jobsim-ai`

```cmd
start-backend.cmd      :: 自动探测 JDK 21，默认 demo 模式（H2 零依赖）
start-frontend.cmd     :: 另开窗口
```

演示账号：admin/admin123（管理员）、student/student123。验证基线：`cd backend && mvn test`（**29/29**）+ `cd frontend && npm test`（6/6）`npm run type-check` `npm run build`。

## 四、功能现状（全部已实现并验证）

1. **任务中心**：220 题（基础43/进阶116/挑战52/企业实战9），13+1 类目（含新增 `ai-app`），题库 `demo/demo-tasks*.json`（8 个文件）按编码增量播种
2. **学习路线图（旧）**：10 阶段（第 1~10 阶段）`demo/roadmap.json`，引用 98 个任务编码
3. **八股复习区**：85 题 `bagu/bagu-*.json`（3 文件），每题带「版本适用性」标注（version_note 列）；自测模式（遮答案自评，localStorage）
4. **AI 评估**：AiProvider 双实现（Mock 关键词覆盖率+引用原句 / Remote OpenAI 兼容+降级 MOCK_FALLBACK）；可追溯（prompt_version+raw_response）
5. **追问模拟面试**：followup_dialog 表 + `/api/my/evaluations/{id}/dialog`，带任务上下文与历史对话
6. **求职闭环**：简历素材生成 + JD 反向匹配（分析页底部卡片）
7. **报告导出 Markdown**、工作台/练习记录/能力分析/后台管理（全部可用）
8. **⭐ 开源学习资源中心（本轮新增）**：
   - 资源目录：12 个真实核验来源（GitHub API 核验默认分支/最近更新/许可证 SPDX），5 维筛选+分页+详情抽屉
   - 三条方向路线：Java 全栈/AI 全栈/Python Agent，26 节点（learning-nodes.json），节点关联资源与实战任务（显示真实提交状态，TaskBrief 含 id 可直达任务页）
   - 个人学习计划：加入/状态流转/心得/移除，用户隔离
   - 管理员：登记（初始 PENDING+权限默认禁止）/编辑/重新核验（GitHubMetadataService，无 Token 可用）/上下架
   - 3 道「知识点转实战」示范任务（FW-033/AI-001/AI-002，标注"平台原创 AI 辅助生成并经人工审核"）
   - 文档：docs/RESOURCES.md（核验记录+许可证规则）

## 五、关键代码位置

- 分层：Controller 不写业务 / Service 持事务与归属校验 / Mapper 只查数；统一响应 `common/ApiResponse`
- 认证：`security/` JWT；`/api/admin/**` 路径拦截 + `@PreAuthorize` 双重防护
- AI：`service/ai/`（AiProvider/Mock/Remote/EvaluationContext/FollowupContext）
- 播种器 `bootstrap/`：DemoData/BaguData/LearningData（均 `@Order(LOWEST)`）+ **SchemaMigrationRunner（`@Order(HIGHEST)` 必须最先跑）**
- 资源中心：`entity/Learning{Resource,Node,Plan}` + `service/Learning{Resource,Track,Plan}Service` + `GitHubMetadataService` + `controller/Learning*Controller`
- 前端：`views/LearningResourcesView.vue`（资源目录）、`views/LearningTracksView.vue`（三路线）、`api/learning.ts`、类型在 `api/types.ts`

## 六、真实踩过的坑（不要重蹈）

1. **Runner 顺序**：按类名字母序执行，迁移器必须在播种器前（@Order 已修）
2. **SQL 脚本**：注释必须 `-- ` 前缀 + UTF-8 + `spring.sql.init.encoding: UTF-8`；**H2 表不用反引号**，schema 修改用脚本文件而非内联 heredoc（曾产生标识符丢失的坏表）
3. **统一响应约定**：BusinessException 走 GlobalExceptionHandler 返回 **HTTP 200 + body.code=403/404**——测试断言 body.code 而非 HTTP 状态码
4. **⭐ 页面可达性验证**：新增前端页面必须确认「路由已注册 + 菜单已挂 + 页面真实可访问」——本轮曾因 Edit 冲突失败未重试，页面写了但没挂进导航，用户看不到（已修，引以为戒）
5. cmd 批处理纯 ASCII+CRLF；`setlocal` 连目录一起保存；H2 文件锁（AUTO_SERVER 已开）；CORS 用 origin pattern
6. **push 网络**：github.com 间歇阻断，失败用 `git -c http.proxy=http://127.0.0.1:7897 push origin main`（用户 Clash 端口）；代理也可能没开，失败先 `gh api repos/Yangxbao/OfferDojo/commits/main --jq .sha` 核对是否实际已推上
7. **不要留运行实例**：验证完杀掉 8090 进程（用户会自己启动）

## 七、用户偏好

诚实第一（不虚报/不造假/做不完写进 ROADMAP）；对外"AI 辅助开发"；中文交流；改动必须：真实运行验证 → 提交推送 → CI 绿 → 汇报附真实验证结果。

## 八、未完成清单（docs/ROADMAP.md + docs/RESOURCES.md）

- 参考解法库+题解对比区（内容工程量大，按高频题分批）
- 错题本 SRS 间隔重复调度；任务↔八股题目级互链（类目级已有）
- 评估人工复核标记、SSE 流式、prompt 外置、Testcontainers、Flyway、refresh token
- 资源中心：自动定时重核验、官方讲义（Spring Guides/MySQL 文档）、NOASSERTION 条款人工审阅（Mnawar83）、平台原创讲义（可放开复制）

## 九、新会话快速上手

```bash
# 1. 读本文档 → 2. 跑基线：backend mvn test（期望 29/29）+ frontend npm test（6/6）
# 3. 体验：start-backend.cmd + start-frontend.cmd（侧边栏应有：工作台/学习路线图/任务中心/八股复习/资源中心/成长路线/练习记录/能力分析）
# 改题库：新增 demo-tasks-08-xxx.json 或 bagu-xxx.json → 重启即增量入库
# 新表：两套 schema 都加（H2 不用反引号）+ 老库补列进 SchemaMigrationRunner + 播种器 @Order(LOWEST)
# 新页面：路由 + 菜单 + 可达性三连验证（见坑 #4）
```
