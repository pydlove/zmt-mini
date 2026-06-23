package com.example.blogger.service;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.util.ArticleJsonParser;
import com.example.blogger.util.ArticleRenderer;
import com.example.blogger.util.ArticleStyleProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ArticleGenerationIntegrationTest {

    @Autowired
    private ArticleJsonParser articleJsonParser;

    @Autowired
    private ArticleStyleProcessor articleStyleProcessor;

    @Autowired
    private ArticleRenderer articleRenderer;

    @MockBean
    private TitleLibraryService titleLibraryService;

    @Test
    void endToEndJsonPipeline() {
        String json = "[{\"type\":\"section\",\"title\":\"01 | 为什么值得关注\",\"marker\":\"01\",\"markerText\":\"为什么值得关注\",\"content\":\"这是一段测试正文。\",\"styleHint\":\"emphasis\"}]";
        List<ArticleBlock> blocks = articleJsonParser.parse(json);
        blocks = articleStyleProcessor.process(blocks);
        String rendered = articleRenderer.render(blocks);
        assertTrue(rendered.contains("<h3>"));
        assertTrue(rendered.contains("这是一段测试正文。"));
    }
}
