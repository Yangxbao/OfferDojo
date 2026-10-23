# 架构说明

## 总体结构

前后端分离的两个 Maven/npm 工程，放在同一个仓库：

```
浏览器（Vue 3 SPA）
   │  HTTP：/api/** + Authorization: Bearer <JWT>
   ▼
Vite Dev Server（开发时代理 /api → http://127.0.0.1:8090，生产可换成 Nginx）
   ▼
Spring Boot（8090）
   ├── Controller 层        接收请求、参数校验（@Valid）、组装 VO
   ├── Service 层           业务逻辑、事务边界、权限归属校验
   ├── Mapper 层            MyBatis-Plus：BaseMapper CRUD + 少量统计 SQL
   ├── security 包          JWT 签发/解析、过滤器、401/403 JSON 输出
   └── service/ai 包        AI 评估适配层（Mock / Remote）
   ▼
MySQL 8（或 H2：测试 + 零依赖演示 profile）
```

## 分层职责与调用规则

- **Controller 不写业务**：只做参数校验、调用 Service、包装 `ApiResponse`。Admin 控制器叠加 `@PreAuthorize("hasRole('ADMIN')")`。
- **Service 持有事务边界**：`@Transactional` 放在"提交 + 评估"这样的多表写操作上；远程调用不放进事务（评估是本地 Mock 或一次 HTTP 调用，且评估与提交同库，可同事务）。
- **Mapper 只做数据访问**：复杂统计用 `@Select` 注解 SQL（见 `TaskMapper#selectTaskStats`、`EvaluationMapper#selectTrend`），简单条件查询用 `LambdaQueryWrapper`。
- **DTO 与实体分离**：实体（`entity/`）映射表结构；对外输出用 record DTO（`dto/`），转换在 Service 内完成，避免把 JSON 字符串字段和密码哈希直接暴露给前端。

## 关键设计决策（面试常问）

### 1. 为什么统一 `ApiResponse{code, message, data}`？

全站错误结构一致后，前端 axios 拦截器只需要处理一种约定：HTTP 非 2xx → 弹 `message`；2xx → 直接取 `data`。`GlobalExceptionHandler` 把所有异常（参数校验、业务异常、越权、未捕获异常）翻译成这个结构，Controller 里不出现 try-catch。

### 2. JWT 认证链路

```
登录 → AuthService 校验 BCrypt → JwtService.create(uid, username, role) 签发 token
请求 → JwtAuthenticationFilter 读 Authorization 头
     → 解析成功：构造 UsernamePasswordAuthenticationToken（principal=LoginUser, authorities=ROLE_xxx）
     → 解析失败：静默放行为匿名，由后面的授权规则决定 401/403
授权：/api/admin/** 需 ROLE_ADMIN；其余接口 authenticated
```

要点：过滤器**不抛异常**，把"是否放行"交给 SecurityConfig 的规则链，这样公开接口（登录、Swagger）不需要 token。401/403 由自定义 `AuthenticationEntryPoint` / `AccessDeniedHandler` 输出 JSON，与全站响应结构一致。

### 3. AI 评估链路（`service/ai/`）

```
AiProvider（接口）
 ├── MockAiProvider     默认实现。按任务预设的关键词提示（mock_hints_json）计算"要点覆盖率"→ 确定性评分；
 │                      管理员手工创建的任务没有提示数据时走通用关键词路径
 └ RemoteAiProvider     调 OpenAI 兼容 /chat/completions；JDK HttpClient + 超时控制；
                        强制 JSON 输出并逐字段钳制（分数 0-10、维度必须对齐评分标准、非法任务编码被过滤）
```

- **为什么结构化而不是存一段模型回复**：评估的每个字段（维度得分/亮点/问题/边界/建议/追问/推荐任务）都要被前端分区渲染和统计聚合，存 JSON 字段（`task_evaluation` 表）才能支撑"按维度算薄弱环节"这类功能。
- **降级策略**：主 Provider 为 Remote 时，任何 `AiServiceException`（TIMEOUT / HTTP_ERROR / PARSE_ERROR）→ 记录 `ai_call_log` 失败 → 回退 Mock → 评估记录 `provider=MOCK_FALLBACK` + `notice` 降级说明。用户始终能拿到结果，且**如实知道结果来自哪条链路**。
- **总分计算在后端**：`加权总分 = Σ(维度分×权重)/Σ权重`，由服务端按任务评分标准计算，不信任模型自报的总分。

### 4. 统计为什么实时算而不是预聚合？

当前数据量（个人学习工具）下，实时聚合（几条 GROUP BY + Java 内存聚合 6 个维度）足够快，代码也好懂。`AbilityCalculator` 是纯函数，便于单元测试。数据量大了以后再引入汇总表（见 ROADMAP）。

### 5. 草稿与提交的关系

`task_submission` 一张表用 `status` 区分 DRAFT / EVALUATED。保存草稿 = 同一 (user, task) 的最新 DRAFT 覆盖更新；提交 = 草稿升级为 EVALUATED（没有草稿则新建）。重新提交产生新记录 → 天然保留练习历史。

## 前端结构

- `api/http.ts`：axios 实例 + 拦截器（注入 token、401 清理跳转、统一错误提示）+ `request<T>()` 泛型封装
- `api/types.ts`：与后端 DTO 一一对应的 TypeScript 接口
- `stores/auth.ts`：token/user 状态（localStorage 持久化）
- `router/index.ts`：路由 + 守卫（未登录跳登录、非管理员禁入 /admin、页面标题）
- `components/`：`AppLayout`（侧边导航 + 顶栏）、`EvaluationReport`（评估报告）、`AbilityRadar` / `ScoreTrend`（ECharts 封装）、`TaskCard`
- `views/`：登录/注册/工作台/任务列表/任务详情（作答 + 报告双页签、30 秒自动存草稿）/练习记录/评估报告/能力分析/后台两页

响应式：≤900px 收缩网格，≤720px 侧边栏隐藏改用抽屉导航。
