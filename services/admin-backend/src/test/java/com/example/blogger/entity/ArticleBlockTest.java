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
