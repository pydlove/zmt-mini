package com.example.blogger.service;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.util.ArticleJsonParser;
import com.example.blogger.util.ArticleRenderer;
import com.example.blogger.util.ArticleStyleProcessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
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

    @Test
    void parseBlocksFromRenderedTextShouldPreserveMarkerAndMarkerText() throws Exception {
        // Given: original blocks with section title="01 | 标题", marker="01", markerText="标题"
        List<ArticleBlock> originalBlocks = new ArrayList<>();
        originalBlocks.add(ArticleBlock.section("01 | 标题", "01", "标题", "这是第一段的正文内容", "normal"));

        // Render the blocks to HTML (simulating what happens before image insertion)
        ArticleRenderer renderer = new ArticleRenderer();
        String renderedText = renderer.render(originalBlocks);
        // renderedText will contain: <h3>01 | 标题</h3>\n\n这是第一段的正文内容

        // When: re-parse the rendered text using parseBlocksFromRenderedText
        Method method = GenerationTaskExecutor.class.getDeclaredMethod(
                "parseBlocksFromRenderedText", String.class, List.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ArticleBlock> result = (List<ArticleBlock>) method.invoke(null, renderedText, originalBlocks);

        // Then: the resulting section block should have the same marker and markerText
        assertEquals(1, result.size());
        ArticleBlock resultBlock = result.get(0);
        assertEquals(ArticleBlock.TYPE_SECTION, resultBlock.getType());
        assertEquals("01 | 标题", resultBlock.getTitle());
        assertEquals("01", resultBlock.getMarker());
        assertEquals("标题", resultBlock.getMarkerText());
        assertEquals("这是第一段的正文内容", resultBlock.getContent());
    }
}
