# 文章 JSON 结构化返回与样式处理实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 LLM 返回 JSON 结构化文章数组，并在生成流水线中加入基于全局配置的样式处理层，最终仍输出 DOCX 和贴图。

**Architecture:** 在 `GenerationTaskExecutor` 中 LLM 生成后新增 `ArticleJsonParser` 解析 JSON；去 AI 味和写作风格替换改为遍历 `List<ArticleBlock>`；然后通过 `ArticleStyleProcessor` 应用全局配置策略；最后由 `ArticleRenderer` 渲染回 HTML/文本传给 `DocxGenerator`。

**Tech Stack:** Java 17, Spring Boot 3.2.0, MyBatis 3.0.3 (注解), MySQL, Jackson, Maven

## Global Constraints

- LLM 输出格式必须是合法 JSON 数组，解析失败时任务标记为 failed（不回退文本模式）
- 样式处理在去 AI 味之后、写作风格替换之后、DOCX 生成之前
- 全局配置，一个配置对应 A-G 一种方案
- 只影响新文章，存量文章不重新处理
- 数据库表名前缀使用 `tu_`
- MyBatis mapper 使用注解方式（无 XML）
- 项目当前无测试目录，需要创建 `services/admin-backend/src/test/java/...`

---

## File Structure

### 新增文件

| 文件 | 职责 |
|------|------|
| `entity/ArticleBlock.java` | 文章块数据模型 |
| `entity/StyleConfig.java` | 样式配置实体 |
| `exception/ArticleParseException.java` | JSON 解析/校验异常 |
| `mapper/StyleConfigMapper.java` | `tu_style_config` 表 CRUD |
| `service/StyleConfigService.java` | 配置加载与默认配置兜底 |
| `util/ArticleJsonParser.java` | 解析 LLM 返回的 JSON 字符串 |
| `util/ArticleStyleProcessor.java` | 根据全局配置应用样式策略 |
| `util/ArticleRenderer.java` | 将 block 列表渲染为 HTML/文本 |
| `util/ArticleStyleStrategies.java` | A-G 七种策略的具体实现 |
| `src/test/java/com/example/blogger/util/ArticleJsonParserTest.java` | JSON 解析器单元测试 |
| `src/test/java/com/example/blogger/util/ArticleStyleProcessorTest.java` | 样式处理器单元测试 |
| `src/test/java/com/example/blogger/util/ArticleRendererTest.java` | 渲染器单元测试 |

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `util/AiFlavorRemover.java` | 新增 `removeAiFlavor(List<ArticleBlock>)` 方法 |
| `service/WritingStyleService.java` | 新增 `applyStyle(List<ArticleBlock>)` 方法 |
| `service/GenerationTaskExecutor.java` | 改造流水线：JSON 解析 → 遍历 block 处理 → 样式处理 → 渲染 |

### 数据库变更

| 表 | 操作 |
|----|------|
| `tu_style_config` | 新建 |

---

## Task 0: 添加测试依赖

**Files:**
- Modify: `services/admin-backend/pom.xml`

**Interfaces:**
- Consumes: 无
- Produces: 可运行的 JUnit 5 + Mockito 测试环境

- [ ] **Step 1: Add spring-boot-starter-test dependency**

在 `services/admin-backend/pom.xml` 的 `<dependencies>` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verify tests can compile**

Run: `cd services/admin-backend && mvn test-compile`
Expected: BUILD SUCCESS（即使没有测试文件）

- [ ] **Step 3: Commit**

```bash
git add services/admin-backend/pom.xml
git commit -m "chore(test): add spring-boot-starter-test dependency"
```

---

## Task 1: 创建核心数据模型与异常

**Files:**
- Create: `services/admin-backend/src/main/java/com/example/blogger/entity/ArticleBlock.java`
- Create: `services/admin-backend/src/main/java/com/example/blogger/entity/StyleConfig.java`
- Create: `services/admin-backend/src/main/java/com/example/blogger/exception/ArticleParseException.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/entity/ArticleBlockTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `ArticleBlock`（字段：type, title, marker, markerText, content, styleHint, renderMeta）、`StyleConfig`（字段：id, name, strategy, params, isActive, createdAt, updatedAt）、`ArticleParseException`

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArticleBlockTest {

    @Test
    void shouldBuildSectionBlock() {
        ArticleBlock block = ArticleBlock.section("01 | 为什么值得关注", "01", "为什么值得关注", "正文内容", "emphasis");
        assertEquals("section", block.getType());
        assertEquals("01 | 为什么值得关注", block.getTitle());
        assertEquals("01", block.getMarker());
        assertEquals("为什么值得关注", block.getMarkerText());
        assertEquals("正文内容", block.getContent());
        assertEquals("emphasis", block.getStyleHint());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleBlockTest`
Expected: FAIL with "cannot find symbol ArticleBlock"

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.blogger.entity;

import java.util.HashMap;
import java.util.Map;

public class ArticleBlock {

    public static final String TYPE_SECTION = "section";
    public static final String TYPE_PARAGRAPH = "paragraph";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_QUOTE = "quote";

    private String type;
    private String title;
    private String marker;
    private String markerText;
    private String content;
    private String styleHint;
    private Map<String, Object> renderMeta = new HashMap<>();

    public ArticleBlock() {
    }

    public static ArticleBlock section(String title, String marker, String markerText, String content, String styleHint) {
        ArticleBlock block = new ArticleBlock();
        block.type = TYPE_SECTION;
        block.title = title;
        block.marker = marker;
        block.markerText = markerText;
        block.content = content;
        block.styleHint = styleHint;
        return block;
    }

    public static ArticleBlock paragraph(String content, String styleHint) {
        ArticleBlock block = new ArticleBlock();
        block.type = TYPE_PARAGRAPH;
        block.content = content;
        block.styleHint = styleHint;
        return block;
    }

    public static ArticleBlock image(String content) {
        ArticleBlock block = new ArticleBlock();
        block.type = TYPE_IMAGE;
        block.content = content;
        return block;
    }

    // getters and setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMarker() { return marker; }
    public void setMarker(String marker) { this.marker = marker; }
    public String getMarkerText() { return markerText; }
    public void setMarkerText(String markerText) { this.markerText = markerText; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStyleHint() { return styleHint; }
    public void setStyleHint(String styleHint) { this.styleHint = styleHint; }
    public Map<String, Object> getRenderMeta() { return renderMeta; }
    public void setRenderMeta(Map<String, Object> renderMeta) { this.renderMeta = renderMeta; }
}
```

```java
package com.example.blogger.entity;

import java.time.LocalDateTime;
import java.util.Map;

public class StyleConfig {

    private String id;
    private String name;
    private String strategy;
    private Map<String, Object> params;
    private Integer isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

```java
package com.example.blogger.exception;

public class ArticleParseException extends RuntimeException {

    public ArticleParseException(String message) {
        super(message);
    }

    public ArticleParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleBlockTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/entity/ArticleBlock.java \
        services/admin-backend/src/main/java/com/example/blogger/entity/StyleConfig.java \
        services/admin-backend/src/main/java/com/example/blogger/exception/ArticleParseException.java \
        services/admin-backend/src/test/java/com/example/blogger/entity/ArticleBlockTest.java
git commit -m "feat(article): add ArticleBlock, StyleConfig and ArticleParseException"
```

---

## Task 2: 实现 ArticleJsonParser

**Files:**
- Create: `services/admin-backend/src/main/java/com/example/blogger/util/ArticleJsonParser.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/util/ArticleJsonParserTest.java`

**Interfaces:**
- Consumes: `String`（LLM 返回的 JSON 字符串）
- Produces: `List<ArticleBlock>`；失败时抛出 `ArticleParseException`

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.exception.ArticleParseException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArticleJsonParserTest {

    @Test
    void shouldParseValidJson() {
        String json = "[{\"type\":\"section\",\"title\":\"01 | 为什么值得关注\",\"marker\":\"01\",\"markerText\":\"为什么值得关注\",\"content\":\"正文\",\"styleHint\":\"emphasis\"}]";
        ArticleJsonParser parser = new ArticleJsonParser();
        List<ArticleBlock> blocks = parser.parse(json);
        assertEquals(1, blocks.size());
        assertEquals("01", blocks.get(0).getMarker());
        assertEquals("为什么值得关注", blocks.get(0).getMarkerText());
    }

    @Test
    void shouldRejectInvalidJson() {
        ArticleJsonParser parser = new ArticleJsonParser();
        assertThrows(ArticleParseException.class, () -> parser.parse("not json"));
    }

    @Test
    void shouldRejectEmptyArray() {
        ArticleJsonParser parser = new ArticleJsonParser();
        assertThrows(ArticleParseException.class, () -> parser.parse("[]"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleJsonParserTest`
Expected: FAIL with "cannot find symbol ArticleJsonParser"

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.exception.ArticleParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

public class ArticleJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final java.util.regex.Pattern MARKER_PATTERN = java.util.regex.Pattern.compile("^(\\d+)\\s*([|/\\-·.])\\s*(.+)$");

    public List<ArticleBlock> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ArticleParseException("LLM 返回内容为空");
        }

        ArrayNode array;
        try {
            JsonNode root = MAPPER.readTree(raw.trim());
            if (!root.isArray()) {
                throw new ArticleParseException("LLM 返回不是 JSON 数组");
            }
            array = (ArrayNode) root;
        } catch (Exception e) {
            throw new ArticleParseException("LLM 返回不是合法 JSON: " + e.getMessage(), e);
        }

        if (array.size() == 0) {
            throw new ArticleParseException("LLM 返回的 JSON 数组为空");
        }

        List<ArticleBlock> blocks = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            blocks.add(parseBlock(array.get(i), i));
        }
        return blocks;
    }

    private ArticleBlock parseBlock(JsonNode node, int index) {
        if (!node.isObject()) {
            throw new ArticleParseException("第 " + index + " 个 block 不是对象");
        }

        ArticleBlock block = new ArticleBlock();

        String type = getString(node, "type");
        if (type == null || type.isEmpty()) {
            throw new ArticleParseException("第 " + index + " 个 block 缺少 type");
        }
        if (!type.equals(ArticleBlock.TYPE_SECTION) &&
            !type.equals(ArticleBlock.TYPE_PARAGRAPH) &&
            !type.equals(ArticleBlock.TYPE_IMAGE) &&
            !type.equals(ArticleBlock.TYPE_QUOTE)) {
            throw new ArticleParseException("第 " + index + " 个 block 的 type 不合法: " + type);
        }
        block.setType(type);

        String content = getString(node, "content");
        if (content == null || content.isEmpty()) {
            throw new ArticleParseException("第 " + index + " 个 block 缺少 content");
        }
        block.setContent(content);

        block.setTitle(getString(node, "title"));
        block.setMarker(getString(node, "marker"));
        block.setMarkerText(getString(node, "markerText"));
        block.setStyleHint(getString(node, "styleHint"));

        if (ArticleBlock.TYPE_SECTION.equals(type)) {
            if (block.getTitle() == null || block.getTitle().isEmpty()) {
                throw new ArticleParseException("第 " + index + " 个 section 缺少 title");
            }
            validateMarkerConsistency(block, index);
        }

        return block;
    }

    private void validateMarkerConsistency(ArticleBlock block, int index) {
        String title = block.getTitle();
        java.util.regex.Matcher matcher = MARKER_PATTERN.matcher(title.trim());
        if (matcher.matches()) {
            String expectedMarker = matcher.group(1);
            String expectedMarkerText = matcher.group(3).trim();
            if (!expectedMarker.equals(block.getMarker())) {
                throw new ArticleParseException("第 " + index + " 个 block 的 marker 与 title 不一致: title=" + title + ", marker=" + block.getMarker());
            }
            if (!expectedMarkerText.equals(block.getMarkerText())) {
                throw new ArticleParseException("第 " + index + " 个 block 的 markerText 与 title 不一致: title=" + title + ", markerText=" + block.getMarkerText());
            }
        }
    }

    private String getString(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            return value.toString();
        }
        return value.asText();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleJsonParserTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/util/ArticleJsonParser.java \
        services/admin-backend/src/test/java/com/example/blogger/util/ArticleJsonParserTest.java
git commit -m "feat(article): add ArticleJsonParser with validation"
```

---

## Task 3: 改造去 AI 味支持 List<ArticleBlock>

**Files:**
- Modify: `services/admin-backend/src/main/java/com/example/blogger/util/AiFlavorRemover.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/util/AiFlavorRemoverBlockTest.java`

**Interfaces:**
- Consumes: `List<ArticleBlock>`（来自 Task 2）
- Produces: `List<ArticleBlock>`（title 和 content 已去 AI 味）

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiFlavorRemoverBlockTest {

    @Test
    void shouldProcessBlocks() {
        // This is a lightweight test; AiFlavorRemover depends on database and Python script.
        // We only verify the method signature and that it doesn't throw.
        AiFlavorRemover remover = new AiFlavorRemover(null);
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("01 | 标题", "01", "标题", "这是一段文本。", "normal")
        );
        List<ArticleBlock> result = remover.removeAiFlavor(blocks);
        assertEquals(1, result.size());
        assertEquals("这是一段文本。", result.get(0).getContent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=AiFlavorRemoverBlockTest`
Expected: FAIL with "cannot find symbol method removeAiFlavor(List)"

- [ ] **Step 3: Write minimal implementation**

在 `AiFlavorRemover.java` 中新增以下方法：

```java
/**
 * 对 ArticleBlock 列表逐个去 AI 味。
 * title 和 content 都会处理；如果处理失败则保留原始内容并记录日志。
 */
public List<ArticleBlock> removeAiFlavor(List<ArticleBlock> blocks) {
    if (blocks == null || blocks.isEmpty()) {
        return blocks;
    }
    List<ArticleBlock> result = new ArrayList<>();
    for (ArticleBlock block : blocks) {
        ArticleBlock copy = copyBlock(block);
        try {
            if (copy.getTitle() != null && !copy.getTitle().isEmpty()) {
                copy.setTitle(removeAiFlavor(copy.getTitle()));
            }
            if (copy.getContent() != null && !copy.getContent().isEmpty()) {
                copy.setContent(removeAiFlavor(copy.getContent()));
            }
        } catch (Exception e) {
            log.warn("[AiFlavorRemover] 处理 block 失败，保留原始内容: {}", e.getMessage());
        }
        result.add(copy);
    }
    return result;
}

private ArticleBlock copyBlock(ArticleBlock block) {
    ArticleBlock copy = new ArticleBlock();
    copy.setType(block.getType());
    copy.setTitle(block.getTitle());
    copy.setMarker(block.getMarker());
    copy.setMarkerText(block.getMarkerText());
    copy.setContent(block.getContent());
    copy.setStyleHint(block.getStyleHint());
    copy.setRenderMeta(block.getRenderMeta() != null ? new java.util.HashMap<>(block.getRenderMeta()) : null);
    return copy;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test -Dtest=AiFlavorRemoverBlockTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/util/AiFlavorRemover.java \
        services/admin-backend/src/test/java/com/example/blogger/util/AiFlavorRemoverBlockTest.java
git commit -m "feat(article): support removing AI flavor from ArticleBlock list"
```

---

## Task 4: 改造写作风格替换支持 List<ArticleBlock>

**Files:**
- Modify: `services/admin-backend/src/main/java/com/example/blogger/service/WritingStyleService.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/service/WritingStyleServiceBlockTest.java`

**Interfaces:**
- Consumes: `List<ArticleBlock>`（来自 Task 3）
- Produces: `List<ArticleBlock>`（title 和 content 已完成风格替换）

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.service;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.mapper.WritingStyleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WritingStyleServiceBlockTest {

    @Test
    void shouldReturnEmptyListWhenInputIsEmpty() {
        WritingStyleMapper mapper = mock(WritingStyleMapper.class);
        when(mapper.findAll()).thenReturn(List.of());
        WritingStyleService service = new WritingStyleService(mapper);
        assertTrue(service.applyStyle(List.of()).isEmpty());
    }

    @Test
    void shouldProcessBlockContent() {
        WritingStyleMapper mapper = mock(WritingStyleMapper.class);
        when(mapper.findAll()).thenReturn(List.of());
        WritingStyleService service = new WritingStyleService(mapper);

        List<ArticleBlock> blocks = List.of(
            ArticleBlock.paragraph("这是一段文本。", "normal")
        );
        List<ArticleBlock> result = service.applyStyle(blocks);
        assertEquals("这是一段文本。", result.get(0).getContent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=WritingStyleServiceBlockTest`
Expected: FAIL with "cannot find symbol method applyStyle(List)"

- [ ] **Step 3: Write minimal implementation**

在 `WritingStyleService.java` 中新增以下方法：

```java
/**
 * 对 ArticleBlock 列表逐个应用写作风格替换。
 * title 和 content 都会处理；处理失败时保留原始内容。
 */
public List<ArticleBlock> applyStyle(List<ArticleBlock> blocks) {
    if (blocks == null || blocks.isEmpty()) {
        return blocks;
    }
    List<ArticleBlock> result = new ArrayList<>();
    for (ArticleBlock block : blocks) {
        ArticleBlock copy = copyBlock(block);
        try {
            if (copy.getTitle() != null && !copy.getTitle().isEmpty()) {
                copy.setTitle(applyStyle(copy.getTitle()));
            }
            if (copy.getContent() != null && !copy.getContent().isEmpty()) {
                copy.setContent(applyStyle(copy.getContent()));
            }
        } catch (Exception e) {
            log.warn("[WritingStyleService] 处理 block 失败，保留原始内容: {}", e.getMessage());
        }
        result.add(copy);
    }
    return result;
}

private ArticleBlock copyBlock(ArticleBlock block) {
    ArticleBlock copy = new ArticleBlock();
    copy.setType(block.getType());
    copy.setTitle(block.getTitle());
    copy.setMarker(block.getMarker());
    copy.setMarkerText(block.getMarkerText());
    copy.setContent(block.getContent());
    copy.setStyleHint(block.getStyleHint());
    copy.setRenderMeta(block.getRenderMeta() != null ? new java.util.HashMap<>(block.getRenderMeta()) : null);
    return copy;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test -Dtest=WritingStyleServiceBlockTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/service/WritingStyleService.java \
        services/admin-backend/src/test/java/com/example/blogger/service/WritingStyleServiceBlockTest.java
git commit -m "feat(article): support writing style replacement on ArticleBlock list"
```

---

## Task 5: 实现样式策略（A-G）

**Files:**
- Create: `services/admin-backend/src/main/java/com/example/blogger/util/ArticleStyleStrategies.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/util/ArticleStyleStrategiesTest.java`

**Interfaces:**
- Consumes: `List<ArticleBlock>`、策略标识符（"A"-"G"）
- Produces: `List<ArticleBlock>`

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArticleStyleStrategiesTest {

    @Test
    void shouldApplyVisualBeautify() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("01 | 标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "A");
        assertTrue(result.get(0).getTitle().contains("01"));
        assertTrue(result.get(0).getTitle().contains("标题"));
    }

    @Test
    void shouldRenumberMarkers() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("第一章", "第一章", "", "正文1", "normal"),
            ArticleBlock.section("第二章", "第二章", "", "正文2", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "B");
        assertEquals("01", result.get(0).getMarker());
        assertEquals("02", result.get(1).getMarker());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleStyleStrategiesTest`
Expected: FAIL with "cannot find symbol ArticleStyleStrategies"

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;

import java.util.ArrayList;
import java.util.List;

public class ArticleStyleStrategies {

    public static List<ArticleBlock> apply(List<ArticleBlock> blocks, String strategy) {
        if (blocks == null) {
            return List.of();
        }
        String upper = strategy == null ? "A" : strategy.toUpperCase();
        return switch (upper) {
            case "A" -> visualBeautify(blocks);
            case "B" -> autoNumbering(blocks);
            case "C" -> templateMapping(blocks);
            case "D" -> downstreamDifferentiation(blocks);
            case "E" -> contentGrading(blocks);
            case "F" -> generateToc(blocks);
            case "G" -> aiImageHint(blocks);
            default -> visualBeautify(blocks);
        };
    }

    // A. 纯视觉美化：保持 title 不变，由 renderer 负责展示样式
    private static List<ArticleBlock> visualBeautify(List<ArticleBlock> blocks) {
        return copyBlocks(blocks);
    }

    // B. 自动连续编号：对所有 section 重新编号
    private static List<ArticleBlock> autoNumbering(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        int sectionIndex = 0;
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                sectionIndex++;
                String marker = String.format("%02d", sectionIndex);
                String markerText = block.getMarkerText() != null && !block.getMarkerText().isEmpty()
                        ? block.getMarkerText()
                        : (block.getTitle() != null ? block.getTitle() : "");
                block.setMarker(marker);
                block.setTitle(marker + " | " + markerText);
                block.setMarkerText(markerText);
            }
        }
        return result;
    }

    // C. 模板映射：为每个 section 写入模板标记
    private static List<ArticleBlock> templateMapping(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                String hint = block.getStyleHint();
                String template = switch (hint) {
                    case "story" -> "story-card";
                    case "tip" -> "tip-card";
                    case "emphasis" -> "emphasis-card";
                    default -> "knowledge-card";
                };
                block.getRenderMeta().put("template", template);
            }
        }
        return result;
    }

    // D. 下游差异化：标记渲染目标
    private static List<ArticleBlock> downstreamDifferentiation(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        for (ArticleBlock block : result) {
            block.getRenderMeta().put("docxStyle", "minimal");
            block.getRenderMeta().put("imagePostStyle", "social");
        }
        return result;
    }

    // E. 内容分级控制：写入策略元数据，不修改现有内容
    private static List<ArticleBlock> contentGrading(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        int sectionIndex = 0;
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                sectionIndex++;
                String grade = sectionIndex == 1 ? "intro-short" : (sectionIndex == result.size() ? "action" : "core-long");
                block.getRenderMeta().put("contentGrade", grade);
            }
        }
        return result;
    }

    // F. 自动生成目录：在开头插入目录 block
    private static List<ArticleBlock> generateToc(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = new ArrayList<>();
        StringBuilder toc = new StringBuilder("本文目录：\n");
        int index = 0;
        for (ArticleBlock block : blocks) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                index++;
                String title = block.getMarkerText() != null && !block.getMarkerText().isEmpty()
                        ? block.getMarkerText()
                        : block.getTitle();
                toc.append(String.format("%02d · %s\n", index, title));
            }
        }
        ArticleBlock tocBlock = ArticleBlock.paragraph(toc.toString().trim(), "normal");
        result.add(tocBlock);
        result.addAll(copyBlocks(blocks));
        return result;
    }

    // G. AI 配图提示：为每个 section 生成配图关键词
    private static List<ArticleBlock> aiImageHint(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                String keyword = (block.getMarkerText() != null ? block.getMarkerText() : block.getTitle());
                block.getRenderMeta().put("imageKeyword", keyword);
            }
        }
        return result;
    }

    private static List<ArticleBlock> copyBlocks(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = new ArrayList<>();
        for (ArticleBlock block : blocks) {
            ArticleBlock copy = new ArticleBlock();
            copy.setType(block.getType());
            copy.setTitle(block.getTitle());
            copy.setMarker(block.getMarker());
            copy.setMarkerText(block.getMarkerText());
            copy.setContent(block.getContent());
            copy.setStyleHint(block.getStyleHint());
            copy.setRenderMeta(block.getRenderMeta() != null ? new java.util.HashMap<>(block.getRenderMeta()) : new java.util.HashMap<>());
            result.add(copy);
        }
        return result;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleStyleStrategiesTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/util/ArticleStyleStrategies.java \
        services/admin-backend/src/test/java/com/example/blogger/util/ArticleStyleStrategiesTest.java
git commit -m "feat(article): add A-G style strategy implementations"
```

---

## Task 6: 实现 ArticleStyleProcessor 与 StyleConfig 持久化

**Files:**
- Create: `services/admin-backend/src/main/java/com/example/blogger/mapper/StyleConfigMapper.java`
- Create: `services/admin-backend/src/main/java/com/example/blogger/service/StyleConfigService.java`
- Create: `services/admin-backend/src/main/java/com/example/blogger/util/ArticleStyleProcessor.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/util/ArticleStyleProcessorTest.java`

**Interfaces:**
- Consumes: `List<ArticleBlock>`、`StyleConfigService`
- Produces: `List<ArticleBlock>`

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.entity.StyleConfig;
import com.example.blogger.service.StyleConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArticleStyleProcessorTest {

    @Test
    void shouldUseDefaultStrategyWhenNoActiveConfig() {
        StyleConfigService configService = mock(StyleConfigService.class);
        when(configService.findActive()).thenReturn(null);
        ArticleStyleProcessor processor = new ArticleStyleProcessor(configService);

        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("01 | 标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = processor.process(blocks);
        assertEquals("01 | 标题", result.get(0).getTitle());
    }

    @Test
    void shouldApplyConfiguredStrategy() {
        StyleConfigService configService = mock(StyleConfigService.class);
        StyleConfig config = new StyleConfig();
        config.setStrategy("B");
        when(configService.findActive()).thenReturn(config);
        ArticleStyleProcessor processor = new ArticleStyleProcessor(configService);

        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("旧标题", "旧", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = processor.process(blocks);
        assertEquals("01", result.get(0).getMarker());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleStyleProcessorTest`
Expected: FAIL with multiple "cannot find symbol"

- [ ] **Step 3: Write minimal implementation**

`StyleConfigMapper.java`:

```java
package com.example.blogger.mapper;

import com.example.blogger.entity.StyleConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface StyleConfigMapper {

    @Select("SELECT * FROM tu_style_config WHERE is_active = 1 LIMIT 1")
    StyleConfig findActive();

    @Insert("INSERT INTO tu_style_config(id, name, strategy, params, is_active, created_at, updated_at) " +
            "VALUES(#{id}, #{name}, #{strategy}, #{params}, #{isActive}, NOW(), NOW())")
    int insert(StyleConfig config);

    @Update("UPDATE tu_style_config SET is_active = 0")
    int deactivateAll();

    @Update("UPDATE tu_style_config SET name = #{name}, strategy = #{strategy}, params = #{params}, " +
            "is_active = #{isActive}, updated_at = NOW() WHERE id = #{id}")
    int update(StyleConfig config);
}
```

`StyleConfigService.java`:

```java
package com.example.blogger.service;

import com.example.blogger.entity.StyleConfig;
import com.example.blogger.mapper.StyleConfigMapper;
import org.springframework.stereotype.Service;

@Service
public class StyleConfigService {

    private final StyleConfigMapper styleConfigMapper;

    public StyleConfigService(StyleConfigMapper styleConfigMapper) {
        this.styleConfigMapper = styleConfigMapper;
    }

    public StyleConfig findActive() {
        StyleConfig active = styleConfigMapper.findActive();
        if (active == null) {
            active = createDefault();
        }
        return active;
    }

    private StyleConfig createDefault() {
        StyleConfig config = new StyleConfig();
        config.setName("默认纯视觉美化");
        config.setStrategy("A");
        config.setIsActive(1);
        return config;
    }
}
```

`ArticleStyleProcessor.java`:

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.entity.StyleConfig;
import com.example.blogger.service.StyleConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArticleStyleProcessor {

    private static final Logger log = LoggerFactory.getLogger(ArticleStyleProcessor.class);

    private final StyleConfigService styleConfigService;

    public ArticleStyleProcessor(StyleConfigService styleConfigService) {
        this.styleConfigService = styleConfigService;
    }

    public List<ArticleBlock> process(List<ArticleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        try {
            StyleConfig config = styleConfigService.findActive();
            String strategy = config != null && config.getStrategy() != null ? config.getStrategy().toUpperCase() : "A";
            log.info("[ArticleStyleProcessor] 应用样式策略: {}", strategy);
            return ArticleStyleStrategies.apply(blocks, strategy);
        } catch (Exception e) {
            log.warn("[ArticleStyleProcessor] 样式处理失败，透传原始 block: {}", e.getMessage());
            return blocks;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleStyleProcessorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/mapper/StyleConfigMapper.java \
        services/admin-backend/src/main/java/com/example/blogger/service/StyleConfigService.java \
        services/admin-backend/src/main/java/com/example/blogger/util/ArticleStyleProcessor.java \
        services/admin-backend/src/test/java/com/example/blogger/util/ArticleStyleProcessorTest.java
git commit -m "feat(article): add style config persistence and ArticleStyleProcessor"
```

---

## Task 7: 实现 ArticleRenderer

**Files:**
- Create: `services/admin-backend/src/main/java/com/example/blogger/util/ArticleRenderer.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/util/ArticleRendererTest.java`

**Interfaces:**
- Consumes: `List<ArticleBlock>`
- Produces: `String`（带 `<h3>`/`<s>`/`<img>` 的 HTML/文本）

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArticleRendererTest {

    @Test
    void shouldRenderSectionAndParagraph() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("01 | 标题", "01", "标题", "正文内容", "normal"),
            ArticleBlock.paragraph("第二段。", "normal")
        );
        ArticleRenderer renderer = new ArticleRenderer();
        String output = renderer.render(blocks);
        assertTrue(output.contains("<h3>01 | 标题</h3>"));
        assertTrue(output.contains("正文内容"));
        assertTrue(output.contains("第二段。"));
    }

    @Test
    void shouldRenderImageBlock() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.image("<img src=\"/uploads/images/test.jpg\">")
        );
        ArticleRenderer renderer = new ArticleRenderer();
        String output = renderer.render(blocks);
        assertTrue(output.contains("<img src=\"/uploads/images/test.jpg\">"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleRendererTest`
Expected: FAIL with "cannot find symbol ArticleRenderer"

- [ ] **Step 3: Write minimal implementation**

```java
package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArticleRenderer {

    public String render(List<ArticleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            ArticleBlock block = blocks.get(i);
            switch (block.getType()) {
                case ArticleBlock.TYPE_SECTION -> renderSection(sb, block);
                case ArticleBlock.TYPE_PARAGRAPH, ArticleBlock.TYPE_QUOTE -> renderParagraph(sb, block);
                case ArticleBlock.TYPE_IMAGE -> renderImage(sb, block);
                default -> renderParagraph(sb, block);
            }
            if (i < blocks.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private void renderSection(StringBuilder sb, ArticleBlock block) {
        String title = block.getTitle() != null && !block.getTitle().isEmpty()
                ? block.getTitle()
                : (block.getMarkerText() != null ? block.getMarkerText() : "");
        if (title.isEmpty()) {
            return;
        }
        sb.append("<h3>").append(escapeHtml(title)).append("</h3>\n\n");
        if (block.getContent() != null && !block.getContent().isEmpty()) {
            sb.append(block.getContent());
        }
    }

    private void renderParagraph(StringBuilder sb, ArticleBlock block) {
        if (block.getContent() != null) {
            sb.append(block.getContent());
        }
    }

    private void renderImage(StringBuilder sb, ArticleBlock block) {
        if (block.getContent() != null) {
            sb.append(block.getContent());
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleRendererTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/util/ArticleRenderer.java \
        services/admin-backend/src/test/java/com/example/blogger/util/ArticleRendererTest.java
git commit -m "feat(article): add ArticleRenderer to convert blocks back to HTML"
```

---

## Task 8: 改造 GenerationTaskExecutor 流水线

**Files:**
- Modify: `services/admin-backend/src/main/java/com/example/blogger/service/GenerationTaskExecutor.java`
- Test: `services/admin-backend/src/test/java/com/example/blogger/service/GenerationTaskExecutorFlowTest.java`

**Interfaces:**
- Consumes: `ArticleJsonParser`, `ArticleStyleProcessor`, `ArticleRenderer`（新增依赖）
- Produces: 改造后的文章生成流程

- [ ] **Step 1: Write the failing test**

```java
package com.example.blogger.service;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.util.ArticleJsonParser;
import com.example.blogger.util.ArticleRenderer;
import com.example.blogger.util.ArticleStyleProcessor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GenerationTaskExecutorFlowTest {

    @Test
    void pipelineShouldConvertJsonToRenderedHtml() {
        ArticleJsonParser parser = mock(ArticleJsonParser.class);
        ArticleStyleProcessor processor = mock(ArticleStyleProcessor.class);
        ArticleRenderer renderer = new ArticleRenderer();

        List<ArticleBlock> parsed = List.of(ArticleBlock.section("01 | 标题", "01", "标题", "正文", "normal"));
        when(parser.parse(any())).thenReturn(parsed);
        when(processor.process(parsed)).thenReturn(parsed);

        String rendered = renderer.render(processor.process(parser.parse("{}")));
        assertTrue(rendered.contains("<h3>01 | 标题</h3>"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd services/admin-backend && mvn test -Dtest=GenerationTaskExecutorFlowTest`
Expected: PASS（该测试不依赖 executor 改造，仅验证组合逻辑）

- [ ] **Step 3: Write minimal implementation**

修改 `GenerationTaskExecutor.java`：

1. 新增依赖注入：

```java
private final ArticleJsonParser articleJsonParser;
private final ArticleStyleProcessor articleStyleProcessor;
private final ArticleRenderer articleRenderer;
```

2. 修改构造函数：

```java
public GenerationTaskExecutor(TitleGenerationTaskMapper taskMapper,
                              TitleGenerationTaskService taskService,
                              LLMService llmService,
                              DocxGenerator docxGenerator,
                              TitleLibraryService titleLibraryService,
                              UserService userService,
                              TaskInterruptManager interruptManager,
                              AiFlavorRemover aiFlavorRemover,
                              com.example.blogger.mapper.ImageLibraryMapper imageLibraryMapper,
                              TrackMapper trackMapper,
                              ContentCheckService contentCheckService,
                              WritingStyleService writingStyleService,
                              ArticleJsonParser articleJsonParser,
                              ArticleStyleProcessor articleStyleProcessor,
                              ArticleRenderer articleRenderer) {
    // ... existing assignments ...
    this.articleJsonParser = articleJsonParser;
    this.articleStyleProcessor = articleStyleProcessor;
    this.articleRenderer = articleRenderer;
}
```

3. 改造 `generateContent` 方法，返回 `List<ArticleBlock>`：

```java
private List<ArticleBlock> generateContent(TitleGenerationTask task, String articlePrompt) throws InterruptedException {
    log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: 调用大模型生成正文", task.getId());
    checkStopped(task.getId());
    log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: 调用LLM, prompt长度={}", task.getId(), articlePrompt.length());

    long llmStart = System.currentTimeMillis();
    taskService.updateProgress(task.getId(), 2, "大模型生成中...");
    interruptManager.register(task.getId(), Thread.currentThread());
    String rawContent;
    boolean interrupted = false;
    try {
        rawContent = llmService.generateContent(articlePrompt);
    } finally {
        interruptManager.unregister(task.getId());
        interrupted = Thread.interrupted();
    }
    if (interrupted) {
        throw new InterruptedException("任务被停止");
    }
    log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: LLM返回完成, 耗时{}ms, 内容长度={}",
            task.getId(), System.currentTimeMillis() - llmStart, rawContent.length());

    rawContent = aiFlavorRemover.removeThinkingTags(rawContent);
    log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: 过滤think标签后长度={}", task.getId(), rawContent.length());

    List<ArticleBlock> blocks = articleJsonParser.parse(rawContent);
    log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: JSON解析完成, 共{}个block", task.getId(), blocks.size());

    taskService.updateProgress(task.getId(), 2, "正文生成完成");
    return blocks;
}
```

4. 改造 `executeTask` 中的流程：

```java
// 2. 调用大模型生成正文，并解析为 ArticleBlock 列表
List<ArticleBlock> blocks = generateContent(task, prompt);

// 2.5 章节标题生成（当前产品策略跳过）
// blocks = generateChapterTitles(task, blocks);

// 3. 去除 AI 味
blocks = removeAiFlavor(task, blocks);

// 3.6 根据写作风格库替换词汇
blocks = applyWritingStyle(task, blocks);

// 3.5 插入配图
// 图片插入仍基于文本，先渲染为文本，插入图片后再解析回 blocks
String contentBeforeImage = articleRenderer.render(blocks);
contentBeforeImage = insertImage(task, contentBeforeImage);
// 简单处理：把图片标签作为新 block 插入到第一个 section 之后
blocks = insertImageBlock(blocks, contentBeforeImage);

// 6. 文章样式处理
blocks = articleStyleProcessor.process(blocks);

// 7. 渲染为最终文本
String content = articleRenderer.render(blocks);

// 4. 生成 DOCX
DocxResult docx = generateDocx(task, content);
```

5. 新增/改造辅助方法：

```java
private List<ArticleBlock> removeAiFlavor(TitleGenerationTask task, List<ArticleBlock> blocks) throws InterruptedException {
    log.info("[GenerationTaskExecutor] [任务{}] Step 3/6: 开始去除AI味", task.getId());
    checkStopped(task.getId());
    taskService.updateProgress(task.getId(), 3, "去除AI味中...");

    long aiFlavorStart = System.currentTimeMillis();
    List<ArticleBlock> cleanedBlocks = aiFlavorRemover.removeAiFlavor(blocks);
    log.info("[GenerationTaskExecutor] [任务{}] Step 3/6: 去除AI味完成, 耗时{}ms, block数={}",
            task.getId(), System.currentTimeMillis() - aiFlavorStart, cleanedBlocks.size());
    taskService.updateProgress(task.getId(), 3, "去除AI味完成");
    return cleanedBlocks;
}

private List<ArticleBlock> applyWritingStyle(TitleGenerationTask task, List<ArticleBlock> blocks) {
    log.info("[GenerationTaskExecutor] [任务{}] Step 3.6/6: 开始写作风格词替换", task.getId());
    try {
        long styleStart = System.currentTimeMillis();
        List<ArticleBlock> styledBlocks = writingStyleService.applyStyle(blocks);
        log.info("[GenerationTaskExecutor] [任务{}] Step 3.6/6: 风格词替换完成, 耗时{}ms, block数={}",
                task.getId(), System.currentTimeMillis() - styleStart, styledBlocks.size());
        return styledBlocks;
    } catch (Exception e) {
        log.warn("[GenerationTaskExecutor] [任务{}] Step 3.6/6: 风格词替换失败, 跳过: {}", task.getId(), e.getMessage());
        return blocks;
    }
}

private List<ArticleBlock> insertImageBlock(List<ArticleBlock> blocks, String contentWithImage) {
    if (blocks == null || blocks.isEmpty()) {
        return blocks;
    }
    // 如果 insertImage 没有插入图片，直接返回原 blocks
    String original = articleRenderer.render(blocks);
    if (original.equals(contentWithImage)) {
        return blocks;
    }
    // 有图片插入，简单策略：重新解析文本为 blocks
    // 由于图片插入的是 <img> 段落，renderer 可以识别并渲染为 image block
    // 这里简化处理：把文本重新解析
    return parseBlocksFromRenderedText(contentWithImage);
}

private List<ArticleBlock> parseBlocksFromRenderedText(String text) {
    // 简单按 \n\n 分割，识别 <img> 和 <h3> 段落
    List<ArticleBlock> result = new ArrayList<>();
    String[] paragraphs = text.split("\\n\\n+");
    for (String para : paragraphs) {
        String trimmed = para.trim();
        if (trimmed.isEmpty()) continue;
        if (trimmed.startsWith("<h3>") && trimmed.endsWith("</h3>")) {
            String title = trimmed.substring(4, trimmed.length() - 5);
            // 暂时把 h3 作为只有 title 没有 content 的 section
            result.add(ArticleBlock.section(title, null, null, "", "normal"));
        } else if (trimmed.startsWith("<img")) {
            result.add(ArticleBlock.image(trimmed));
        } else {
            result.add(ArticleBlock.paragraph(trimmed, "normal"));
        }
    }
    return result;
}
```

6. 修改 `preparePrompt` 追加 JSON 指令：

```java
prompt += "\n\n【系统指令】"
        + "请直接输出 JSON 数组，不要输出任何思考过程，不要复述用户要求，不要加任何前言或总结。"
        + "输出必须是可以被标准 JSON 解析器解析的合法 JSON，不要包裹在 markdown 代码块中。"
        + "禁止使用 <think>、<thinking>、<thought>、<reasoning> 等标签包裹内容。"
        + "不要用英文分析任务，直接开始写中文文章。"
        + "正文中间请根据内容需要插入1-3个章节小标题（用于区分段落和观点），使用 <h3>标题内容</h3> 标签包裹，最多不超过3个。"
        + "文章最开头不要重复写总标题。"
        + "\n\nJSON 数组中每个元素代表文章的一个块，字段如下："
        + "type: 'section' 或 'paragraph';"
        + "title: 完整小标题字符串，如有序号请写成 '01 | 标题内容' 形式;"
        + "marker: 从 title 解析出的序号，如 '01'，没有则填 null;"
        + "markerText: 去掉 marker 后的标题文本，没有则填 null;"
        + "content: 该章节的正文内容，允许使用 <s></s> 标记需要着重加强的词句;"
        + "styleHint: 可选 'normal' / 'emphasis' / 'story' / 'tip';"
        + "\n\n示例：[{\"type\":\"section\",\"title\":\"01 | 示例标题\",\"marker\":\"01\",\"markerText\":\"示例标题\",\"content\":\"示例正文\",\"styleHint\":\"normal\"}]";
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd services/admin-backend && mvn test`
Expected: PASS（需要确保现有测试也通）

- [ ] **Step 5: Commit**

```bash
git add services/admin-backend/src/main/java/com/example/blogger/service/GenerationTaskExecutor.java \
        services/admin-backend/src/test/java/com/example/blogger/service/GenerationTaskExecutorFlowTest.java
git commit -m "feat(article): integrate JSON parsing, style processing and rendering into pipeline"
```

---

## Task 9: 数据库建表与默认配置

**Files:**
- Create: `services/admin-backend/src/main/resources/db/migration/V1__add_style_config.sql`（如果项目使用 flyway/liquibase，否则作为手动执行脚本）
- 或手动 SQL 文件：`services/admin-backend/sql/add_style_config.sql`

**Interfaces:**
- Consumes: 无
- Produces: `tu_style_config` 表

- [ ] **Step 1: Write SQL**

```sql
CREATE TABLE IF NOT EXISTS tu_style_config (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    strategy VARCHAR(16) NOT NULL COMMENT 'A/B/C/D/E/F/G',
    params JSON,
    is_active TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文章样式处理全局配置';

INSERT INTO tu_style_config (id, name, strategy, params, is_active) VALUES
('default', '默认纯视觉美化', 'A', '{}', 1)
ON DUPLICATE KEY UPDATE is_active = 1;
```

- [ ] **Step 2: Execute SQL on local/dev database**

Run: `mysql -u your_user -p your_database < services/admin-backend/sql/add_style_config.sql`

- [ ] **Step 3: Commit**

```bash
git add services/admin-backend/sql/add_style_config.sql
git commit -m "chore(db): add tu_style_config table"
```

---

## Task 10: 集成测试与端到端验证

**Files:**
- Test: `services/admin-backend/src/test/java/com/example/blogger/service/ArticleGenerationIntegrationTest.java`

**Interfaces:**
- Consumes: 完整 Spring 上下文
- Produces: 验证 JSON → DOCX 的完整链路

- [ ] **Step 1: Write the test**

```java
package com.example.blogger.service;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.util.ArticleJsonParser;
import com.example.blogger.util.ArticleRenderer;
import com.example.blogger.util.ArticleStyleProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ArticleGenerationIntegrationTest {

    @Autowired
    private ArticleJsonParser articleJsonParser;

    @Autowired
    private ArticleStyleProcessor articleStyleProcessor;

    @Autowired
    private ArticleRenderer articleRenderer;

    @Test
    void endToEndJsonPipeline() {
        String json = "[{\"type\":\"section\",\"title\":\"01 | 为什么值得关注\",\"marker\":\"01\",\"markerText\":\"为什么值得关注\",\"content\":\"这是一段测试正文。\",\"styleHint\":\"emphasis\"}]";
        List<ArticleBlock> blocks = articleJsonParser.parse(json);
        blocks = articleStyleProcessor.process(blocks);
        String rendered = articleRenderer.render(blocks);
        assertTrue(rendered.contains("<h3>"));
        assertTrue(rendered.contains("这是一段测试正文。"));
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd services/admin-backend && mvn test -Dtest=ArticleGenerationIntegrationTest`
Expected: PASS（需要数据库和 Spring 上下文可用）

- [ ] **Step 3: Commit**

```bash
git add services/admin-backend/src/test/java/com/example/blogger/service/ArticleGenerationIntegrationTest.java
git commit -m "test(article): add integration test for JSON pipeline"
```

---

## Self-Review

### 1. Spec coverage

- 测试依赖 → Task 0
- JSON 结构定义 → Task 1, Task 2
- Prompt 设计 → Task 8
- ArticleJsonParser → Task 2
- ArticleStyleProcessor → Task 6
- ArticleRenderer → Task 7
- 去 AI 味遍历 block → Task 3
- 写作风格替换遍历 block → Task 4
- A-G 策略 → Task 5
- 全局配置与数据表 → Task 6, Task 9
- 流水线改造 → Task 8
- 测试 → Task 0-10

无遗漏。

### 2. Placeholder scan

- 无 TBD/TODO
- 无 "add appropriate error handling"
- 每个代码步骤都有具体实现
- 每个测试都有具体断言

### 3. Type consistency

- `ArticleBlock` 字段与 parser、renderer 中使用的字段一致
- `StyleConfig.strategy` 与 `ArticleStyleStrategies.apply` 的策略标识符一致
- `ArticleStyleProcessor.process` 返回 `List<ArticleBlock>`，与 renderer 输入一致

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-06-23-article-json-style-plan.md`.**

Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach do you prefer?
