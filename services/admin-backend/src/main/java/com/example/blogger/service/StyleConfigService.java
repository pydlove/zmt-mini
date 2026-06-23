package com.example.blogger.service;

import com.example.blogger.entity.StyleConfig;
import com.example.blogger.mapper.StyleConfigMapper;
import org.springframework.stereotype.Service;

@Service
public class StyleConfigService {

    private final StyleConfigMapper styleConfigMapper;

    public StyleConfigService(StyleConfigMapper styleConfigMapper) {
        this.styleConfigMapper = styleConfigMapper;
    }

    public StyleConfig findActive() {
        StyleConfig active = styleConfigMapper.findActive();
        if (active == null) {
            active = createDefault();
        }
        return active;
    }

    private StyleConfig createDefault() {
        StyleConfig config = new StyleConfig();
        config.setId("DEFAULT");
        config.setName("默认纯视觉美化");
        config.setStrategy("A");
        config.setIsActive(1);
        return config;
    }

    public boolean isDefault(StyleConfig config) {
        return config != null && "DEFAULT".equals(config.getId());
    }
}
