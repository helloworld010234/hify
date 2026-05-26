# Hify 全量单元测试补齐设计文档

> 日期：2026-05-26
> 需求：基于 testing.md 完成全部 18 个类 / 45-55 个测试方法的单元测试补齐
> 策略：A（按模块分 agent 并行）+ 基类预定义

---

## 1. 范围与目标

### 1.1 测试缺口现状

| 模块 | 当前测试数 | 缺口类 |
|------|-----------|--------|
| hify-chat | 3 IT + 1 UT | `ChatServiceImpl`, `ChatContextAssembler` |
| hify-workflow | 0 | `WorkflowEngine`, `ConditionNodeExecutor`, `NodeConfigParser` |
| hify-knowledge | 0 | `DocumentServiceImpl`（splitChunks 等） |
| hify-provider | 10 IT + 1 UT | `LlmServiceImpl`, `ProviderServiceImpl` 工具方法 |
| hify-mcp | 0 | `McpToolServiceImpl` |
| hify-common | 3 UT | `EncryptionService`, `TokenUtil`, `MdcTaskWrapper` |

### 1.2 目标

- 补齐 P0/P1/P2 全部单测，预计 45-55 个测试方法
- 所有测试遵循 testing.md 中的单元测试规范（命名、Given-When-Then、AssertJ、Mock 规范）
- 全部测试编译通过、运行通过

---

## 2. 执行策略

### 2.1 分 Agent 并行方案

```
主 Agent（我）
    ├── Agent 1: hify-chat
    │   └── ChatServiceImpl（8-10 个测试）
    │   └── ChatContextAssembler（3-4 个测试）
    ├── Agent 2: hify-workflow
    │   └── WorkflowEngine（6-8 个测试）
    │   └── ConditionNodeExecutor（3 个测试）
    │   └── NodeConfigParser（3 个测试）
    ├── Agent 3: hify-provider
    │   └── LlmServiceImpl.resolveAdapter（3 个测试）
    │   └── ProviderServiceImpl 工具方法（4-5 个测试）
    └── Agent 4: hify-knowledge + hify-mcp + hify-common
        └── DocumentServiceImpl.splitChunks 等（5-7 个测试）
        └── McpToolServiceImpl（3-4 个测试）
        └── EncryptionService, TokenUtil, MdcTaskWrapper（5-7 个测试）
```

### 2.2 基类预定义

由主 Agent 先生成 `AbstractUnitTest` 基类，包含：
- 统一的 `@ExtendWith(MockitoExtension.class)`
- 常用 Mockito/AssertJ static import 模板
- `Clock` 替换辅助方法（用于时间相关测试）

所有子 Agent 基于此基类编写测试。

---

## 3. 技术规范

- **框架**：JUnit 5 + Mockito + AssertJ
- **命名**：`should_[期望结果]_when_[输入条件]`
- **结构**：Given-When-Then 三段式
- **Mock**：构造器注入 + `@InjectMocks`，禁止 `@MockBean`（单测中）
- **断言**：AssertJ 链式断言，`assertThatThrownBy` 用于异常

---

## 4. 质量门禁

1. 每个 agent 提交前自查：禁止事项 T1-T10
2. 主 Agent 一致性审查：命名规范、结构规范、Mock 规范
3. 编译通过：`./mvnw compile test-compile`
4. 测试通过：`./mvnw test`

---

## 5. 风险与应对

| 风险 | 应对 |
|------|------|
| 各 agent 风格不一致 | 基类预定义 + 主 Agent 统一审查 |
| 测试运行失败（编译/断言错误） | 主 Agent 汇总后集中修复 |
| 部分类不存在或已重构 | agent 先 read 验证，发现差异立即上报主 Agent |
