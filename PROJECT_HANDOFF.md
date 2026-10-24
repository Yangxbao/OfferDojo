# PROJECT_HANDOFF —— 项目进度交接文档

> 写给「接手这个项目的下一个会话 / AI / 开发者」。读完这一份即可无损接手。
> 最后更新：2026-10-24（第四版：三阶段全部完成）。仓库：https://github.com/Yangxbao/OfferDojo（CI 常绿，HEAD=884c53e）

## 一、项目是什么

**OfferDojo**（本地文件夹仍叫 `jobsim-ai`，勿改名）：大学生实战学习平台，已从"Java 后端岗位练习"升级为四位一体：**任务练习 + 八股复习 + 开源资源中心 + 社区共建**。

核心闭环：选任务→AI 结构化评估→追问模拟面试→能力画像→简历素材；外环：三条方向路线→知识点→资源中心（质量分级+GitHub 榜单）→实战任务→学习计划；社区：用户项目投稿展示 + 面试题投稿共建。

对外口径：个人全栈学习项目、AI 辅助开发（项目名不含 AI）。所有统计真实数据；无 AI Key / 无 GitHub Token 全平台可用。

## 二、技术栈（勿随意升降级）

Java 21（本机 `C:\Users\MR\.jdks\jbr-21.0.11`，CI 用 Temurin 21）· Spring Boot 3.5.16 · MyBatis-Plus 3.5.17 · springdoc 2.8.17 · jjwt 0.12.6 · H2 2.3.232 / MySQL 8 · Vue 3.5 + Vite 7 + TS 5.8 + Element Plus 2.14 + Pinia 3 + ECharts 5.6 + Vitest 3。后端 **8090**，前端 5173（占用自动 5174）。

## 三、路径与启动

项目根：`C:\Users\MR\.zcode\workspace\draft-b89b03c0-bcde-4060-bdbd-b66102ebe73b\jobsim-ai`

```cmd
start-backend.cmd      :: 自动探测 JDK 21，demo 模式（H2 零依赖）
start-frontend.cmd     :: 另开窗口
```

演示账号：admin/admin123、student/student123。**验证基线：后端 `mvn test` 38/38；前端 `npm test` 6/6 + type-check + build 全绿**。

## 四、功能全景（全部已实现并真实验证）

1. **任务中心**：220 题（基础43/进阶116/挑战52/企业实战9），14 类目，`demo/demo-tasks*.json`（8 文件）增量播种
2. **学习路线图**：10 阶段（第 1~10）`demo/roadmap.json`；**三条方向路线**（Java 全栈/AI 全栈/Python Agent，26 节点）`learning/learning-nodes.json`
3. **八股复习区**：**125 题** `bagu/bagu-*.json`（4 文件），全部带版本适用性标注+自测模式；**社区题库**（投稿/审核/授权控制/匿名）
4. **AI 评估**：Mock/Remote 双实现+降级；可追溯（prompt_version+raw_response）；**追问模拟面试**
5. **求职闭环**：简历素材+JD 匹配；报告 Markdown 导出
6. **资源中心**：**18 条资源**（12 原有经质量评审+6 官方文档），六维 100 分制质量分级（**精选3/推荐8/普通3/待评估3/下架1**），多维筛选+6 种后端排序，许可证与使用权限分开建模
7. **GitHub 热门榜**：真实 Search API（8 方向），匿名可用，错误分类（401/403/429/超时/连接失败）
8. **项目广场**：用户项目投稿（草稿→审核→发布状态机），Star 由服务端回填无法伪造，URL 白名单
9. 后台管理：任务 CRUD/评估日志/AI 调用日志/资源审核+质量评审/项目审核/投稿审核

## 五、关键代码位置

- 分层：Controller 无业务 / Service 持事务与归属校验 / Mapper 只查数；`common/ApiResponse` 统一响应
- 认证：`security/` JWT；`/api/admin/**` 路径拦截 + `@PreAuthorize` 双重防护
- AI：`service/ai/`（AiProvider/Mock/Remote/Followup）
- GitHub：`service/GitHubMetadataService`（单仓库核验）+ `GitHubSearchService`（榜单检索）；`config/LearningProperties.githubAuthHeader()` **无 Token 返回 null（不发送头）**
- 资源：`entity/Learning{Resource,Node,Plan}` + `service/Learning{Resource,Track,Plan}Service`
- 投稿：`entity/{UserProject,BaguSubmission}` + 对应 Service/Controller（状态机+授权控制）
- 播种器 `bootstrap/`（均 @Order(LOWEST)，SchemaMigrationRunner @Order(HIGHEST) 最先）：Demo/Bagu/Learning/QualityReview 四个
- 前端页面：views/ 下 15+ 页面，菜单在 `components/AppLayout.vue`，路由在 `router/index.ts`

## 六、真实踩过的坑（必须记住）

1. **空 Bearer 401**（第三阶段修复的教训）：`Authorization: Bearer `（空凭据）会被 GitHub 判 BadCredentials；**无 Token 必须完全不发送该头**。已有 LearningPropertiesTest 守护
2. **Runner 顺序**：迁移器必须最先（@Order 已修）；给老库加列进 SchemaMigrationRunner
3. **SQL 脚本**：注释 `-- ` 前缀+UTF-8；H2 表不用反引号；schema 改动用脚本文件（内联 heredoc 会吞标识符）
4. **统一响应**：BusinessException 返回 **HTTP 200 + body.code=403/404**——测试断言 body.code
5. **新页面三连验证**：路由+菜单+可达（第二阶段曾因 Edit 冲突没重试导致页面写了没挂菜单）
6. cmd 纯 ASCII+CRLF；H2 文件锁（AUTO_SERVER 已开）；CORS origin pattern
7. **push 网络**：github.com 间歇阻断，失败用 `git -c http.proxy=http://127.0.0.1:7897 push origin main`；先 `gh api repos/Yangxbao/OfferDojo/commits/main --jq .sha` 核对是否实际已推上
8. 验证完杀掉 8090 实例；不 reset --hard、不 clean -fd

## 七、用户偏好

诚实第一（不虚报/不造假 Star/不虚构评分——第三阶段资源治理就是逐条真实评审的）；对外"AI 辅助开发"；中文；改动必须：真实验证→提交→CI 绿→汇报附验证结果。

## 八、未完成清单（下一会话可接手）

- **管理员评审 UI**：资源质量评审/项目审核/投稿审核的后端接口全部就位，页面待做（当前走 Swagger）
- Star 快照定时采集（7 天增量继续显示"积累中"直到有连续采样）
- Markdown 防 XSS 渲染（DOMPurify）；图片本地上传（现为外链+白名单）
- RES-AI-002 的 COPYRIGHT.md 人工审阅；docs/RESOURCES.md 补 CC 许可再分发合规文案
- 参考解法库、SRS 间隔重复、SSE 流式、Testcontainers、Flyway（见 docs/ROADMAP.md）

## 九、快速上手

```bash
# 1. 读本文档 → 2. 基线：backend mvn test（38/38）+ frontend npm test（6/6）
# 3. 体验：start-backend.cmd + start-frontend.cmd
# 侧边栏应有：工作台/学习路线图/任务中心/八股复习/资源中心/成长路线/GitHub 榜单/项目广场/社区题库/练习记录/能力分析(+admin 2 项)
# 改题库/资源：新增 JSON → 重启增量入库（不覆盖管理员数据）
# 新表：两套 schema（H2 无反引号）+ 老库补列进 SchemaMigrationRunner + 播种器 @Order(LOWEST)
# 新页面：路由+菜单+可达三连验证
```
