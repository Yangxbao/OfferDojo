# 学习指南（写给要读懂并修改这个项目的你）

这个项目刻意保持了"一个实习生能全部读完"的规模。下面按推荐阅读顺序，把每个目录、关键类和它们之间的调用关系讲清楚。

## 第一站：跑起来再说

1. 按 README「快速开始」用 demo 模式启动（不用装数据库）
2. 浏览器走一遍：登录 → 任务中心 → 打开一个任务 → 提交 → 看评估报告
3. 打开 Swagger（http://localhost:8090/swagger-ui.html）对着接口看请求/响应

带着体感读代码，效率高得多。

## 第二站：一次请求的完整生命周期（后端主线）

以「登录」为例，跟着这条链读：

```
AuthController.login()                    controller/AuthController.java
  → AuthService.login()                   service/AuthService.java
      → UserMapper.selectOne()            mapper/UserMapper.java（MyBatis-Plus BaseMapper）
      → passwordEncoder.matches()         BCrypt 校验（SecurityConfig 里注册的 Bean）
      → JwtService.create()               security/JwtService.java（jjwt 签发）
  → ApiResponse.ok(...)                   common/ApiResponse.java（统一响应）
```

再以「提交任务」为例（项目里最核心的一条链）：

```
SubmissionController.submit()
  → SubmissionService.submit()                @Transactional 事务边界
      → TaskService.requirePublished()        任务必须已发布
      → 草稿升级为提交（或新建）
      → EvaluationService.evaluate()
          → 构建 EvaluationContext（把任务内容 + 答案打包，Provider 不接触数据库实体）
          → AiProvider.evaluate()             Mock 或 Remote（config/AiProviderConfig 决定）
          → Remote 失败 → 记 ai_call_log → 降级 Mock → provider=MOCK_FALLBACK + notice
          → weightedTotal() 按权重算总分 → 插入 task_evaluation
  → detail() 组装 SubmissionDetailVO 返回
```

**读代码时建议开着两个窗口**：一边是这条链的源码，一边是数据库表（docs/DATABASE.md），对照字段看。

## 第三站：目录逐个说

### backend/src/main/java/com/jobsim/

| 目录 | 内容 | 学习要点 |
| --- | --- | --- |
| `common/` | ApiResponse、ErrorCode、BusinessException、GlobalExceptionHandler、PageResult | 统一响应与异常翻译：为什么 Controller 里没有 try-catch |
| `security/` | JwtService、JwtAuthenticationFilter、LoginUser、SecurityResponses | JWT 无状态认证；过滤器"静默放行匿名"的设计 |
| `config/` | SecurityConfig（规则链 + CORS）、MybatisPlusConfig（分页）、MyMetaObjectHandler（自动填充时间）、OpenApiConfig、AiProviderConfig（Provider 装配与降级选择） | Spring Security 规则链怎么读；Bean 条目式装配 |
| `entity/` | 5 张表的实体 | @TableName/@TableId/@TableField(fill) 的作用 |
| `mapper/` | 5 个 BaseMapper + 少量 @Select 统计 SQL + record 行类型 | 什么时候用 Wrapper、什么时候写 SQL；分页插件如何自动 LIMIT |
| `dto/` | 请求/响应 record | DTO 与实体分离的原因；容器元素校验 `List<@NotBlank String>` |
| `service/` | 业务逻辑。`AbilityCalculator` 是纯函数聚合器 | 事务边界、越权校验（detail 里 userId 比对）、推荐逻辑 |
| `service/ai/` | AiProvider 接口 + Mock/Remote 实现 + EvaluationContext/Result | 策略模式；远程调用超时/错误分类/JSON 钳制；降级链路 |
| `controller/` | 6 个 Controller | @AuthenticationPrincipal 拿当前用户；@PreAuthorize 双保险 |
| `bootstrap/` | DemoDataInitializer | ApplicationRunner 生命周期；幂等初始化（表空才写） |

### frontend/src/

| 目录 | 内容 | 学习要点 |
| --- | --- | --- |
| `api/http.ts` | axios 封装 | 拦截器注入 token、401 统一跳转、`request<T>()` 泛型 |
| `api/types.ts` | 与后端 DTO 一一对应的接口 | 前后端类型契约怎么维护 |
| `stores/auth.ts` | Pinia 认证状态 | state/getters/actions；localStorage 持久化 |
| `router/index.ts` | 路由与守卫 | meta 标记 + beforeEach 守卫的写法 |
| `components/` | AppLayout（布局）、EvaluationReport（评估报告）、AbilityRadar/ScoreTrend（ECharts 封装）、TaskCard | 组件 props 设计；ECharts 生命周期（init/setOption/dispose/ResizeObserver） |
| `views/` | 页面组件 | TaskDetailView 最值得精读：双页签（作答/报告）、草稿自动保存（setInterval + 脏标记）、提交后切页签 |
| `styles/main.css` | 设计规范（颜色/圆角/阴影变量） | CSS 变量做统一主题 |

## 第四站：动手改（由易到难）

1. **改一句文案/颜色**：`styles/main.css` 的 `--jobsim-primary` → 感受设计变量
2. **加一个展示字段**：TaskVO 加"已练习人数"→ 后端 `TaskService.toTaskVO` → `api/types.ts` → `TaskCard.vue`
3. **加一个筛选**：任务列表按 estimatedMinutes 过滤 → `TaskController.page` 加参数 → `taskApi.page` → 下拉框
4. **给 Mock 评分加一条规则**：`MockAiProvider.evaluate`（比如"答案含 SQL 代码块时正确性 +0.5"）→ 跑 `MockAiProviderTest` 验证
5. **加一个新统计**：比如"连续练习天数"→ `SubmissionMapper` 加 @Select → `ProgressService` → `ProgressVO` → `AnalysisView.vue`
6. **接入真实 AI**：`.env` 里配 `AI_PROVIDER=remote` + `AI_API_KEY` + `AI_BASE_URL` + `AI_MODEL`（任何 OpenAI 兼容服务），观察降级日志与 `ai_call_log`

## 常见疑问

**Q：token 存 localStorage 安全吗？**
有 XSS 风险（脚本可读 localStorage），业界常用 HttpOnly Cookie 缓解（但有 CSRF 主题需要处理）。学习项目选 localStorage 是为了把复杂度留在认证本身；这个取舍值得在面试里主动讲。

**Q：为什么评估结果不直接存模型回复的文本？**
前端要分区渲染、统计要按维度聚合，结构化字段（JSON 列）才能支撑；同时服务端钳制分数范围、过滤非法任务编码，不信任模型输出。

**Q：为什么有 `schema-mysql.sql` 和 `schema-h2.sql` 两份？**
生产/开发用 MySQL，测试和 demo 用 H2（零依赖、可在任何 CI 跑）。两份结构一致、都幂等；这是"测试轻量"与"环境一致"之间的折中，等引入 Testcontainers 后可以合并（见 ROADMAP）。

**Q：Lombok 是什么？**
`@Data`/`@RequiredArgsConstructor`/`@Slf4j` 在编译期生成 getter/构造器/logger，减少样板代码。IDE 装 Lombok 插件即可（IDEA 默认内置支持）。
