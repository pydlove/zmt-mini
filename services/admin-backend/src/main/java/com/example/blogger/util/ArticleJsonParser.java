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
