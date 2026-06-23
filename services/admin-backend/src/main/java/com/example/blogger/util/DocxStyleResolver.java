package com.example.blogger.util;

import com.example.blogger.entity.ExportTemplate;
import com.example.blogger.mapper.ExportTemplateMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 根据用户指定的模板名称或默认模板，解析出 {@link DocxStyleConfig}。
 */
@Component
public class DocxStyleResolver {

    private static final Logger log = LoggerFactory.getLogger(DocxStyleResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExportTemplateMapper exportTemplateMapper;

    public DocxStyleResolver(ExportTemplateMapper exportTemplateMapper) {
        this.exportTemplateMapper = exportTemplateMapper;
    }

    /**
     * 解析指定名称的模板；找不到则返回默认配置。
     */
    public DocxStyleConfig resolve(String templateName) {
        if (templateName != null && !templateName.isEmpty()) {
            ExportTemplate template = exportTemplateMapper.findByName(templateName);
            if (template != null && template.getConfig() != null && !template.getConfig().isEmpty()) {
                return parse(template.getConfig());
            }
        }
        ExportTemplate defaultTpl = exportTemplateMapper.findDefault();
        if (defaultTpl != null && defaultTpl.getConfig() != null && !defaultTpl.getConfig().isEmpty()) {
            return parse(defaultTpl.getConfig());
        }
        log.warn("[DocxStyleResolver] 未找到可用模板，使用硬编码默认配置");
        return new DocxStyleConfig();
    }

    /**
     * 返回默认模板配置；没有任何模板时兜底到硬编码默认值。
     */
    public DocxStyleConfig resolveDefault() {
        return resolve(null);
    }

    private DocxStyleConfig parse(String configJson) {
        try {
            return MAPPER.readValue(configJson, DocxStyleConfig.class);
        } catch (Exception e) {
            log.error("[DocxStyleResolver] 解析模板配置失败: {}", e.getMessage());
            return new DocxStyleConfig();
        }
    }
}
