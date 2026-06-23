package com.example.blogger.util;

/**
 * DOCX 导出样式配置，对应 {@code tu_export_template.config} JSON 结构。
 */
public class DocxStyleConfig {

    private String fontFamily = "微软雅黑";
    private String headingFontFamily = "微软雅黑";
    private int bodyFontSizePt = 12;
    private int headingFontSizePt = 16;
    private String bodyColor = "#262626";
    private String headingColor = "#07c160";
    private int lineSpacing = 360;
    private int paragraphSpacingAfter = 200;
    private int marginTop = 1440;
    private int marginBottom = 1440;
    private int marginLeft = 1800;
    private int marginRight = 1800;
    private String quoteBg = "#e6f7ff";
    private String previewColor = "#07c160";
    private String description = "";

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
    public String getHeadingFontFamily() { return headingFontFamily; }
    public void setHeadingFontFamily(String headingFontFamily) { this.headingFontFamily = headingFontFamily; }
    public int getBodyFontSizePt() { return bodyFontSizePt; }
    public void setBodyFontSizePt(int bodyFontSizePt) { this.bodyFontSizePt = bodyFontSizePt; }
    public int getHeadingFontSizePt() { return headingFontSizePt; }
    public void setHeadingFontSizePt(int headingFontSizePt) { this.headingFontSizePt = headingFontSizePt; }
    public String getBodyColor() { return bodyColor; }
    public void setBodyColor(String bodyColor) { this.bodyColor = bodyColor; }
    public String getHeadingColor() { return headingColor; }
    public void setHeadingColor(String headingColor) { this.headingColor = headingColor; }
    public int getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(int lineSpacing) { this.lineSpacing = lineSpacing; }
    public int getParagraphSpacingAfter() { return paragraphSpacingAfter; }
    public void setParagraphSpacingAfter(int paragraphSpacingAfter) { this.paragraphSpacingAfter = paragraphSpacingAfter; }
    public int getMarginTop() { return marginTop; }
    public void setMarginTop(int marginTop) { this.marginTop = marginTop; }
    public int getMarginBottom() { return marginBottom; }
    public void setMarginBottom(int marginBottom) { this.marginBottom = marginBottom; }
    public int getMarginLeft() { return marginLeft; }
    public void setMarginLeft(int marginLeft) { this.marginLeft = marginLeft; }
    public int getMarginRight() { return marginRight; }
    public void setMarginRight(int marginRight) { this.marginRight = marginRight; }
    public String getQuoteBg() { return quoteBg; }
    public void setQuoteBg(String quoteBg) { this.quoteBg = quoteBg; }
    public String getPreviewColor() { return previewColor; }
    public void setPreviewColor(String previewColor) { this.previewColor = previewColor; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取不带 # 的 headingColor，供 POI 使用。
     */
    public String getHeadingColorForPoi() {
        return headingColor != null ? headingColor.replace("#", "") : "07c160";
    }

    /**
     * 获取不带 # 的 bodyColor，供 POI 使用。
     */
    public String getBodyColorForPoi() {
        return bodyColor != null ? bodyColor.replace("#", "") : "262626";
    }
}
