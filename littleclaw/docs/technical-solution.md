# littleclaw 技术方案

## 1. 目标与定位

`littleclaw` 的目标不是做一个只会转发模型请求的薄壳接口，而是做一个面向 **高并发 SSE、上下文工程、可扩展工具接入、多 channel 接入** 的 Java 聊天平台骨架。

当前方案重点解决 4 个核心问题：

- **高并发流式输出**：面向 SSE-heavy 场景，优先保证连接稳定性、背压控制、流量整形、连接数控制
- **上下文工程**：把技能、检索结果、MCP 工具、channel 能力统一收敛到 provider 前的上下文装配层
- **能力扩展**：将 provider、RAG、MCP、channel 都设计为清晰边界，避免后期演进时大面积返工
- **生产可演进性**：提前铺设 admission control、tenant quota、请求护栏、错误分层、后续观测能力的接入点

一句话概括：

**littleclaw 是一个面向 OpenClaw 风格演进的、以 WebFlux 为底座的多能力聊天编排内核。**

---

## 2. 总体设计原则

### 2.1 非阻塞优先

由于目标场景包含大量流式连接，方案默认采用：

- `Spring WebFlux`
- `Reactor Netty`
- `Flux/Mono` 驱动的请求处理

这意味着：

- 请求路径上尽量不允许 `block()`
- provider 输出采用流式消费
- admission / quota / stream cleanup 也使用 reactive 方式串联

**重点：同步接口也不能靠内部 `block()` 伪装出来，否则在 SSE-heavy 场景下会把事件循环拖死。**

### 2.2 清晰边界优先于“先跑再说”

当前代码虽然还是 skeleton，但模块边界已经按后续演进方向拆开：

- `api`：HTTP 入口、异常映射
- `chat`：请求编排、plan 构建、SSE 输出
- `provider`：模型适配层
- `skill`：本地 skill 装载与匹配
- `admission`：限流、连接数控制、流式准入
- `context`：上下文装配
- `rag`：检索层
- `mcp`：MCP server/tool 编目边界
- `channel`：多 channel 归一化能力层
- `config`：类型化配置

**重点：provider 只做 transport，context 不应散落在 provider 里拼接。**

### 2.3 先做正确骨架，再补真实能力

当前方案刻意先补“结构正确性”，例如：

- RAG 先用本地文件检索，不急着上向量库
- MCP 先做 registry，不急着做真实 client execution
- channel 先做 normalized metadata + capability registry，不急着立刻做所有 webhook adapter

这样做的好处是：

- 能快速验证编排链路是否合理
- 能避免以后把接口签名和 service 关系推倒重来
- 能让后续真实能力接入时只替换实现，不重写结构

---

## 3. 当前总体架构

## 3.1 请求主链路

当前主链路可以抽象成：

1. 客户端调用 `/v1/chat/completions` 或 `/v1/chat/completions/stream`
2. `ChatController` 接收请求与 tenant header
3. `ChatService` 做输入校验与标准化
4. `SkillRegistry` 基于最新用户消息匹配技能
5. `ContextAssembler` 组装：
   - skill 上下文
   - RAG 检索片段
   - MCP 工具清单
   - channel 能力信息
6. 生成 `ChatPlan`
7. `AdmissionController` 执行准入控制：
   - 请求窗口限流
   - 全局活跃流上限
   - tenant 活跃流上限
8. `ChatEngine` 调用 `ChatProvider`
9. `ChatProvider` 向上游模型或 stub provider 发起流式请求
10. 返回 token 流，最终由 SSE 或 JSON completion 输出给调用方

这个链路的关键不是“能返回结果”，而是：

**在 provider 调用前，所有策略、上下文、准入都已经收口。**

---

## 4. 模块设计细节

## 4.1 API 层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/api/ChatController.java`
- `littleclaw/src/main/java/ai/littleclaw/api/GlobalExceptionHandler.java`

职责：

- 暴露 HTTP API
- 接收 `X-Tenant-Id`
- 路由到 completion 或 streaming
- 将业务异常映射为 HTTP 状态码

当前行为：

- `/v1/chat/completions` 返回 `Mono<ChatResponse>`
- `/v1/chat/completions/stream` 返回 `Flux<ServerSentEvent<?>>`
- admission 拒绝时返回 `429`
- 校验失败返回 `400`

**重点：API 层不做上下文拼接、不做 provider 逻辑，只负责 transport 和协议面。**

### 当前不足

- 已补最小版 auth filter 与 request context filter，但仍缺少生产级 token/tenant/signature 体系
- 尚未实现 channel signature 验证
- 尚未针对不同 channel 输出差异做 renderer

---

## 4.2 Chat 编排层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/chat/ChatService.java`
- `littleclaw/src/main/java/ai/littleclaw/chat/ChatPlan.java`
- `littleclaw/src/main/java/ai/littleclaw/chat/HeuristicChatEngine.java`

职责：

- 请求校验
- 默认值归一化
- skill 匹配
- context assemble
- admission control 串联
- 生成 `ChatPlan`
- 驱动 `ChatEngine`

### ChatPlan 当前承载信息

- `systemPrompt`
- `latestUserMessage`
- `messages`
- `skills`
- `ragSnippets`
- `mcpTools`
- `channel`
- `maxTokens`
- `temperature`

这意味着 `ChatPlan` 已经不只是“聊天输入”，而是一个 **执行前编排产物**。

**重点：`ChatPlan` 是整个系统最重要的内部边界之一。后续策略、审计、回放、缓存都可以围绕它展开。**

### 当前不足

- 还没有 planning / tool-calling 状态机
- 还没有多轮 tool execution 反馈回路
- 还没有 conversation/session persistence
- 还没有 structured trace/span 注入

---

## 4.3 Provider 层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/provider/ChatProvider.java`
- `littleclaw/src/main/java/ai/littleclaw/provider/OpenAiChatProvider.java`
- `littleclaw/src/main/java/ai/littleclaw/provider/StubChatProvider.java`

职责：

- 对接真实模型上游
- 将 `ProviderRequest` 转换为 provider-specific payload
- 以流式方式输出 `ProviderChunk`

### 当前实现

#### StubChatProvider

用于开发验证：

- 输出 channel 名称
- 输出技能命中
- 输出 RAG 命中
- 输出 MCP 工具清单
- 输出 provider 类型

作用：

- 无需真实模型即可验证整个上下文工程链路
- 让测试能覆盖编排逻辑而不是依赖第三方模型

#### OpenAiChatProvider

当前提供：

- OpenAI-compatible endpoint 支持
- `WebClient` + Reactor Netty 非阻塞调用
- SSE `data:` 块解析
- `delta.content` 与 `message.content` 兼容提取
- 基于 `limitRate` 的下游 prefetch 约束

**重点：Provider 层已经从“字符串拼 prompt”收缩为“纯传输适配层”，这对后续维护很重要。**

### 当前不足

- 没有 provider error taxonomy 映射
- 没有重试 / circuit breaker
- 没有 inter-chunk stall 检测
- 没有 upstream timeout 分类
- 没有 provider metrics

---

## 4.4 Skill 层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/skill/SkillLoader.java`
- `littleclaw/src/main/java/ai/littleclaw/skill/SkillRegistry.java`

职责：

- 扫描本地 `SKILL.md`
- 解析 front matter
- 生成 runtime skill model
- 基于关键词做简单匹配

当前价值：

- 能把 OpenClaw-style 技能目录映射到当前 Java 服务里
- 能作为上下文工程的一部分参与 prompt 构建

### 当前不足

- 还没有热更新 watch
- 还没有优先级/作用域机制
- 还没有 skill policy / allowlist / denylist
- 还没有 skill execution runtime

---

## 4.5 Admission / 流量控制层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/admission/InMemoryAdmissionController.java`
- `littleclaw/src/main/java/ai/littleclaw/admission/RedisAdmissionController.java`
- `littleclaw/src/main/java/ai/littleclaw/admission/CompositeAdmissionController.java`

职责：

- 请求级限流
- 流式连接级准入控制
- tenant 粒度配额保护
- 全局活跃流保护

### 当前实现

#### InMemoryAdmissionController

适用于单节点开发环境：

- 固定时间窗限流
- 全局 active streams 计数
- tenant active streams 计数

#### RedisAdmissionController

适用于多副本部署：

- 使用 Lua script 执行原子准入判断
- 限流与连接计数共享到 Redis
- 使用独立 release script 避免流计数释放时失真

**重点：这层已经把“背压 / 流量整形 / 连接数控制”的第一阶段骨架打出来了。**

### 当前不足

- 仍然是 fixed-window，不是 weighted cost shaping
- 还没有区分 completion 与 stream 的成本模型
- 还没有按 prompt size / maxTokens 计费准入
- 还没有 queueing / shedding 策略
- 还没有 per-channel 连接预算

---

## 4.6 Context Engineering 层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/context/ContextAssembler.java`
- `littleclaw/src/main/java/ai/littleclaw/context/ContextEnvelope.java`

职责：

- 把不同来源的上下文统一整合
- 形成 provider-facing system prompt
- 避免上下文逻辑散落在各层

当前装配内容：

- channel metadata / capability
- matched skills
- RAG snippets
- MCP tool inventory

**重点：这层是当前方案最大的“架构抓手”。如果没有它，RAG、MCP、skills、channels 会各自为战。**

### 当前不足

- 还没有 prompt budgeting
- 还没有上下文裁剪策略
- 还没有 relevance scoring aggregation
- 还没有 channel-specific prompt templates

---

## 4.7 RAG 层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/rag/RagService.java`
- `littleclaw/src/main/java/ai/littleclaw/rag/LocalFilesystemRagService.java`

当前方案不是完整向量 RAG，而是：

- 在配置指定目录中扫描文档
- 对 `.md/.txt/.json/.yml/.yaml` 做轻量检索
- 用简单 token 命中分数排序
- 截取片段作为 `RagSnippet`

### 设计意图

- 先验证“检索结果进入上下文工程”的链路
- 避免在基础结构未稳定前过早引入向量库复杂度
- 为后续替换为 embedding/vector 实现预留接口

### 适合当前阶段的点

- 轻量
- 无额外依赖
- 可直接利用项目 docs / memory / skills

### 不适合长期生产的点

- 召回质量有限
- 无 chunk metadata
- 无 embedding similarity
- 无多租户隔离
- 无增量索引

**重点：当前 RAG 是“流程验证版”，不是最终生产版。**

后续建议演进为：

- 文档切块
- metadata 标注（tenant/project/path/type/time）
- embedding 生成
- vector store（如 pgvector / Qdrant / Milvus / LanceDB）
- 混合检索（BM25 + vector）
- citation 输出

---

## 4.8 MCP 层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/mcp/McpRegistry.java`
- `littleclaw/src/main/java/ai/littleclaw/mcp/McpToolDescriptor.java`

当前方案把 MCP 拆成两个阶段：

### 第一阶段：Registry

当前已实现：

- 从配置读取 MCP server 定义
- 暴露 server / transport / tool inventory
- 将其输入到 context assembly

### 第二阶段：Execution

当前尚未实现：

- stdio / http MCP client
- tool invocation
- timeout / retry / cancellation
- tool result injection
- 审计与权限控制

**重点：先做 registry 是为了把“工具可用性”纳入编排，不让工具能力以后硬编码进 provider 或 chat 层。**

---

## 4.9 Multi-channel 能力层

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/channel/ChannelRequest.java`
- `littleclaw/src/main/java/ai/littleclaw/channel/ChannelRegistry.java`
- `littleclaw/src/main/java/ai/littleclaw/channel/ChannelDescriptor.java`
- `littleclaw/src/main/java/ai/littleclaw/channel/ChannelCapability.java`

当前方案引入了一个 **normalized channel model**。

### 当前支持的抽象字段

- `channel`
- `provider`
- `chatType`
- `userId`
- `conversationId`
- `messageId`

### 当前 capability registry 设计

已经建模的能力包括：

- `TEXT_IN`
- `TEXT_OUT`
- `STREAMING_OUT`
- `ATTACHMENTS`
- `REACTIONS`
- `THREADS`
- `CARDS`
- `AUDIO`

当前注册了：

- `api`
- `openai`
- `feishu`
- `telegram`
- `discord`
- `slack`
- `wechat`
- `whatsapp`

### 设计目的

- 让核心编排先理解“当前来自什么 channel，有哪些能力差异”
- 避免后面 channel 功能变多时把大量 `if (feishu) ...` 逻辑散到各处

**重点：现在只是 capability model，不是完整 channel adapter。**

要做到真正接近 OpenClaw，还需要：

- inbound webhook adapter
- outbound renderer
- attachment normalization
- card/thread/reaction abstraction
- channel auth/signature verification
- session binding

---

## 5. 配置设计

相关文件：

- `littleclaw/src/main/java/ai/littleclaw/config/LittleClawProperties.java`
- `littleclaw/src/main/resources/application.yml`

当前配置已经覆盖：

- system prompt
- skills path
- stream 控制参数
- request limits
- provider 配置
- admission 配置
- RAG 配置
- MCP server 配置

### 设计优点

- 所有关键能力通过 typed properties 管理
- 配置面已经具备继续扩展的能力
- 后续非常适合接入 config center / env override

### 当前不足

- 还没有 auth / tenant policy 配置
- 还没有 channel policy 配置
- 还没有 RAG index backend 选择
- 还没有 provider failover 配置

---

## 6. 当前方案最值得强调的设计亮点

### 6.1 上下文工程被提升为一级能力

这不是简单“把 prompt 拼长一点”。

当前方案已经把 skills、RAG、MCP、channel 放进统一上下文层，这意味着：

- 可以统一控制 prompt budget
- 可以统一做 relevance 排序
- 可以统一做策略拦截
- 可以统一做审计与回放

**这是整个方案里最该保留和继续加强的部分。**

### 6.2 多 channel 不是“多几个 webhook”，而是 capability model

当前方向是对的：

不是先做 N 个 controller，而是先做 normalized channel abstraction。

这样未来新增渠道时：

- 新增的是 adapter / renderer
- 而不是改核心编排逻辑

**这会直接降低后期渠道扩容成本。**

### 6.3 admission control 提前进入架构主线

很多项目会等压测失败后再补限流与流控。

当前方案反过来：

- admission 在 provider 调用前就生效
- tenant 级控制已经纳入主链路
- Redis 版已经为多副本部署铺路

**这对 SSE-heavy 场景非常关键。**

---

## 7. 关键风险与当前缺口

## 7.1 还没有 auth / tenant context filter

这是目前最大缺口之一。

后续必须补：

- API key / bearer token 识别
- tenant 绑定
- channel 签名校验
- request ID 注入

否则后面 MCP、RAG、channel 能力越多，风险越高。

## 7.2 RAG 仍然不是生产版

当前能证明“链路可通”，但不能证明：

- 召回效果可靠
- 检索结果可解释
- 多租户不串数据
- 更新及时

## 7.3 MCP 还只是静态工具目录

现在的 MCP 还不能实际执行工具。

这意味着：

- 架构边界是有了
- 真正 tool-use 闭环还没形成

## 7.4 缺少完整可观测性

后续必须补：

- active streams gauge
- first-byte latency
- upstream latency
- chunk stall counters
- admission reject counters
- provider error taxonomy metrics

## 7.5 缺少真实本地编译测试

由于当前环境无 `java` / `mvn`，现在只能做静态 CR 风格修正。

**重点：当前代码结构方向可信，但还没有经过真实编译器和测试框架完整验证。**

---

## 8. 推荐的下一阶段实施顺序

### P0：必须优先

1. auth + tenant/channel context filter
2. request ID + structured logging
3. provider error mapping + timeout classification
4. channel-aware response renderer

### P1：强烈建议

1. vector-backed RAG
2. MCP execution client
3. tool/provider policy layer
4. metrics + Prometheus integration

### P2：体系化增强

1. webhook ingress adapters for channels
2. session persistence / thread binding
3. plugin / extension model
4. memory slot / long-term memory module

### P3：更高阶能力

1. multi-agent workflow orchestration
2. async queue-backed planning/execution
3. richer card/reaction/audio abstractions
4. cost-aware routing / provider failover

---

## 9. 建议的最终演进形态

如果沿当前方向继续推进，`littleclaw` 最合理的最终形态会是：

- **core orchestration service**
  - chat planning
  - context assembly
  - policy enforcement
  - admission control
- **provider layer**
  - OpenAI / Claude / vLLM / internal
- **tool layer**
  - MCP execution
  - local tools
  - policy/quotas
- **retrieval layer**
  - vector + keyword hybrid RAG
- **channel layer**
  - webhook adapters
  - response renderers
  - session binding
- **infra layer**
  - Redis
  - metrics
  - trace/logging
  - durable audit

最终它会更像一个“聊天能力操作系统内核”，而不是单一聊天接口。

---

## 10. 结论

当前 `littleclaw` 的技术方案已经具备一个非常好的起点，原因不是“功能多”，而是：

- **关键边界已经拆对了**
- **上下文工程已经被提升为主链路能力**
- **SSE 场景下最容易出事的 admission / backpressure / quota 已经提前纳入设计**
- **RAG / MCP / multi-channel 已经具备可演进接入口**

当前最重要的工作不是继续横向加功能，而是继续把以下几层做实：

- auth / tenant / policy
- channel renderer
- provider error model
- vector RAG
- MCP execution
- metrics / tracing

**重点结论：当前方案不是“临时 demo 架构”，而是已经进入了“可向生产演进的 skeleton 架构”阶段。下一步应该从边界正确，走向实现扎实。**
