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
    void shouldPassThroughBlocksWhenServiceReturnsNull() {
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

    @Test
    void shouldReturnNullWhenBlocksIsNull() {
        StyleConfigService configService = mock(StyleConfigService.class);
        ArticleStyleProcessor processor = new ArticleStyleProcessor(configService);
        List<ArticleBlock> result = processor.process(null);
        assertNull(result);
    }

    @Test
    void shouldReturnEmptyListWhenBlocksIsEmpty() {
        StyleConfigService configService = mock(StyleConfigService.class);
        ArticleStyleProcessor processor = new ArticleStyleProcessor(configService);
        List<ArticleBlock> blocks = List.of();
        List<ArticleBlock> result = processor.process(blocks);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFallbackToOriginalBlocksOnRuntimeException() {
        StyleConfigService configService = mock(StyleConfigService.class);
        when(configService.findActive()).thenThrow(new RuntimeException("DB error"));
        ArticleStyleProcessor processor = new ArticleStyleProcessor(configService);

        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = processor.process(blocks);
        assertEquals(blocks, result);
    }

    @Test
    void shouldFallbackToOriginalBlocksOnIllegalArgumentException() {
        StyleConfigService configService = mock(StyleConfigService.class);
        when(configService.findActive()).thenThrow(new IllegalArgumentException("unknown strategy"));
        ArticleStyleProcessor processor = new ArticleStyleProcessor(configService);

        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("标题", "01", "标题", "正文", "normal")
        );
        List<ArticleBlock> result = processor.process(blocks);
        assertEquals(blocks, result);
    }
}
