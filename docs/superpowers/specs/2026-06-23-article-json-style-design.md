# 文章 JSON 结构化返回与样式处理设计

**日期：** 2026-06-23  
**状态：** 已批准，待实现  
**负责人：** Claude Code

## 1. 背景与目标

当前文章生成流水线中，LLM 返回的是带 HTML 标记的纯文本（如 `<h3>` 章节标题、`<s>` 着重、`<img>` 图片）。这种格式存在以下问题：

- 小标题与正文的边界依赖 LLM 是否正确输出 `<h3>` 标签，鲁棒性不足
- 样式处理需要基于正则或文本解析，难以扩展
- 无法针对每个章节做差异化处理（如不同 marker 对应不同版式）

本设计目标：

1. 让 LLM 直接返回结构化的 JSON 数组
2. 在生成流水线中加入"文章样式处理"步骤
3. 通过全局配置选择 A-G 中的一种处理方式
4. 最终仍输出现有 DOCX 和贴图所需的 HTML/文本格式

## 2. 设计决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| LLM 输出格式 | JSON 数组 | 结构清晰，便于后续样式处理 |
| 解析失败策略 | 任务失败 | 用户明确要求方案 1，不回退文本模式 |
| 样式处理位置 | 去 AI 味之后、写作风格替换之后、DOCX 生成之前 | 结构级操作应在文本清洗完成后、渲染输出前 |
| 配置级别 | 全局配置 | 简单，统一管理 |
| 配置粒度 | 一个配置对应 A-G 一个方案 | 用户明确要求 |
| 存量文章 | 只影响新文章 | 用户明确要求 |

## 3. JSON 结构

### 3.1 ArticleBlock 定义

```json
[
  {
    "type": "section",
    "title": "01 | 为什么这件事值得关注",
    "marker": "01",
    "markerText": "为什么这件事值得关注",
    "content": "正文内容，可能包含多个自然段...",
    "styleHint": "emphasis"
  },
  {
    "type": "paragraph",
    "title": null,
    "marker": null,
    "markerText": null,
    "content": "一个没有小标题的过渡段落。",
    "styleHint": "normal"
  }
]
```

### 3.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | `section` / `paragraph` / `image` / `quote` |
| title | string \| null | 否 | 完整小标题，保留原始标记 |
| marker | string \| null | 否 | 解析出的序号，如 `01` |
| markerText | string \| null | 否 | 去掉 marker 后的标题文本 |
| content | string | 是 | 正文内容，可包含 `<s>` 行内标记 |
| styleHint | string \| null | 否 | `normal` / `emphasis` / `story` / `tip` |

### 3.3 Marker 识别规则

- 匹配模式：行首的 `数字 + 分隔符 + 空格`
- 支持分隔符：`|`、`/`、`-`、`·`、`.`
- 示例：`01 |`、`02 -`、`03.`、`04 ·`
- 无标记时，`marker` 和 `markerText` 为 null，`title` 保存原始标题

## 4. Prompt 设计

在现有系统指令后追加：

```
【系统指令】
请直接输出 JSON 数组，不要输出任何思考过程、前言或总结。
输出必须是可以被标准 JSON 解析器解析的合法 JSON，不要包裹在 markdown 代码块中。

JSON 数组中每个元素代表文章的一个块，字段如下：
- type: "section"（带小标题的章节）或 "paragraph"（普通段落）
- title: 完整小标题字符串。如果章节有序号标记，请写成 "01 | 标题内容" 这种形式
- marker: 从 title 中解析出的序号，如 "01"；没有则填 null
- markerText: 去掉 marker 后的标题文本；没有则填 null
- content: 该章节的正文内容，允许使用 <s></s> 标记需要着重加强的词句
- styleHint: 该章节的风格提示，可选 "normal" / "emphasis" / "story" / "tip"

要求：
1. 文章最开头不要重复写总标题。
2. 根据内容需要插入 1-3 个章节小标题（type="section"），最多不超过 3 个。
3. 每个 section 的 content 控制在 200-400 字之间。
4. 不要输出 title 为 null 的 section。
```

## 5. 架构与数据流

```
┌─────────────────┐
│ 1. Prompt 构造   │
└────────┬────────┘
         ▼
┌─────────────────┐
│ 2. LLM 生成      │
└────────┬────────┘
         ▼
┌──────────────────────────┐
│ 3. ArticleJsonParser（新增）│  ← 解析 + 校验 JSON
└────────┬─────────────────┘
         ▼
┌──────────────────────────┐
│ 4. 去 AI 味                │  ← 遍历每个 block 处理文本
└────────┬─────────────────┘
         ▼
┌──────────────────────────┐
│ 5. 写作风格替换            │  ← 遍历每个 block 处理文本
└────────┬─────────────────┘
         ▼
┌──────────────────────────────┐
│ 6. ArticleStyleProcessor（新增）│  ← 读取全局配置，应用 A-G 方案
└────────┬─────────────────────┘
         ▼
┌──────────────────────────┐
│ 7. ArticleRenderer（新增）  │  ← 渲染为 HTML/文本
└────────┬─────────────────┘
         ▼
┌──────────────────────────┐
│ 8. DocxGenerator（改造）    │  ← 输入不变
└────────┬─────────────────┘
         ▼
┌──────────────────────────────────┐
│ 9. generate_image_posts.py（改造） │  ← 可选支持 JSON 输入
└──────────────────────────────────┘
```

## 6. 组件设计

### 6.1 ArticleJsonParser

**职责：** 将 LLM 返回的字符串解析为 `List<ArticleBlock>`，并做基础校验。

**输入：** LLM 返回的字符串（已去除 think 标签）

**输出：** `List<ArticleBlock>`

**失败行为：** 抛出 `ArticleParseException`，任务标记为 failed

**校验规则：**

1. 必须是合法 JSON 数组
2. 数组不能为空
3. 每个 block 必须包含 `type` 和 `content`
4. `type` 必须是允许值之一
5. `type=section` 时 `title` 不能为 null 或空
6. 如果 `title` 包含 marker 模式，`marker` 和 `markerText` 必须正确填充

### 6.2 ArticleStyleProcessor

**职责：** 根据全局配置，对 `List<ArticleBlock>` 应用 A-G 中的一种处理方式。

**输入：** `List<ArticleBlock>`、全局 `StyleConfig`

**输出：** 处理后的 `List<ArticleBlock>`

**支持的方案：**

| 方案 | 处理行为 |
|------|----------|
| A. 纯视觉美化 | 修改 `title` 的显示形式，为 `marker` 添加样式前缀/后缀 |
| B. 自动连续编号 | 重新计算所有 section 的 marker，统一为 `01`、`02`、`03`... |
| C. 模板映射 | 根据 marker 或 styleHint 选择不同模板样式 |
| D. 下游差异化 | 在 block 中注入 `renderTarget` 标记，供 DOCX/贴图分别处理 |
| E. 内容分级控制 | 根据 marker 位置调整 content 长度策略（当前版本不修改历史内容，仅用于后续生成指导） |
| F. 自动生成目录 | 收集所有 section 生成目录 block，插入到文章开头 |
| G. AI 配图提示 | 为每个 section 生成配图关键词，写入 block 元数据 |

### 6.3 ArticleRenderer

**职责：** 将处理后的 `List<ArticleBlock>` 渲染为现有 DocxGenerator 可消费的 HTML/文本格式。

**输出示例：**

```html
<h3>01 | 为什么这件事值得关注</h3>

正文内容...

<h3>02 | 普通人最容易踩的 3 个坑</h3>

正文内容...
```

## 7. 全局配置设计

### 7.1 数据模型

```java
public class StyleConfig {
    private String id;
    private String name;           // 配置名称，如 "小红书爆款风"
    private String strategy;       // A/B/C/D/E/F/G
    private Map<String, Object> params;  // 额外参数
    private Integer isActive;      // 1 启用，0 禁用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### 7.2 存储

- 新增数据库表 `style_config`
- 通过 MyBatis mapper 读写
- 同时提供默认配置（如 strategy=A），避免表为空时无法生成

### 7.3 配置加载

- `ArticleStyleProcessor` 初始化时读取 active 配置
- 如果没有 active 配置，使用 strategy=A 作为默认

### 7.4 管理接口（可选）

- `GET /api/style-config`：获取当前配置
- `PUT /api/style-config`：更新当前配置

## 8. 下游改造

### 8.1 DocxGenerator

- 输入格式不变：仍接收 HTML/文本
- 不需要改造（已有 `<h3>`、`<s>`、`<img>` 处理逻辑）

### 8.2 generate_image_posts.py

- 当前版本：从 DOCX 读取纯文本段落
- 可选增强：支持直接读取 JSON 输入，基于 `type` 和 `styleHint` 做更精细的贴图版式
- 本次实现：先保持 DOCX 路径不变，将 JSON 渲染后的文本写入 DOCX

## 9. 错误处理

| 错误场景 | 处理方式 |
|----------|----------|
| LLM 返回非 JSON | 抛出 `ArticleParseException`，任务 failed |
| JSON 数组为空 | 抛出 `ArticleParseException`，任务 failed |
| block 缺少必填字段 | 抛出 `ArticleParseException`，任务 failed |
| section title 为空 | 抛出 `ArticleParseException`，任务 failed |
| style processor 执行异常 | 记录 warn 日志，透传原始 block，不阻塞任务 |
| renderer 执行异常 | 记录 warn 日志，任务 failed |

## 10. 测试策略

### 10.1 单元测试

- `ArticleJsonParserTest`
  - 合法 JSON 解析成功
  - 非法 JSON 抛出异常
  - marker 识别正确
  - 必填字段缺失抛出异常

- `ArticleStyleProcessorTest`
  - 每种策略 A-G 的处理结果符合预期
  - 空配置使用默认策略 A

- `ArticleRendererTest`
  - section / paragraph / image / quote 正确渲染
  - marker 样式正确输出

### 10.2 集成测试

- 端到端生成一篇文章，验证 JSON 输出到 DOCX 的完整链路
- 切换全局配置 A/B/C，验证输出差异

### 10.3 人工测试

- 用 Kimi 和 MiniMax 分别测试 JSON 遵循能力
- 测试边界 prompt（超长标题、无 marker、多个 section 等）

## 11. 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| LLM 不遵循 JSON 输出格式 | 任务失败率上升 | 强化 prompt，加示例；后续可考虑加入 few-shot 或 schema 约束 |
| MiniMax 对 JSON 遵循能力弱于 Kimi | 切换模型时失败 | 分别测试两个 provider，必要时为不同模型写不同 prompt |
| 样式处理异常影响主流程 | 任务失败 | 样式处理层捕获异常，透传原始 block |
| 现有 `<h3>` 风格数据兼容 | 历史任务可能失效 | 历史数据不参与新流程，只影响新任务 |

## 12. 实现范围

### 本期必做

1. 新增 `ArticleBlock` 数据模型
2. 新增 `ArticleJsonParser`
3. 改造 `GenerationTaskExecutor`：在 LLM 生成后调用 parser
4. 改造去 AI 味和写作风格替换：支持遍历 block
5. 新增 `ArticleStyleProcessor` 和 `StyleConfig`
6. 新增 `ArticleRenderer`
7. 新增 `style_config` 数据库表和 mapper
8. 改造 prompt，要求 LLM 输出 JSON
9. 补充单元测试

### 本期不做

1. 前端配置管理页面（可先通过数据库配置）
2. `generate_image_posts.py` 直接消费 JSON（先走 DOCX 路径）
3. 多配置切换 UI
4. 存量文章重新处理

## 13. 附录：示例 JSON

```json
[
  {
    "type": "section",
    "title": "01 | 为什么这件事值得关注",
    "marker": "01",
    "markerText": "为什么这件事值得关注",
    "content": "你有没有发现，身边越来越多人开始关注这个话题。它不是一时的风口，而是正在改变我们生活方式的长期趋势。",
    "styleHint": "emphasis"
  },
  {
    "type": "paragraph",
    "title": null,
    "marker": null,
    "markerText": null,
    "content": "今天我们就来聊聊，普通人最容易忽视的几个关键点。",
    "styleHint": "normal"
  },
  {
    "type": "section",
    "title": "02 | 普通人最容易踩的 3 个坑",
    "marker": "02",
    "markerText": "普通人最容易踩的 3 个坑",
    "content": "第一个坑是<s>只看短期收益</s>，忽略了长期复利。第二个坑是<s>追求完美开局</s>，迟迟不行动。第三个坑是<s>从不复盘</s>，同样的错误反复犯。",
    "styleHint": "story"
  }
]
```
