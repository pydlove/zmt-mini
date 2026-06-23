package com.example.blogger.service;

import com.example.blogger.entity.WritingStyle;
import com.example.blogger.mapper.WritingStyleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.blogger.entity.ArticleBlock;

@Service
public class WritingStyleService {

    private static final Logger log = LoggerFactory.getLogger(WritingStyleService.class);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(<[^>]+>)");

    private final WritingStyleMapper writingStyleMapper;

    public WritingStyleService(WritingStyleMapper writingStyleMapper) {
        this.writingStyleMapper = writingStyleMapper;
    }

    public List<WritingStyle> list() {
        return writingStyleMapper.findAll();
    }

    public List<WritingStyle> listByCategory(String category) {
        return writingStyleMapper.findByCategory(category);
    }

    public WritingStyle getById(String id) {
        return writingStyleMapper.findById(id);
    }

    public List<String> categories() {
        return writingStyleMapper.findAllCategories();
    }

    public void save(WritingStyle writingStyle) {
        if (writingStyle.getId() == null || writingStyle.getId().isEmpty()) {
            writingStyle.setId(UUID.randomUUID().toString().replace("-", ""));
            if (writingStyle.getIsActive() == null) {
                writingStyle.setIsActive(1);
            }
            if (writingStyle.getCategory() == null || writingStyle.getCategory().isEmpty()) {
                writingStyle.setCategory("通用");
            }
            writingStyleMapper.insert(writingStyle);
        } else {
            writingStyleMapper.update(writingStyle);
        }
    }

    public void delete(String id) {
        writingStyleMapper.delete(id);
    }

    /**
     * 将文本中的原词替换为风格词。
     * 只替换 HTML 标签外的文本内容，避免破坏标签结构。
     * 按原词长度降序排序，优先匹配长词，避免短词干扰。
     * 使用词边界检查（前后非中文），避免部分匹配。
     */
    public String applyStyle(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        List<WritingStyle> styles = writingStyleMapper.findAll();
        if (styles == null || styles.isEmpty()) {
            return content;
        }

        // 过滤出启用的规则，并按原词长度降序排序
        List<WritingStyle> activeStyles = new ArrayList<>();
        for (WritingStyle ws : styles) {
            if (ws.getIsActive() != null && ws.getIsActive() == 1
                    && ws.getOriginalWord() != null && !ws.getOriginalWord().isEmpty()
                    && ws.getStyleWord() != null && !ws.getStyleWord().isEmpty()) {
                activeStyles.add(ws);
            }
        }
        activeStyles.sort((a, b) -> Integer.compare(b.getOriginalWord().length(), a.getOriginalWord().length()));
        if (activeStyles.isEmpty()) {
            return content;
        }

        // 将内容按 HTML 标签分割，只对非标签部分做替换
        Matcher tagMatcher = HTML_TAG_PATTERN.matcher(content);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        int replaceCount = 0;

        while (tagMatcher.find()) {
            // 处理标签前的文本段
            String textSegment = content.substring(lastEnd, tagMatcher.start());
            if (!textSegment.isEmpty()) {
                String replaced = applyReplacements(textSegment, activeStyles);
                if (!replaced.equals(textSegment)) {
                    replaceCount++;
                }
                result.append(replaced);
            }
            // 保留原 HTML 标签不变
            result.append(tagMatcher.group());
            lastEnd = tagMatcher.end();
        }
        // 处理最后一段文本（尾部无标签的情况）
        if (lastEnd < content.length()) {
            String textSegment = content.substring(lastEnd);
            String replaced = applyReplacements(textSegment, activeStyles);
            if (!replaced.equals(textSegment)) {
                replaceCount++;
            }
            result.append(replaced);
        }

        log.info("[WritingStyleService] 风格词替换完成, 共替换 {} 处", replaceCount);
        return result.toString();
    }

    private String applyReplacements(String text, List<WritingStyle> styles) {
        String result = text;
        for (WritingStyle ws : styles) {
            String original = ws.getOriginalWord();
            String style = ws.getStyleWord();
            // 使用词边界正则：匹配 original，且前后不是中文汉字
            // 中文词边界使用 (?!<[\u4e00-\u9fa5]) 和 (?![\u4e00-\u9fa5])
            String escaped = Pattern.quote(original);
            Pattern pattern = Pattern.compile("(?<![\u4e00-\u9fa5])" + escaped + "(?![\u4e00-\u9fa5])");
            result = pattern.matcher(result).replaceAll(style);
        }
        return result;
    }

    /**
     * 对 ArticleBlock 列表逐个应用写作风格替换。
     * title 和 content 都会处理；处理失败时保留原始内容。
     */
    public List<ArticleBlock> applyStyle(List<ArticleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        List<ArticleBlock> result = new ArrayList<>();
        for (ArticleBlock block : blocks) {
            ArticleBlock copy = copyBlock(block);
            try {
                if (copy.getTitle() != null && !copy.getTitle().isEmpty()) {
                    copy.setTitle(applyStyle(copy.getTitle()));
                }
                if (copy.getContent() != null && !copy.getContent().isEmpty()) {
                    copy.setContent(applyStyle(copy.getContent()));
                }
            } catch (Exception e) {
                log.warn("[WritingStyleService] 处理 block 失败，保留原始内容: {}", e.getMessage());
            }
            result.add(copy);
        }
        return result;
    }

    private ArticleBlock copyBlock(ArticleBlock block) {
        ArticleBlock copy = new ArticleBlock();
        copy.setType(block.getType());
        copy.setTitle(block.getTitle());
        copy.setMarker(block.getMarker());
        copy.setMarkerText(block.getMarkerText());
        copy.setContent(block.getContent());
        copy.setStyleHint(block.getStyleHint());
        copy.setRenderMeta(block.getRenderMeta() != null ? new java.util.HashMap<>(block.getRenderMeta()) : null);
        return copy;
    }
}
