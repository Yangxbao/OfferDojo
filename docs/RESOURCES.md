# 开源学习资源中心：来源核验与许可证规则

> 本文说明资源中心的来源核验流程、许可证判断规则与管理员操作规范。核验时间：2026-10-24。

## 一、核验方法

所有候选来源通过 **GitHub 官方 REST API**（`repos/{owner}/{repo}` 与 `repos/{owner}/{repo}/license`）核验，记录：

- 默认分支（如 main / master）
- 最近推送时间（pushed_at）
- 许可证 SPDX 标识与 LICENSE 文件路径
- star 数（参考指标，不作质量判断）
- README 描述与方向匹配度

官方文档（如 LangGraph docs）通过 HTTP 状态码核验可访问性。

**无法核实的字段明确标为"待核验"**，不编造数据。

## 二、许可证规则（重要）

| 状态 | 含义 | 展示策略 |
|---|---|---|
| `VERIFIED` + SPDX（如 MIT） | GitHub API 识别出标准许可证 | 可展示元数据+链接；再分发需按许可证保留声明 |
| `NOASSERTION` | 有 LICENSE 文件但 GitHub 无法识别（自定义文本） | 仅展示元数据+链接；权限默认禁止，待人工阅读条款 |
| `UNKNOWN` / 无 LICENSE | 未找到许可证文件 | **仅展示元数据+来源链接**；无许可证=默认版权保护，不得复制内容 |

### 权限字段默认值

所有资源初始状态：`allowCopy=false`、`allowRedistribute=false`、`attributionRequired=true`。

**管理员必须依据可追溯的授权证据才能开放复制/再分发**——证据不足时保持禁止。本平台第一版对所有资源仅展示元数据与来源链接，不复制任何外部内容本体，因此版权风险最小。

### 审核状态流转

```
PENDING（待核验）→ VERIFIED（元数据已核验）→ PUBLISHED（内容审核通过，可展示）
                     ↓
                  OFFLINE（下架：不可访问/失效/违规）
```

- 重新核验（调 GitHub API）成功：自动推进到 `VERIFIED`，但**不自动**到 `PUBLISHED`（需管理员对内容负责）
- 重新核验失败（404/限流/网络错误）：状态不变，记录失败原因
- 无许可证 ≠ 不可参考：阅读与学习不受版权限制，受限制的是复制与再分发

## 三、已核验来源清单（12 个）

### Java 全栈（4 个）

| 编码 | 来源 | 许可证 | 最近更新 | 状态 |
|---|---|---|---|---|
| RES-JAVA-001 | [s4kibs4mi/java-developer-roadmap](https://github.com/s4kibs4mi/java-developer-roadmap) | MIT（LICENSE） | 2026-10-06 | 可展示 |
| RES-JAVA-002 | [Abanoub-Asaad/Java-Backend](https://github.com/Abanoub-Asaad/Java-Backend) | Apache-2.0（LICENSE） | 2026-04-15 | 可展示 |
| RES-JAVA-003 | [aniketmishra-0/Java-FullStack-Roadmap](https://github.com/aniketmishra-0/Java-FullStack-Roadmap) | MIT（LICENSE） | 2026-03-11 | 已核验 |
| RES-JAVA-004 | [TreTre1over/code-roadmap](https://github.com/TreTre1over/code-roadmap) | **无 LICENSE（404）** | 2022-03-01 | 待核验（中文路线，长期未更新） |

### AI 全栈应用开发（5 个）

| 编码 | 来源 | 许可证 | 最近更新 | 状态 |
|---|---|---|---|---|
| RES-AI-001 | [tal7aouy/LLM-Engineering](https://github.com/tal7aouy/LLM-Engineering) | MIT | 2026-09-05 | 可展示 |
| RES-AI-002 | [Mnawar83/Modern-AI-Engineering-Path-2026](https://github.com/Mnawar83/Modern-AI-Engineering-Path-2026) | NOASSERTION（COPYRIGHT.md，自定义条款） | 2026-09-23 | 待核验（条款待人工审阅） |
| RES-AI-003 | [karthikeyanp-dev/ai-engineering-learning](https://github.com/karthikeyanp-dev/ai-engineering-learning) | **无 LICENSE（404）** | 2026-06-10 | 待核验 |
| RES-AI-004 | [partme-ai/spring-ai-examples](https://github.com/partme-ai/spring-ai-examples) | **无 LICENSE（404）** | 2026-08-10 | 待核验（代码参考价值高，再分发未确认） |
| RES-AI-005 | [taniy8/springai-cookbook](https://github.com/taniy8/springai-cookbook) | MIT | 2026-03-09 | 可展示 |

### Python Agent 开发（3 个）

| 编码 | 来源 | 许可证 | 最近更新 | 状态 |
|---|---|---|---|---|
| RES-PY-001 | [AdilShamim8/Agentic-AI-Roadmap...](https://github.com/AdilShamim8/Agentic-AI-Roadmap-with-Notes-and-Projects) | MIT | 2026-10-19 | 可展示 |
| RES-PY-002 | [atryx/ai-engineer-roadmap-2026](https://github.com/atryx/ai-engineer-roadmap-2026) | MIT | 2026-04-15 | 可展示 |
| RES-PY-003 | [LangGraph 官方文档](https://docs.langchain.com/oss/python/langgraph/overview) | 官方文档（使用条款未逐条核验） | 持续更新 | 已核验（链接可用） |

### 未核验字段说明

以下字段因 API 限制或内容工程量原因标注为"待核验"，不虚构：

- 各仓库 README 内**第三方链接**的独立授权（仅引用不复制，风险可控）
- Mnawar83 的 COPYRIGHT.md **具体条款**（是否允许再分发需人工阅读）
- LangGraph 文档站的**使用条款**（官方文档通常允许链接引用）
- Spring AI 示例仓库**内部代码**的逐文件授权（根目录无 LICENSE）

## 四、管理员操作指南

1. **登记新资源**：`POST /api/admin/learning/resources`——初始状态自动为 `PENDING`，权限字段强制默认禁止
2. **重新核验**：`POST /api/admin/learning/resources/{id}/reverify`——调用 GitHub API 刷新元数据；失败时保持原状态并记录原因
3. **上下架**：`PUT /api/admin/learning/resources/{id}/status?status=PUBLISHED|OFFLINE`——可展示须管理员确认内容审核
4. **编辑元数据**：`PUT /api/admin/learning/resources/{id}`——不改变审核状态

管理端同时受 SecurityConfig 路径拦截（`/api/admin/**`）与 `@PreAuthorize("hasRole('ADMIN')")` 双重防护。

## 五、免责与边界

- 本平台只展示资源元数据、事实性描述与原始链接，**不复制外部文章、课程、图片或代码集合**
- star 数仅表示社区关注度，不代表内容质量
- 外部资源内容随时间变化，学习时以原始来源当前版本为准
- 发现资源失效或侵权，请通过 GitHub Issue 报告，将立即下架
