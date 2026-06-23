package com.example.blogger.service;

import com.example.blogger.entity.StyleConfig;
import com.example.blogger.mapper.StyleConfigMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StyleConfigServiceTest {

    @Test
    void shouldReturnDefaultConfigWhenMapperReturnsNull() {
        StyleConfigMapper mapper = mock(StyleConfigMapper.class);
        when(mapper.findActive()).thenReturn(null);

        StyleConfigService service = new StyleConfigService(mapper);
        StyleConfig result = service.findActive();

        assertNotNull(result);
        assertEquals("A", result.getStrategy());
        assertEquals("默认纯视觉美化", result.getName());
        assertEquals(Integer.valueOf(1), result.getIsActive());
        assertEquals("DEFAULT", result.getId());
        verify(mapper, never()).insert(any(StyleConfig.class));
    }

    @Test
    void shouldReturnTrueForDefaultConfig() {
        StyleConfigMapper mapper = mock(StyleConfigMapper.class);
        when(mapper.findActive()).thenReturn(null);

        StyleConfigService service = new StyleConfigService(mapper);
        StyleConfig result = service.findActive();

        assertTrue(service.isDefault(result));
    }

    @Test
    void shouldReturnFalseForNonDefaultConfig() {
        StyleConfig config = new StyleConfig();
        config.setId("123");
        config.setStrategy("C");
        config.setName("模板映射");
        config.setIsActive(1);

        StyleConfigMapper mapper = mock(StyleConfigMapper.class);
        when(mapper.findActive()).thenReturn(config);

        StyleConfigService service = new StyleConfigService(mapper);
        StyleConfig result = service.findActive();

        assertFalse(service.isDefault(result));
    }

    @Test
    void shouldReturnActiveConfigFromMapper() {
        StyleConfig config = new StyleConfig();
        config.setStrategy("C");
        config.setName("模板映射");
        config.setIsActive(1);

        StyleConfigMapper mapper = mock(StyleConfigMapper.class);
        when(mapper.findActive()).thenReturn(config);

        StyleConfigService service = new StyleConfigService(mapper);
        StyleConfig result = service.findActive();

        assertEquals("C", result.getStrategy());
        assertEquals("模板映射", result.getName());
        verify(mapper, never()).insert(any());
    }
}
