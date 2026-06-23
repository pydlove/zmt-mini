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

    @Test
    void shouldRejectMarkerMismatch() {
        String json = "[{\"type\":\"section\",\"title\":\"01 | Foo\",\"marker\":\"02\",\"markerText\":\"Foo\",\"content\":\"正文\"}]";
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("marker 与 title 不一致"));
    }

    @Test
    void shouldRejectMarkerTextMismatch() {
        String json = "[{\"type\":\"section\",\"title\":\"01 | Foo\",\"marker\":\"01\",\"markerText\":\"Bar\",\"content\":\"正文\"}]";
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("markerText 与 title 不一致"));
    }

    @Test
    void shouldRejectSectionWithoutTitle() {
        String json = "[{\"type\":\"section\",\"marker\":\"01\",\"markerText\":\"Foo\",\"content\":\"正文\"}]";
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("section 缺少 title"));
    }

    @Test
    void shouldRejectMissingType() {
        String json = "[{\"content\":\"正文\"}]";
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("缺少 type"));
    }

    @Test
    void shouldRejectMissingContent() {
        String json = "[{\"type\":\"paragraph\"}]";
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("缺少 content"));
    }

    @Test
    void shouldRejectNonArrayRoot() {
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse("{}"));
        assertTrue(ex.getMessage().contains("不是 JSON 数组"));
    }

    @Test
    void shouldRejectNullInput() {
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse(null));
        assertTrue(ex.getMessage().contains("为空"));
    }

    @Test
    void shouldRejectBlankInput() {
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse("   "));
        assertTrue(ex.getMessage().contains("为空"));
    }

    @Test
    void shouldRejectNonObjectElementInArray() {
        String json = "[\"not an object\"]";
        ArticleJsonParser parser = new ArticleJsonParser();
        ArticleParseException ex = assertThrows(ArticleParseException.class, () -> parser.parse(json));
        assertTrue(ex.getMessage().contains("不是对象"));
    }
}
