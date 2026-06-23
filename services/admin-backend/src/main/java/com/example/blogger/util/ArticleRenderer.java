package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArticleRenderer {

    public String render(List<ArticleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            ArticleBlock block = blocks.get(i);
            switch (block.getType()) {
                case ArticleBlock.TYPE_SECTION -> renderSection(sb, block);
                case ArticleBlock.TYPE_PARAGRAPH, ArticleBlock.TYPE_QUOTE -> renderParagraph(sb, block);
                case ArticleBlock.TYPE_IMAGE -> renderImage(sb, block);
                default -> renderParagraph(sb, block);
            }
            if (i < blocks.size() - 1) {
                sb.append("\n\n");
            }
        }
        return sb.toString().trim();
    }

    private void renderSection(StringBuilder sb, ArticleBlock block) {
        String title = block.getTitle() != null && !block.getTitle().isEmpty()
                ? block.getTitle()
                : (block.getMarkerText() != null ? block.getMarkerText() : "");
        if (title.isEmpty()) {
            return;
        }
        sb.append("<h3>").append(escapeHtml(title)).append("</h3>\n\n");
        if (block.getContent() != null && !block.getContent().isEmpty()) {
            sb.append(block.getContent());
        }
    }

    private void renderParagraph(StringBuilder sb, ArticleBlock block) {
        if (block.getContent() != null) {
            sb.append(block.getContent());
        }
    }

    private void renderImage(StringBuilder sb, ArticleBlock block) {
        if (block.getContent() != null) {
            sb.append(block.getContent());
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}
