package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiFlavorRemoverBlockTest {

    @Test
    void shouldProcessBlocks() {
        // This is a lightweight test; AiFlavorRemover depends on database and Python script.
        // We only verify the method signature and that it doesn't throw.
        AiFlavorRemover remover = new AiFlavorRemover(null);
        List<ArticleBlock> blocks = List.of(
            ArticleBlock.section("01 | 标题", "01", "标题", "这是一段文本。", "normal")
        );
        List<ArticleBlock> result = remover.removeAiFlavor(blocks);
        assertEquals(1, result.size());
        assertEquals("这是一段文本。", result.get(0).getContent());
    }
}
