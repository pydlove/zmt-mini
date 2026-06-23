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
