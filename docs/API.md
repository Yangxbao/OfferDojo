# API 说明

Base URL：`http://localhost:8090/api`（前端开发通过 Vite 代理 `/api` 访问）。

完整交互式文档：启动后端后打开 **http://localhost:8090/swagger-ui.html**（右上角 Authorize 填入登录返回的 token 即可调试受保护接口）。

## 通用约定

- 所有响应为 `{ "code": 0, "message": "ok", "data": ... }`；`code` 非 0 时与 HTTP 状态码一致（400/401/403/404/409/500）
- 认证：除注册/登录/健康检查/文档外都需要 `Authorization: Bearer <token>`
- 分页：`?page=1&size=10`，size 上限 50；返回 `PageResult{ list, total, page, size }`

## 接口清单

### 认证

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | /auth/register | 注册（用户名字母数字下划线 3-20，密码 ≥6 位） |
| POST | /auth/login | 登录，返回 token + 用户信息 |
| GET | /auth/me | 当前用户 |

```bash
# 登录
curl -X POST http://localhost:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student","password":"student123"}'
```

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "token": "eyJhbGciOiJIUzM4NCJ9...",
    "user": { "id": 2, "username": "student", "nickname": "演示学生", "role": "USER" }
  }
}
```

### 任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /tasks | 任务列表。查询参数：`category`、`difficulty`、`keyword`、`page`、`size`；每项带 `myStatus`（NOT_STARTED/DRAFTING/COMPLETED）与 `myBestScore` |
| GET | /tasks/{id} | 任务详情：完整背景/需求/约束/交付物/验收标准/评分标准 + 我的草稿内容 |

### 提交与评估

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| PUT | /tasks/{taskId}/draft | 保存草稿（覆盖式），body：`{"content": "..."}`，返回草稿 id |
| POST | /tasks/{taskId}/submit | 提交并触发 AI 评估，返回提交详情 + 评估报告 |
| GET | /my/submissions | 我的练习历史（分页） |
| GET | /my/submissions/{id} | 提交详情 + 评估报告（只能查自己的） |

评估报告响应结构（节选，完整示例见 `EvaluationVO`）：

```json
{
  "code": 0,
  "data": {
    "id": 1,
    "status": "EVALUATED",
    "evaluation": {
      "totalScore": 3.5,
      "provider": "MOCK",
      "notice": "本结果由内置模拟评估生成（未配置真实模型），用于演示完整流程，评分仅供参考",
      "dimensionScores": [
        { "dimension": "需求理解", "score": 3.5, "weight": 30, "comment": "证据不足（命中要点 0/7）..." }
      ],
      "strengths": ["..."],
      "problems": ["..."],
      "edgeCases": ["..."],
      "suggestions": ["..."],
      "followUps": ["..."],
      "nextTasks": [{ "id": 2, "code": "DB-001", "title": "..." }]
    }
  }
}
```

### 工作台与能力分析

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /dashboard | 今日任务（含推荐理由）、完成数、能力雷达、最近练习、推荐任务、继续上次草稿 |
| GET | /my/progress | 评分趋势（30 天按天）、能力维度、薄弱环节与建议、分类目完成率 |

### 后台管理（仅 ADMIN，路径规则 + @PreAuthorize 双重校验）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /admin/tasks | 任务列表（含草稿/下架），附练习统计（次数/人数/均分） |
| GET | /admin/tasks/{id} | 编辑回显 |
| POST | /admin/tasks | 新建任务 |
| PUT | /admin/tasks/{id} | 编辑任务 |
| PUT | /admin/tasks/{id}/status?status=OFFLINE | 上架 / 下架 / 转草稿 |
| GET | /admin/overview | 总览：用户数、任务数、评估数、平均分、近 24h AI 失败数 |
| GET | /admin/evaluations | 评估记录（分页，可按任务过滤） |
| GET | /admin/ai-logs | AI 调用日志（`?success=false` 只看失败） |

## 错误响应示例

```json
// 未登录（无 token / token 过期）
HTTP 401
{ "code": 401, "message": "未登录或登录已过期", "data": null }

// 普通用户访问管理接口
HTTP 403
{ "code": 403, "message": "没有权限执行该操作", "data": null }

// 参数校验失败
HTTP 400
{ "code": 400, "message": "答案内容不能为空", "data": null }
```
