package com.example.blogger.service;

import com.example.blogger.entity.ExportTemplate;
import com.example.blogger.mapper.ExportTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExportTemplateService {

    private final ExportTemplateMapper exportTemplateMapper;

    public ExportTemplateService(ExportTemplateMapper exportTemplateMapper) {
        this.exportTemplateMapper = exportTemplateMapper;
    }

    public List<ExportTemplate> list() {
        return exportTemplateMapper.findAll();
    }

    public ExportTemplate getById(String id) {
        return exportTemplateMapper.findById(id);
    }

    public ExportTemplate getByName(String name) {
        return exportTemplateMapper.findByName(name);
    }

    public ExportTemplate getDefault() {
        ExportTemplate tpl = exportTemplateMapper.findDefault();
        if (tpl == null) {
            List<ExportTemplate> all = exportTemplateMapper.findAll();
            if (!all.isEmpty()) {
                tpl = all.get(0);
            }
        }
        return tpl;
    }

    @Transactional
    public ExportTemplate save(ExportTemplate template) {
        if (template.getId() == null || template.getId().isEmpty()) {
            template.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (template.getType() == null || template.getType().isEmpty()) {
            template.setType("docx");
        }
        if (template.getIsDefault() == null) {
            template.setIsDefault(0);
        }
        if (template.getIsDeleted() == null) {
            template.setIsDeleted(0);
        }

        if (template.getId() != null && !template.getId().isEmpty()) {
            ExportTemplate existing = exportTemplateMapper.findById(template.getId());
            if (existing != null) {
                if (Integer.valueOf(1).equals(template.getIsDefault())) {
                    exportTemplateMapper.clearDefault();
                }
                exportTemplateMapper.update(template);
                return template;
            }
        }

        if (Integer.valueOf(1).equals(template.getIsDefault())) {
            exportTemplateMapper.clearDefault();
        }
        exportTemplateMapper.insert(template);
        return template;
    }

    @Transactional
    public void delete(String id) {
        exportTemplateMapper.delete(id);
    }

    @Transactional
    public void setDefault(String id) {
        ExportTemplate template = exportTemplateMapper.findById(id);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        exportTemplateMapper.clearDefault();
        template.setIsDefault(1);
        exportTemplateMapper.update(template);
    }
}
