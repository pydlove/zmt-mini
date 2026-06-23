package com.example.blogger.util;

import com.example.blogger.entity.ArticleBlock;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DocxGenerator {

    private static final Logger log = LoggerFactory.getLogger(DocxGenerator.class);
    private static final String HIGHLIGHT_COLOR = "fa541c";
    private static final int DEFAULT_H3_FONT_SIZE = 16; // 16pt
    private static final int DEFAULT_NORMAL_FONT_SIZE = 12; // 12pt

    /**
     * 设置 Run 字号（pt）。POI 5.x 的 setFontSize 入参即为 pt，内部会转成 half-points。
     */
    private void setRunFontSize(XWPFRun run, int pt) {
        run.setFontSize(Math.max(2, pt));
    }

    private static final Pattern H1_PATTERN = Pattern.compile("<h1[^>]*>(.*?)</h1>");
    private static final Pattern H3_PATTERN = Pattern.compile("<h3[^>]*>(.*?)</h3>");
    private static final Pattern S_PATTERN = Pattern.compile("<s>(.*?)</s>");
    private static final Pattern IMG_PATTERN = Pattern.compile("<img\\s+src=\"([^\"]+)\"[^>]*>");

    /**
     * 将文章内容写入 DOCX 文件（使用默认主题色和字号）
     * @param title 文章标题
     * @param content 文章正文（支持 <h3> 章节标题 和 <s> 着重加强 标签）
     * @param filePath 输出文件路径
     */
    public void generateDocx(String title, String content, String filePath) throws Exception {
        generateDocx(title, content, filePath, HIGHLIGHT_COLOR, null, null);
    }

    /**
     * 将文章内容写入 DOCX 文件（使用自定义主题色）
     * @param title 文章标题
     * @param content 文章正文（支持 <h3> 章节标题 和 <s> 着重加强 标签）
     * @param filePath 输出文件路径
     * @param themeColor 主题色（十六进制，如 fa541c）
     */
    public void generateDocx(String title, String content, String filePath, String themeColor) throws Exception {
        generateDocx(title, content, filePath, themeColor, null, null);
    }

    /**
     * 将文章内容写入 DOCX 文件（完整版：支持主题色和字号配置）
     * @param title 文章标题
     * @param content 文章正文（支持 <h3> 章节标题 和 <s> 着重加强 标签）
     * @param filePath 输出文件路径
     * @param themeColor 主题色（十六进制，如 fa541c）
     * @param titleFontSize 标题字号（pt），null 时使用默认值 16
     * @param contentFontSize 正文字号（pt），null 时使用默认值 12
     */
    public void generateDocx(String title, String content, String filePath, String themeColor, Integer titleFontSize, Integer contentFontSize) throws Exception {
        // 兜底：移除大模型思考过程标签及其内容
        content = stripThinkingTags(content);
        // 清洗常见 HTML 标签（保留 h1, h3, s, img 供后续专项处理）
        content = cleanHtmlTags(content);

        DocxStyleConfig config = new DocxStyleConfig();
        if (themeColor != null && !themeColor.isEmpty()) {
            config.setHeadingColor(themeColor);
            config.setPreviewColor(themeColor);
        }
        if (titleFontSize != null && titleFontSize > 0) {
            config.setHeadingFontSizePt(titleFontSize);
        }
        if (contentFontSize != null && contentFontSize > 0) {
            config.setBodyFontSizePt(contentFontSize);
        }

        String color = config.getHeadingColorForPoi();
        int h3Size = config.getHeadingFontSizePt();
        int normalSize = config.getBodyFontSizePt();

        // title 参数保留用于兼容调用方，但不再写入文档正文
        try (FileOutputStream out = new FileOutputStream(filePath);
             XWPFDocument document = new XWPFDocument()) {

            applyPageMargins(document, config);

            // 预处理：标准化换行符，把单个 \n 变成 \n\n，确保块级标签独占段落
            // 把连续三个及以上 \n 压缩为 \n\n，避免过多空段落
            String normalizedContent = content.replaceAll("(?<!\n)\n(?!\n)", "\n\n")
                                               .replaceAll("\n{3,}", "\n\n");
            // 正文段落：按 \n\n 分割段落（不再单独写入标题，标题已体现在文件名中）
            String[] paragraphs = normalizedContent.split("\n\n+");
            for (int i = 0; i < paragraphs.length; i++) {
                String para = paragraphs[i];
                if (para.trim().isEmpty()) {
                    document.createParagraph();
                    continue;
                }
                String trimmed = para.trim();

                // 检查是否是 <img> 图片段落
                Matcher imgMatcher = IMG_PATTERN.matcher(trimmed);
                if (imgMatcher.matches()) {
                    String src = imgMatcher.group(1);
                    addImageParagraph(document, src);
                    continue;
                }

                // 检查是否是 <h3> 标题段落（仅保留 AI 显式标记的 h3）
                Matcher h3Matcher = H3_PATTERN.matcher(trimmed);
                if (h3Matcher.matches()) {
                    String h3Text = h3Matcher.group(1);
                    addH3Paragraph(document, h3Text, config);
                } else {
                    // 普通段落：可能包含 <s> 标签
                    addNormalParagraph(document, trimmed, config);
                }
            }

            document.write(out);
        }
    }

    /**
     * 从 {@link ArticleBlock} 列表直接生成 DOCX，支持基于 styleStrategy 的视觉差异渲染。
     * <p>
     * 视觉策略对照：
     * <ul>
     *   <li>A 纯视觉美化：默认，最简样式</li>
     *   <li>B 自动连续编号：章节编号 "01 |" 用小字号主题色，标题加大加粗</li>
     *   <li>C 模板映射：按 block.renderMeta.template 渲染 story-card / tip-card / emphasis-card</li>
     *   <li>D 下游差异化：极简风格（无主题色，无加粗）</li>
     *   <li>E 内容分级控制：按 renderMeta.contentGrade 区分 intro-short / core-long / action 字号</li>
     *   <li>F 自动生成目录：检测 "本文目录：" 开头的 block 渲染为多行缩进格式</li>
     *   <li>G AI 配图提示：DCOX 不做处理（关键词用于下游图片生成）</li>
     * </ul>
     * 此外任何策略下，block.styleHint = 'emphasis' / 'story' / 'tip' 都会触发对应卡片样式
     * （由 LLM 在 prompt 中按段落指定）。
     *
     * @param title            文章标题（仅用于日志/调试，不写入文档）
     * @param blocks           文章块列表
     * @param filePath         输出文件路径
     * @param themeColor       主题色（十六进制，如 fa541c）
     * @param titleFontSize    标题字号（pt），null 时使用默认值
     * @param contentFontSize  正文字号（pt），null 时使用默认值
     * @param styleStrategy    样式策略（A-G），null 时按 A 处理
     */
    public void generateDocxFromBlocks(String title, List<ArticleBlock> blocks, String filePath,
                                        String themeColor, Integer titleFontSize, Integer contentFontSize,
                                        String styleStrategy) throws Exception {
        DocxStyleConfig config = new DocxStyleConfig();
        if (themeColor != null && !themeColor.isEmpty()) {
            config.setHeadingColor(themeColor);
            config.setPreviewColor(themeColor);
        }
        if (titleFontSize != null && titleFontSize > 0) {
            config.setHeadingFontSizePt(titleFontSize);
        }
        if (contentFontSize != null && contentFontSize > 0) {
            config.setBodyFontSizePt(contentFontSize);
        }
        generateDocxFromBlocks(title, blocks, filePath, config, styleStrategy);
    }

    /**
     * 从 {@link ArticleBlock} 列表直接生成 DOCX，使用 {@link DocxStyleConfig} 控制整体样式。
     *
     * @param title         文章标题（仅用于日志/调试，不写入文档）
     * @param blocks        文章块列表
     * @param filePath      输出文件路径
     * @param styleConfig   导出模板样式配置
     * @param styleStrategy 样式策略（A-G），null 时按 A 处理
     */
    public void generateDocxFromBlocks(String title, List<ArticleBlock> blocks, String filePath,
                                        DocxStyleConfig styleConfig, String styleStrategy) throws Exception {
        DocxStyleConfig config = styleConfig != null ? styleConfig : new DocxStyleConfig();
        String strategy = styleStrategy != null ? styleStrategy.toUpperCase() : "A";
        String color = config.getHeadingColorForPoi();
        int h3Size = config.getHeadingFontSizePt();
        int normalSize = config.getBodyFontSizePt();

        log.info("[DocxGenerator] 从 blocks 生成 DOCX: title={}, strategy={}, blocks={}",
                title, strategy, blocks != null ? blocks.size() : 0);

        try (FileOutputStream out = new FileOutputStream(filePath);
             XWPFDocument document = new XWPFDocument()) {

            applyPageMargins(document, config);

            if (blocks == null || blocks.isEmpty()) {
                document.write(out);
                return;
            }

            for (ArticleBlock block : blocks) {
                if (block == null) continue;
                String type = block.getType();
                String content = block.getContent() != null ? stripThinkingTags(block.getContent()) : null;

                if (ArticleBlock.TYPE_SECTION.equals(type)) {
                    renderSectionBlock(document, block, content, config, strategy);
                } else if (ArticleBlock.TYPE_IMAGE.equals(type)) {
                    renderImageBlock(document, content);
                } else {
                    renderParagraphBlock(document, block, content, config, strategy);
                }
            }

            document.write(out);
            log.info("[DocxGenerator] DOCX 生成完成: {}", filePath);
        }
    }

    private void applyPageMargins(XWPFDocument document, DocxStyleConfig config) {
        try {
            CTBody body = document.getDocument().getBody();
            CTSectPr sectPr = body.getSectPr();
            if (sectPr == null) {
                sectPr = body.addNewSectPr();
            }
            CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
            pageMar.setTop(BigInteger.valueOf(config.getMarginTop()));
            pageMar.setBottom(BigInteger.valueOf(config.getMarginBottom()));
            pageMar.setLeft(BigInteger.valueOf(config.getMarginLeft()));
            pageMar.setRight(BigInteger.valueOf(config.getMarginRight()));
        } catch (Exception e) {
            log.warn("[DocxGenerator] 设置页边距失败: {}", e.getMessage());
        }
    }

    private void applyParagraphSpacing(XWPFParagraph paragraph, DocxStyleConfig config) {
        try {
            paragraph.setSpacingAfter(config.getParagraphSpacingAfter());
            paragraph.setSpacingLineRule(LineSpacingRule.AUTO);
            paragraph.setSpacingBetween(config.getLineSpacing() / 240.0);
        } catch (Exception e) {
            log.warn("[DocxGenerator] 设置段落间距失败: {}", e.getMessage());
        }
    }

    // ===================== 策略感知渲染方法 =====================

    private void renderSectionBlock(XWPFDocument doc, ArticleBlock block, String content, DocxStyleConfig config,
                                     String strategy) {
        String titleText = block.getTitle() != null ? block.getTitle() : content;
        if (titleText == null) return;
        titleText = stripThinkingTags(titleText);
        if (titleText.isEmpty()) return;

        String color = config.getHeadingColorForPoi();
        int fontSize = config.getHeadingFontSizePt();
        int normalSize = config.getBodyFontSizePt();

        // 策略 B：把 "01 |" 编号前缀单独用小字号主题色渲染，标题部分加大加粗
        if ("B".equals(strategy)) {
            int pipeIdx = titleText.indexOf("|");
            if (pipeIdx > 0 && pipeIdx < titleText.length() - 1) {
                String prefix = titleText.substring(0, pipeIdx + 1).trim();
                String rest = titleText.substring(pipeIdx + 1).trim();
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.LEFT);
                applyParagraphSpacing(p, config);

                XWPFRun prefixRun = p.createRun();
                prefixRun.setText(prefix);
                setRunFontSize(prefixRun, Math.max(10, fontSize - 4));
                prefixRun.setColor(color);
                prefixRun.setBold(true);
                setRunFont(prefixRun, config.getHeadingFontFamily());

                XWPFRun restRun = p.createRun();
                restRun.setText(" " + rest);
                setRunFontSize(restRun, fontSize + 2);
                restRun.setBold(true);
                setRunFont(restRun, config.getHeadingFontFamily());
                addSectionContentParagraph(doc, content, config);
                return;
            }
        }

        // 默认：A/C/D/E/F/G 策略下都使用统一的小节标题样式
        addH3Paragraph(doc, titleText, config);
        addSectionContentParagraph(doc, content, config);
    }

    private void addSectionContentParagraph(XWPFDocument doc, String content, DocxStyleConfig config) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        addNormalParagraph(doc, content, config);
    }

    private void renderImageBlock(XWPFDocument doc, String content) {
        if (content == null) return;
        Matcher imgMatcher = IMG_PATTERN.matcher(content);
        if (imgMatcher.matches()) {
            String src = imgMatcher.group(1);
            addImageParagraph(doc, src);
        }
    }

    private void renderParagraphBlock(XWPFDocument doc, ArticleBlock block, String content, DocxStyleConfig config, String strategy) {
        if (content == null || content.isEmpty()) {
            return;
        }

        String color = config.getHeadingColorForPoi();
        int fontSize = config.getBodyFontSizePt();

        // 1. 策略 F：检测 TOC 块（内容以 "本文目录：" 开头）
        if (content.startsWith("本文目录：") || content.startsWith("本文目录:")) {
            renderTocBlock(doc, content, config);
            return;
        }

        // 2. 策略 C：优先用 renderMeta.template
        if ("C".equals(strategy)) {
            Object templateObj = block.getRenderMeta() != null ? block.getRenderMeta().get("template") : null;
            if (templateObj instanceof String) {
                renderByTemplate(doc, content, config, (String) templateObj);
                return;
            }
        }

        // 3. 策略 E：按 renderMeta.contentGrade 分级
        if ("E".equals(strategy)) {
            Object gradeObj = block.getRenderMeta() != null ? block.getRenderMeta().get("contentGrade") : null;
            if (gradeObj instanceof String) {
                renderByGrade(doc, content, config, (String) gradeObj);
                return;
            }
        }

        // 4. 策略 D：极简风格（无主题色无加粗）
        if ("D".equals(strategy)) {
            renderMinimalParagraph(doc, content, config);
            return;
        }

        // 5. block.styleHint 驱动样式（任何 strategy 下都生效，LLM 在 prompt 中按段落指定）
        String styleHint = block.getStyleHint();
        if (styleHint != null && !styleHint.isEmpty() && !"normal".equalsIgnoreCase(styleHint)) {
            renderByStyleHint(doc, content, config, styleHint);
            return;
        }

        // 6. 默认渲染（处理 <s> 标签）
        addNormalParagraph(doc, content, config);
    }

    private void renderByTemplate(XWPFDocument doc, String content, DocxStyleConfig config, String template) {
        switch (template) {
            case "story-card":
                renderIndentedItalic(doc, content, config);
                break;
            case "tip-card":
                renderTipCard(doc, content, config);
                break;
            case "emphasis-card":
                renderEmphasisCard(doc, content, config);
                break;
            default:
                addNormalParagraph(doc, content, config);
        }
    }

    private void renderByGrade(XWPFDocument doc, String content, DocxStyleConfig config, String grade) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        applyParagraphSpacing(p, config);

        int baseSize = config.getBodyFontSizePt();
        String color = config.getHeadingColorForPoi();
        int size = baseSize;
        boolean bold = false;
        String colorToUse = null;
        String prefix = null;
        switch (grade) {
            case "intro-short":
                size = Math.max(10, baseSize - 1);
                break;
            case "core-long":
                size = baseSize;
                break;
            case "action":
                size = baseSize + 2;
                bold = true;
                colorToUse = color;
                prefix = "👉 ";
                break;
            default:
                break;
        }
        XWPFRun run = p.createRun();
        run.setText((prefix != null ? prefix : "") + content);
        setRunFontSize(run, size);
        if (bold) run.setBold(true);
        if (colorToUse != null) run.setColor(colorToUse);
        setRunFont(run, config.getFontFamily());
    }

    private void renderByStyleHint(XWPFDocument doc, String content, DocxStyleConfig config, String styleHint) {
        switch (styleHint.toLowerCase()) {
            case "emphasis":
                renderEmphasisCard(doc, content, config);
                break;
            case "story":
                renderIndentedItalic(doc, content, config);
                break;
            case "tip":
                renderTipCard(doc, content, config);
                break;
            default:
                addNormalParagraph(doc, content, config);
        }
    }

    private void renderEmphasisCard(XWPFDocument doc, String content, DocxStyleConfig config) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        applyParagraphSpacing(p, config);

        XWPFRun run = p.createRun();
        run.setText(content);
        run.setBold(true);
        setRunFontSize(run, config.getBodyFontSizePt() + 1);
        run.setColor(config.getHeadingColorForPoi());
        setRunFont(run, config.getHeadingFontFamily());
    }

    private void renderIndentedItalic(XWPFDocument doc, String content, DocxStyleConfig config) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(400);
        p.setAlignment(ParagraphAlignment.LEFT);
        applyParagraphSpacing(p, config);

        XWPFRun run = p.createRun();
        run.setText(content);
        run.setItalic(true);
        setRunFontSize(run, config.getBodyFontSizePt());
        run.setColor(config.getHeadingColorForPoi());
        setRunFont(run, config.getFontFamily());
    }

    private void renderTipCard(XWPFDocument doc, String content, DocxStyleConfig config) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(200);
        p.setAlignment(ParagraphAlignment.LEFT);
        applyParagraphSpacing(p, config);

        XWPFRun run = p.createRun();
        run.setText("💡 " + content);
        setRunFontSize(run, Math.max(10, config.getBodyFontSizePt() - 1));
        run.setColor("888888");
        setRunFont(run, config.getFontFamily());
    }

    private void renderMinimalParagraph(XWPFDocument doc, String content, DocxStyleConfig config) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        applyParagraphSpacing(p, config);

        XWPFRun run = p.createRun();
        run.setText(content);
        setRunFontSize(run, config.getBodyFontSizePt());
        setRunFont(run, config.getFontFamily());
    }

    private void renderTocBlock(XWPFDocument doc, String content, DocxStyleConfig config) {
        // 拆成多行，每行渲染为独立段落（小字号、缩进）
        String[] lines = content.split("\n");
        int fontSize = config.getBodyFontSizePt();
        String color = config.getHeadingColorForPoi();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.LEFT);
            p.setIndentationLeft(200);
            applyParagraphSpacing(p, config);

            XWPFRun run = p.createRun();
            if (line.startsWith("本文目录")) {
                run.setText(line);
                setRunFontSize(run, fontSize + 1);
                run.setBold(true);
                run.setColor(color);
                setRunFont(run, config.getHeadingFontFamily());
            } else {
                run.setText(line);
                setRunFontSize(run, Math.max(10, fontSize - 1));
                setRunFont(run, config.getFontFamily());
            }
        }
    }

    /**
     * 兜底过滤：移除大模型思考过程标签及其内容。
     * 如果整篇文章被单个 <think> 包裹（过滤后为空），则只去掉标签本身，保留内容。
     */
    private String stripThinkingTags(String text) {
        if (text == null || text.isEmpty()) return text;
        String cleaned = text.replaceAll("(?is)<thinking\\b[^>]*>.*?</thinking>", "")
                             .replaceAll("(?is)<think\\b[^>]*>.*?</think>", "")
                             .replaceAll("(?is)<thought\\b[^>]*>.*?</thought>", "")
                             .replaceAll("(?is)<reasoning\\b[^>]*>.*?</reasoning>", "")
                             .trim();
        // 兜底1：如果过滤后内容为空，回退为只移除标签保留内容
        if (cleaned.isEmpty()) {
            cleaned = text.replaceAll("(?is)</?thinking\\b[^>]*>", "")
                          .replaceAll("(?is)</?think\\b[^>]*>", "")
                          .replaceAll("(?is)</?thought\\b[^>]*>", "")
                          .replaceAll("(?is)</?reasoning\\b[^>]*>", "")
                          .trim();
        }
        // 兜底2：处理未闭合的 <think> 标签（有开头无结尾，删除从 <think> 开始到文本结束）
        String lower = cleaned.toLowerCase();
        int thinkIdx = lower.indexOf("<think");
        if (thinkIdx >= 0) {
            int closeIdx = lower.indexOf("</think>", thinkIdx);
            if (closeIdx < 0) {
                log.warn("[DocxGenerator] 发现未闭合的 <think> 标签，截断处理");
                cleaned = cleaned.substring(0, thinkIdx).trim();
            }
        }
        int thinkingIdx = lower.indexOf("<thinking");
        if (thinkingIdx >= 0) {
            int closeIdx = lower.indexOf("</thinking>", thinkingIdx);
            if (closeIdx < 0) {
                log.warn("[DocxGenerator] 发现未闭合的 <thinking> 标签，截断处理");
                cleaned = cleaned.substring(0, thinkingIdx).trim();
            }
        }
        return cleaned;
    }

    /**
     * 清洗常见 HTML 标签，保留 h1/h3/s/img 供后续专项处理。
     * 将 <br> 转为换行，解析 HTML 实体，去除 div/span/strong/em/p 等标签。
     */
    private String cleanHtmlTags(String text) {
        if (text == null || text.isEmpty()) return text;
        // 1. <br> 标签转为换行
        text = text.replaceAll("(?i)<br\\s*/?>", "\n");
        // 2. 处理常见 HTML 实体
        text = text.replaceAll("&nbsp;", " ")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&amp;", "&")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&#39;", "'")
                   .replaceAll("&#34;", "\"")
                   .replaceAll("&#x27;", "'");
        // 3. 去除不需要的 HTML 标签（保留 h1, h3, s, img 供后续处理）
        text = text.replaceAll("(?i)<(?!/?h1\\b|/?h3\\b|/?s\\b|/?img\\b)[^>]*?>", "");
        // 4. 清理多余空行
        text = text.replaceAll("\n{3,}", "\n\n");
        return text.trim();
    }

    /**
     * 判断文本去除 HTML 标签后是否以标点符号结尾
     */
    private boolean endsWithPunctuation(String text) {
        String plain = text.replaceAll("<[^>]+>", "").trim();
        if (plain.isEmpty()) return false;
        char last = plain.charAt(plain.length() - 1);
        return "。，！？；：、.?!:;".indexOf(last) >= 0;
    }

    private void addH3Paragraph(XWPFDocument document, String text, DocxStyleConfig config) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.LEFT);
        applyParagraphSpacing(p, config);

        XWPFRun run = p.createRun();
        run.setText(text);
        setRunFontSize(run, config.getHeadingFontSizePt());
        run.setColor(config.getHeadingColorForPoi());
        run.setBold(true);
        setRunFont(run, config.getHeadingFontFamily());
    }

    private void addNormalParagraph(XWPFDocument document, String text, DocxStyleConfig config) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.BOTH);
        applyParagraphSpacing(p, config);

        String color = config.getHeadingColorForPoi();
        int fontSize = config.getBodyFontSizePt();

        // 解析 <s> 标签，将文本分段
        int lastEnd = 0;
        Matcher sMatcher = S_PATTERN.matcher(text);

        while (sMatcher.find()) {
            // 标签前的普通文本
            if (sMatcher.start() > lastEnd) {
                String normalText = text.substring(lastEnd, sMatcher.start());
                if (!normalText.isEmpty()) {
                    XWPFRun normalRun = p.createRun();
                    normalRun.setText(normalText);
                    setRunFontSize(normalRun, fontSize);
                    setRunFont(normalRun, config.getFontFamily());
                }
            }
            // <s> 标签内的文本
            String sText = sMatcher.group(1);
            if (!sText.isEmpty()) {
                XWPFRun sRun = p.createRun();
                sRun.setText(sText);
                setRunFontSize(sRun, fontSize);
                sRun.setBold(true);
                sRun.setColor(color);
                setRunFont(sRun, config.getFontFamily());
            }
            lastEnd = sMatcher.end();
        }

        // 剩余的普通文本
        if (lastEnd < text.length()) {
            String remainingText = text.substring(lastEnd);
            if (!remainingText.isEmpty()) {
                XWPFRun remainingRun = p.createRun();
                remainingRun.setText(remainingText);
                setRunFontSize(remainingRun, fontSize);
                setRunFont(remainingRun, config.getFontFamily());
            }
        }
    }

    private void addImageParagraph(XWPFDocument document, String imageUrl) {
        XWPFParagraph p = document.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        try {
            String imagePath = System.getProperty("user.dir") + imageUrl;
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                log.warn("[DocxGenerator] 图片文件不存在: {}", imagePath);
                return;
            }
            BufferedImage bufferedImage = ImageIO.read(imageFile);
            if (bufferedImage == null) {
                log.warn("[DocxGenerator] 无法读取图片: {}", imagePath);
                return;
            }
            int maxWidthPt = 400;
            int imgWidth = bufferedImage.getWidth();
            int imgHeight = bufferedImage.getHeight();
            double ratio = (double) imgHeight / imgWidth;
            int widthEMU = Units.toEMU(maxWidthPt);
            int heightEMU = Units.toEMU((int) (maxWidthPt * ratio));
            XWPFRun imageRun = p.createRun();
            try (FileInputStream fis = new FileInputStream(imageFile)) {
                int format = getImageFormat(imageFile.getName());
                imageRun.addPicture(fis, format, imageFile.getName(), widthEMU, heightEMU);
            }
        } catch (Exception e) {
            log.warn("[DocxGenerator] 插入图片失败: {}", e.getMessage());
        }
    }

    private int getImageFormat(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return XWPFDocument.PICTURE_TYPE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return XWPFDocument.PICTURE_TYPE_JPEG;
        if (lower.endsWith(".gif")) return XWPFDocument.PICTURE_TYPE_GIF;
        if (lower.endsWith(".bmp")) return XWPFDocument.PICTURE_TYPE_BMP;
        return XWPFDocument.PICTURE_TYPE_JPEG;
    }

    private void setRunFont(XWPFRun run, String fontFamily) {
        String ff = fontFamily != null && !fontFamily.isEmpty() ? fontFamily : "微软雅黑";
        CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();
        fonts.setAscii(ff);
        fonts.setHAnsi(ff);
        fonts.setEastAsia(ff);
        run.setFontFamily(ff);
    }
}
