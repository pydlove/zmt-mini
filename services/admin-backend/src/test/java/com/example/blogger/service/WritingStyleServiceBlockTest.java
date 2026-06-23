package com.example.blogger.service;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.mapper.WritingStyleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WritingStyleServiceBlockTest {

    @Test
    void shouldReturnEmptyListWhenInputIsEmpty() {
        WritingStyleMapper mapper = mock(WritingStyleMapper.class);
        when(mapper.findAll()).thenReturn(List.of());
        WritingStyleService service = new WritingStyleService(mapper);
        assertTrue(service.applyStyle(List.of()).isEmpty());
    }

    @Test
    void shouldProcessBlockContent() {
        WritingStyleMapper mapper = mock(WritingStyleMapper.class);
        when(mapper.findAll()).thenReturn(List.of());
        WritingStyleService service = new WritingStyleService(mapper);

        List<ArticleBlock> blocks = List.of(
            ArticleBlock.paragraph("这是一段文本。", "normal")
        );
        List<ArticleBlock> result = service.applyStyle(blocks);
        assertEquals("这是一段文本。", result.get(0).getContent());
    }
}
