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
