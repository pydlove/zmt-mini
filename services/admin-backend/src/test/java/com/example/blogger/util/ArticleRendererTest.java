package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArticleRendererTest {

    @Test
    void shouldRenderSectionAndParagraph() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("01 | 标题", "01", "标题", "正文内容", "normal"),
            ArticleBlock.paragraph("第二段。", "normal")
        );
        ArticleRenderer renderer = new ArticleRenderer();
        String output = renderer.render(blocks);
        assertTrue(output.contains("<h3>01 | 标题</h3>"));
        assertTrue(output.contains("正文内容"));
        assertTrue(output.contains("第二段。"));
    }

    @Test
    void shouldRenderImageBlock() {
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.image("<img src=\"/uploads/images/test.jpg\">")
        );
        ArticleRenderer renderer = new ArticleRenderer();
        String output = renderer.render(blocks);
        assertTrue(output.contains("<img src=\"/uploads/images/test.jpg\">"));
    }
}
