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
