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
        String strategy = "A";
        try {
            StyleConfig config = styleConfigService.findActive();
            if (config != null && config.getStrategy() != null) {
                strategy = config.getStrategy().toUpperCase();
            }
        } catch (Exception ignored) {
        }
        return process(blocks, strategy);
    }

    /**
     * 使用指定的策略处理 blocks（不读取数据库）。
     * <p>
     * 适用于：上游已读取 StyleConfig，希望避免重复 IO；或测试场景。
     */
    public List<ArticleBlock> process(List<ArticleBlock> blocks, String strategy) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        String s = strategy != null ? strategy.toUpperCase() : "A";
        try {
            log.info("[ArticleStyleProcessor] 应用样式策略: {}", s);
            return ArticleStyleStrategies.apply(blocks, s);
        } catch (Exception e) {
            log.warn("[ArticleStyleProcessor] 样式处理失败，透传原始 block: {}", e.getMessage());
            return blocks;
        }
    }
}
