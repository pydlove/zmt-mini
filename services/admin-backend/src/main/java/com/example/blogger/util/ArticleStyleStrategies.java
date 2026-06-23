package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;

import java.util.ArrayList;
import java.util.List;

public class ArticleStyleStrategies {

    public static List<ArticleBlock> apply(List<ArticleBlock> blocks, String strategy) {
        if (blocks == null) {
            return List.of();
        }
        String upper = strategy == null ? "A" : strategy.toUpperCase();
        return switch (upper) {
            case "A" -> visualBeautify(blocks);
            case "B" -> autoNumbering(blocks);
            case "C" -> templateMapping(blocks);
            case "D" -> downstreamDifferentiation(blocks);
            case "E" -> contentGrading(blocks);
            case "F" -> generateToc(blocks);
            case "G" -> aiImageHint(blocks);
            default -> visualBeautify(blocks);
        };
    }

    // A. 纯视觉美化：保持 title 不变，由 renderer 负责展示样式
    private static List<ArticleBlock> visualBeautify(List<ArticleBlock> blocks) {
        return copyBlocks(blocks);
    }

    // B. 自动连续编号：对所有 section 重新编号
    private static List<ArticleBlock> autoNumbering(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        int sectionIndex = 0;
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                sectionIndex++;
                String marker = String.format("%02d", sectionIndex);
                String markerText = block.getMarkerText() != null && !block.getMarkerText().isEmpty()
                        ? block.getMarkerText()
                        : (block.getTitle() != null ? block.getTitle() : "");
                block.setMarker(marker);
                block.setTitle(marker + " | " + markerText);
                block.setMarkerText(markerText);
            }
        }
        return result;
    }

    // C. 模板映射：为每个 section 写入模板标记
    private static List<ArticleBlock> templateMapping(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                String hint = block.getStyleHint();
                String template = switch (hint) {
                    case "story" -> "story-card";
                    case "tip" -> "tip-card";
                    case "emphasis" -> "emphasis-card";
                    default -> "knowledge-card";
                };
                block.getRenderMeta().put("template", template);
            }
        }
        return result;
    }

    // D. 下游差异化：标记渲染目标
    private static List<ArticleBlock> downstreamDifferentiation(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        for (ArticleBlock block : result) {
            block.getRenderMeta().put("docxStyle", "minimal");
            block.getRenderMeta().put("imagePostStyle", "social");
        }
        return result;
    }

    // E. 内容分级控制：写入策略元数据，不修改现有内容
    private static List<ArticleBlock> contentGrading(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        int sectionIndex = 0;
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                sectionIndex++;
                String grade = sectionIndex == 1 ? "intro-short" : (sectionIndex == result.size() ? "action" : "core-long");
                block.getRenderMeta().put("contentGrade", grade);
            }
        }
        return result;
    }

    // F. 自动生成目录：在开头插入目录 block
    private static List<ArticleBlock> generateToc(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = new ArrayList<>();
        StringBuilder toc = new StringBuilder("本文目录：\n");
        int index = 0;
        for (ArticleBlock block : blocks) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                index++;
                String title = block.getMarkerText() != null && !block.getMarkerText().isEmpty()
                        ? block.getMarkerText()
                        : block.getTitle();
                toc.append(String.format("%02d \u00b7 %s\n", index, title));
            }
        }
        ArticleBlock tocBlock = ArticleBlock.paragraph(toc.toString().trim(), "normal");
        result.add(tocBlock);
        result.addAll(copyBlocks(blocks));
        return result;
    }

    // G. AI 配图提示：为每个 section 生成配图关键词
    private static List<ArticleBlock> aiImageHint(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = copyBlocks(blocks);
        for (ArticleBlock block : result) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType())) {
                String keyword = (block.getMarkerText() != null ? block.getMarkerText() : block.getTitle());
                block.getRenderMeta().put("imageKeyword", keyword);
            }
        }
        return result;
    }

    private static List<ArticleBlock> copyBlocks(List<ArticleBlock> blocks) {
        List<ArticleBlock> result = new ArrayList<>();
        for (ArticleBlock block : blocks) {
            ArticleBlock copy = new ArticleBlock();
            copy.setType(block.getType());
            copy.setTitle(block.getTitle());
            copy.setMarker(block.getMarker());
            copy.setMarkerText(block.getMarkerText());
            copy.setContent(block.getContent());
            copy.setStyleHint(block.getStyleHint());
            copy.setRenderMeta(block.getRenderMeta() != null ? new java.util.HashMap<>(block.getRenderMeta()) : new java.util.HashMap<>());
            result.add(copy);
        }
        return result;
    }
}
