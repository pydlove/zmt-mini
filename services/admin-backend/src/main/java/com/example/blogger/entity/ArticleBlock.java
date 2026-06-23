package com.example.blogger.entity;

import java.util.HashMap;
import java.util.Map;

public class ArticleBlock {

    public static final String TYPE_SECTION = "section";
    public static final String TYPE_PARAGRAPH = "paragraph";
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_QUOTE = "quote";

    private String type;
    private String title;
    private String marker;
    private String markerText;
    private String content;
    private String styleHint;
    private Map<String, Object> renderMeta = new HashMap<>();

    public ArticleBlock() {
    }

    public static ArticleBlock section(String title, String marker, String markerText, String content, String styleHint) {
        ArticleBlock block = new ArticleBlock();
        block.type = TYPE_SECTION;
        block.title = title;
        block.marker = marker;
        block.markerText = markerText;
        block.content = content;
        block.styleHint = styleHint;
        return block;
    }

    public static ArticleBlock paragraph(String content, String styleHint) {
        ArticleBlock block = new ArticleBlock();
        block.type = TYPE_PARAGRAPH;
        block.content = content;
        block.styleHint = styleHint;
        return block;
    }

    public static ArticleBlock image(String content) {
        ArticleBlock block = new ArticleBlock();
        block.type = TYPE_IMAGE;
        block.content = content;
        return block;
    }

    // getters and setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMarker() { return marker; }
    public void setMarker(String marker) { this.marker = marker; }
    public String getMarkerText() { return markerText; }
    public void setMarkerText(String markerText) { this.markerText = markerText; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStyleHint() { return styleHint; }
    public void setStyleHint(String styleHint) { this.styleHint = styleHint; }
    public Map<String, Object> getRenderMeta() { return renderMeta; }
    public void setRenderMeta(Map<String, Object> renderMeta) { this.renderMeta = renderMeta; }
}
