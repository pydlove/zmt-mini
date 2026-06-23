package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.entity.StyleConfig;
import com.example.blogger.service.StyleConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArticleStyleProcessor {

    private static final Logger log = LoggerFactory.getLogger(ArticleStyleProcessor.class);

    private final StyleConfigService styleConfigService;

    public ArticleStyleProcessor(StyleConfigService styleConfigService) {
        this.styleConfigService = styleConfigService;
    }

    public List<ArticleBlock> process(List<ArticleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        try {
            StyleConfig config = styleConfigService.findActive();
            String strategy = config != null && config.getStrategy() != null ? config.getStrategy().toUpperCase() : "A";
            log.info("[ArticleStyleProcessor] 应用样式策略: {}", strategy);
            return ArticleStyleStrategies.apply(blocks, strategy);
        } catch (Exception e) {
            log.warn("[ArticleStyleProcessor] 样式处理失败，透传原始 block: {}", e.getMessage());
            return blocks;
        }
    }
}
