package com.example.blogger.controller;

import com.example.blogger.entity.ExportTemplate;
import com.example.blogger.entity.Result;
import com.example.blogger.service.ExportTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/export-templates")
@CrossOrigin(origins = "*")
public class ExportTemplateController {

    private final ExportTemplateService exportTemplateService;

    public ExportTemplateController(ExportTemplateService exportTemplateService) {
        this.exportTemplateService = exportTemplateService;
    }

    @GetMapping
    public Result<List<ExportTemplate>> list() {
        return Result.ok(exportTemplateService.list());
    }

    @GetMapping("/{id}")
    public Result<ExportTemplate> getById(@PathVariable String id) {
        ExportTemplate template = exportTemplateService.getById(id);
        if (template == null) {
            return Result.error("模板不存在");
        }
        return Result.ok(template);
    }

    @PostMapping
    public Result<ExportTemplate> save(@RequestBody ExportTemplate template) {
        try {
            return Result.ok(exportTemplateService.save(template));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<ExportTemplate> update(@PathVariable String id, @RequestBody ExportTemplate template) {
        ExportTemplate existing = exportTemplateService.getById(id);
        if (existing == null) {
            return Result.error("模板不存在");
        }
        template.setId(id);
        try {
            return Result.ok(exportTemplateService.save(template));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        ExportTemplate existing = exportTemplateService.getById(id);
        if (existing == null) {
            return Result.error("模板不存在");
        }
        exportTemplateService.delete(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/set-default")
    public Result<Void> setDefault(@PathVariable String id) {
        try {
            exportTemplateService.setDefault(id);
            return Result.ok(null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
