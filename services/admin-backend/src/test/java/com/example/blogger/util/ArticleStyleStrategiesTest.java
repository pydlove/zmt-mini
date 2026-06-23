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

    @Test
    void shouldMapTemplates() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("故事", "01", "故事", "正文", "story"),
            ArticleBlock.section("提示", "02", "提示", "正文", "tip"),
            ArticleBlock.section("强调", "03", "强调", "正文", "emphasis"),
            ArticleBlock.section("默认", "04", "默认", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "C");
        assertEquals("story-card", result.get(0).getRenderMeta().get("template"));
        assertEquals("tip-card", result.get(1).getRenderMeta().get("template"));
        assertEquals("emphasis-card", result.get(2).getRenderMeta().get("template"));
        assertEquals("knowledge-card", result.get(3).getRenderMeta().get("template"));
    }

    @Test
    void shouldApplyDownstreamDifferentiation() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "D");
        assertEquals("minimal", result.get(0).getRenderMeta().get("docxStyle"));
        assertEquals("social", result.get(0).getRenderMeta().get("imagePostStyle"));
    }

    @Test
    void shouldApplyContentGrading() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("引言", "01", "引言", "正文1", "normal"),
            ArticleBlock.section("正文", "02", "正文", "正文2", "normal"),
            ArticleBlock.section("行动", "03", "行动", "正文3", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "E");
        assertEquals("intro-short", result.get(0).getRenderMeta().get("contentGrade"));
        assertEquals("core-long", result.get(1).getRenderMeta().get("contentGrade"));
        assertEquals("action", result.get(2).getRenderMeta().get("contentGrade"));
    }

    @Test
    void shouldApplyContentGradingWithNonSectionBlocks() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("引言", "01", "引言", "正文1", "normal"),
            ArticleBlock.paragraph("过渡段落", "normal"),
            ArticleBlock.section("正文", "02", "正文", "正文2", "normal"),
            ArticleBlock.paragraph("过渡段落", "normal"),
            ArticleBlock.section("行动", "03", "行动", "正文3", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "E");
        assertEquals("intro-short", result.get(0).getRenderMeta().get("contentGrade"));
        assertEquals("core-long", result.get(2).getRenderMeta().get("contentGrade"));
        assertEquals("action", result.get(4).getRenderMeta().get("contentGrade"));
    }

    @Test
    void shouldMapTemplatesWithNullHint() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("默认", "01", "默认", "正文", null)
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "C");
        assertEquals("knowledge-card", result.get(0).getRenderMeta().get("template"));
    }

    @Test
    void shouldGenerateToc() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题A", "01", "标题A", "正文", "normal"),
            ArticleBlock.section("标题B", "02", "标题B", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "F");
        assertEquals(ArticleBlock.TYPE_PARAGRAPH, result.get(0).getType());
        assertTrue(result.get(0).getContent().contains("本文目录"));
        assertTrue(result.get(0).getContent().contains("01 · 标题A"));
        assertTrue(result.get(0).getContent().contains("02 · 标题B"));
        assertEquals(ArticleBlock.TYPE_SECTION, result.get(1).getType());
    }

    @Test
    void shouldGenerateAiImageHints() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题", "01", "关键词", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "G");
        assertEquals("关键词", result.get(0).getRenderMeta().get("imageKeyword"));
    }

    @Test
    void shouldHandleNullBlocks() {
        List<ArticleBlock> result = ArticleStyleStrategies.apply(null, "A");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleEmptyBlocks() {
        List<ArticleBlock> result = ArticleStyleStrategies.apply(List.of(), "B");
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleNullStrategy() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, null);
        assertEquals("标题", result.get(0).getTitle());
    }

    @Test
    void shouldHandleLowercaseStrategy() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "a");
        assertEquals("标题", result.get(0).getTitle());
    }

    @Test
    void shouldDefaultToVisualBeautifyForUnknownStrategy() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = ArticleStyleStrategies.apply(blocks, "Z");
        assertEquals("标题", result.get(0).getTitle());
    }

    @Test
    void shouldNotMutateOriginalBlocks() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("第一章", "第一章", "", "正文", "normal")
        );
        ArticleStyleStrategies.apply(blocks, "B");
        assertEquals("第一章", blocks.get(0).getMarker());
    }
}
