package com.example.blogger.util;

import com.example.blogger.entity.AiFlavorRule;
import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.mapper.AiFlavorRuleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class AiFlavorRemover {

    private static final Logger log = LoggerFactory.getLogger(AiFlavorRemover.class);

    private final AiFlavorRuleMapper aiFlavorRuleMapper;

    @Value("${app.script.replace-periods-path:}")
    private String replacePeriodsScriptPath;

    public AiFlavorRemover(AiFlavorRuleMapper aiFlavorRuleMapper) {
        this.aiFlavorRuleMapper = aiFlavorRuleMapper;
    }

    public String removeAiFlavor(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        log.info("[AiFlavorRemover] 原始内容长度: {}", text.length());

        // Step 1: 先调用 Python 脚本处理
        String afterScript = runPythonScript(text);
        log.info("[AiFlavorRemover] Python脚本处理后长度: {}", afterScript.length());

        // Step 2: 执行数据库规则替换
        String afterRules = applyRules(afterScript);
        log.info("[AiFlavorRemover] 最终处理后长度: {}", afterRules.length());

        // Step 3: 过滤大模型思考过程标签
        String cleaned = stripThinkingTags(afterRules);
        if (cleaned.length() != afterRules.length()) {
            log.info("[AiFlavorRemover] 过滤思考标签后长度: {}", cleaned.length());
        }
        return cleaned;
    }

    /**
     * 对 ArticleBlock 列表逐个去 AI 味。
     * title 和 content 都会处理；如果处理失败则保留原始内容并记录日志。
     */
    public List<ArticleBlock> removeAiFlavor(List<ArticleBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        List<ArticleBlock> result = new ArrayList<>();
        for (ArticleBlock block : blocks) {
            ArticleBlock copy = copyBlock(block);
            try {
                if (copy.getTitle() != null && !copy.getTitle().isEmpty()) {
                    copy.setTitle(removeAiFlavor(copy.getTitle()));
                }
                if (copy.getContent() != null && !copy.getContent().isEmpty()) {
                    copy.setContent(removeAiFlavor(copy.getContent()));
                }
            } catch (Exception e) {
                log.warn("[AiFlavorRemover] 处理 block 失败，保留原始内容: {}", e.getMessage());
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

    private String runPythonScript(String content) {
        if (replacePeriodsScriptPath == null || replacePeriodsScriptPath.isEmpty()) {
            log.info("[AiFlavorRemover] 未配置 Python 脚本路径，跳过脚本执行");
            return content;
        }

        Exception lastException = null;
        for (String pythonCmd : new String[]{"python3", "python"}) {
            try {
                // 写入临时文件传给脚本
                java.nio.file.Path tempInput = java.nio.file.Files.createTempFile("ai_flavor_input_", ".txt");
                java.nio.file.Files.writeString(tempInput, content, StandardCharsets.UTF_8);
                java.nio.file.Path tempOutput = java.nio.file.Files.createTempFile("ai_flavor_output_", ".txt");

                java.util.List<String> command = java.util.Arrays.asList(
                    pythonCmd, replacePeriodsScriptPath, tempInput.toString(), "-o", tempOutput.toString()
                );
                log.info("[AiFlavorRemover] 执行去AI味脚本: {} {}", pythonCmd, replacePeriodsScriptPath);

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                StringBuilder stdout = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                }

                boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("[AiFlavorRemover] 去AI味脚本执行超时");
                    throw new RuntimeException("去AI味脚本执行超时");
                }

                int exitCode = process.exitValue();
                log.info("[AiFlavorRemover] 脚本执行完成 exitCode={}, 输出: {}", exitCode, stdout.toString().trim());

                if (exitCode != 0) {
                    String errOutput = stdout.toString().trim();
                    log.error("[AiFlavorRemover] 脚本执行失败: {}", errOutput);
                    throw new RuntimeException("去AI味脚本执行失败: " + errOutput);
                }

                // 读取脚本输出
                String result = java.nio.file.Files.readString(tempOutput, StandardCharsets.UTF_8);

                // 清理临时文件
                java.nio.file.Files.deleteIfExists(tempInput);
                java.nio.file.Files.deleteIfExists(tempOutput);

                return result;

            } catch (Exception e) {
                lastException = e;
                log.warn("[AiFlavorRemover] 使用 {} 执行去AI味脚本失败: {}", pythonCmd, e.getMessage());
            }
        }
        log.error("[AiFlavorRemover] 无法执行去AI味脚本，已尝试 python3 和 python");
        return content;
    }

    /**
     * 移除大模型思考过程标签及其内容。
     * 支持：大小写不敏感、带属性的标签、多行内容。
     * 兜底：如果整篇文章被单个 <think> 包裹（过滤后为空），则只去掉标签本身，保留内容。
     */
    public String removeThinkingTags(String text) {
        return stripThinkingTags(text);
    }

    private String stripThinkingTags(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // (?is) 开启 DOTALL + CASE_INSENSITIVE，使 . 能匹配换行符且忽略大小写
        // \b 确保是完整的标签名，[^>]* 匹配任意属性
        String cleaned = text.replaceAll("(?is)<thinking\\b[^>]*>.*?</thinking>", "")
                             .replaceAll("(?is)<think\\b[^>]*>.*?</think>", "")
                             .replaceAll("(?is)<thought\\b[^>]*>.*?</thought>", "")
                             .replaceAll("(?is)<reasoning\\b[^>]*>.*?</reasoning>", "")
                             .trim();

        // 兜底1：如果过滤后内容为空（说明整篇文章被 <think> 包裹），则只去掉标签本身
        if (cleaned.isEmpty()) {
            log.warn("[AiFlavorRemover] 过滤 <think> 标签后内容为空，回退为只移除标签保留内容");
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
                log.warn("[AiFlavorRemover] 发现未闭合的 <think> 标签，截断处理");
                cleaned = cleaned.substring(0, thinkIdx).trim();
            }
        }
        int thinkingIdx = lower.indexOf("<thinking");
        if (thinkingIdx >= 0) {
            int closeIdx = lower.indexOf("</thinking>", thinkingIdx);
            if (closeIdx < 0) {
                log.warn("[AiFlavorRemover] 发现未闭合的 <thinking> 标签，截断处理");
                cleaned = cleaned.substring(0, thinkingIdx).trim();
            }
        }

        return cleaned;
    }

    /**
     * 从数据库读取启用的规则，对文本进行替换
     */
    private String applyRules(String text) {
        List<AiFlavorRule> rules;
        try {
            rules = aiFlavorRuleMapper.findAllEnabled();
        } catch (Exception e) {
            log.error("[AiFlavorRemover] 读取数据库规则失败，跳过规则替换: {}", e.getMessage());
            return text;
        }
        if (rules == null || rules.isEmpty()) {
            log.info("[AiFlavorRemover] 数据库中没有启用的AI去除规则，跳过规则替换");
            return text;
        }
        log.info("[AiFlavorRemover] 读取到 {} 条AI去除规则，开始替换", rules.size());
        for (AiFlavorRule rule : rules) {
            String from = rule.getRuleFrom();
            String to = rule.getRuleTo();
            if (from != null && !from.isEmpty() && to != null) {
                int count = text.split(from, -1).length - 1;
                text = text.replace(from, to);
                if (count > 0) {
                    log.info("[AiFlavorRemover] 替换 「{}」→「{}」 命中 {} 次", from, to, count);
                }
            }
        }
        return text;
    }
}
