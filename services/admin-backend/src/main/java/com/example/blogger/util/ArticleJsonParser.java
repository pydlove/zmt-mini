package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.exception.ArticleParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArticleJsonParser {

    private static final Logger log = LoggerFactory.getLogger(ArticleJsonParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final java.util.regex.Pattern MARKER_PATTERN = java.util.regex.Pattern.compile("^(\\d+)\\s*([|/\\-·.])\\s*(.+)$");

    public List<ArticleBlock> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ArticleParseException("LLM 返回内容为空");
        }

        String trimmed = raw.trim();
        ArrayNode array = tryParseArray(trimmed);

        // 解析失败时尝试从文本中提取 JSON 数组，应对 LLM 输出前置自然语言（如"让我分析规则..."）的场景
        if (array == null) {
            String extracted = extractJsonArray(trimmed);
            if (extracted != null) {
                log.warn("[ArticleJsonParser] LLM 输出包含非 JSON 前置文字，提取 JSON 部分: 原始长度={}, 提取后长度={}",
                        trimmed.length(), extracted.length());
                array = tryParseArray(extracted);
            }
        }

        // 解析失败时尝试尾部截断恢复，应对 LLM 输出被 max_tokens 截断的场景
        if (array == null && !trimmed.endsWith("]")) {
            String recovered = tryRecoverTruncatedJson(trimmed);
            if (recovered != null) {
                log.warn("[ArticleJsonParser] LLM 输出疑似被截断，截取到最后一个完整对象: 原始长度={}, 恢复后长度={}",
                        trimmed.length(), recovered.length());
                array = tryParseArray(recovered);
            }
        }

        if (array == null) {
            throw new ArticleParseException("LLM 返回不是合法 JSON: " + summarize(trimmed));
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

    /**
     * 尝试将字符串解析为 JSON 数组。失败返回 null，不抛异常。
     */
    private ArrayNode tryParseArray(String s) {
        try {
            JsonNode root = MAPPER.readTree(s);
            if (root.isArray()) {
                return (ArrayNode) root;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从被截断的 JSON 中恢复：找到最后一个完整顶层对象的结尾，截断后补上 ']' 关闭数组。
     * <p>
     * 用于应对 LLM 在 max_tokens 限制下输出被截断的场景（如 content 字段值在中间被切掉）。
     *
     * @param s LLM 原始输出
     * @return 截断后能解析的 JSON 字符串；找不到完整对象则返回 null
     */
    private String tryRecoverTruncatedJson(String s) {
        boolean inString = false;
        boolean escaped = false;
        int braceDepth = 0;       // 嵌套对象深度
        int bracketDepth = 0;     // 数组深度（顶层 [ = 1）
        int lastValidObjectEnd = -1;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (inString && c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '[') {
                bracketDepth++;
            } else if (c == ']') {
                bracketDepth--;
            } else if (c == '{') {
                braceDepth++;
            } else if (c == '}') {
                braceDepth--;
                // braceDepth 回到 0 且仍在顶层数组内时，记录该位置为完整对象结尾
                if (braceDepth == 0 && bracketDepth >= 1) {
                    lastValidObjectEnd = i;
                }
            }
        }

        if (lastValidObjectEnd < 0) {
            return null;
        }

        String truncated = s.substring(0, lastValidObjectEnd + 1);
        if (!truncated.endsWith("]")) {
            truncated += "]";
        }
        return truncated;
    }

    /**
     * 从包含非 JSON 前置文字的文本中提取 JSON 数组。
     * 找到第一个 '[' 和最后一个匹配的 ']'，截取中间部分。
     *
     * @param s LLM 原始输出（可能包含前置自然语言）
     * @return 提取出的 JSON 数组字符串；找不到则返回 null
     */
    private String extractJsonArray(String s) {
        int start = s.indexOf('[');
        if (start < 0) {
            return null;
        }
        // 从末尾找最后一个 ']'，取 start 到 end 之间的内容
        int end = s.lastIndexOf(']');
        if (end < start) {
            return null;
        }
        String candidate = s.substring(start, end + 1);
        // 简单校验：长度不能太短（至少要有 [{}]）
        if (candidate.length() < 4) {
            return null;
        }
        return candidate;
    }

    /**
     * 截取前 200 字符用于错误信息展示
     */
    private String summarize(String raw) {
        if (raw == null) return "null";
        return raw.length() > 200 ? raw.substring(0, 200) + "..." : raw;
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
