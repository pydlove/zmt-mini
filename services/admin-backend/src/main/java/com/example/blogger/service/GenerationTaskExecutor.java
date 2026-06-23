package com.example.blogger.service;

import com.example.blogger.entity.ArticleBlock;
import com.example.blogger.entity.TitleGenerationTask;
import com.example.blogger.entity.TitleLibrary;
import com.example.blogger.entity.Track;
import com.example.blogger.entity.User;
import com.example.blogger.mapper.TitleGenerationTaskMapper;
import com.example.blogger.mapper.TrackMapper;
import com.example.blogger.util.AiFlavorRemover;
import com.example.blogger.util.ArticleJsonParser;
import com.example.blogger.util.ArticleRenderer;
import com.example.blogger.util.ArticleStyleProcessor;
import com.example.blogger.util.DocxGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 文章生成任务执行器。
 * 负责执行单个 {@link TitleGenerationTask} 的完整生成流水线：
 * 提示词准备、大模型生成、去除 AI 味、写作风格替换、图片插入、DOCX 生成、
 * 违禁词检测、数据库更新、贴图生成。
 *
 * 本类只关注“任务如何执行”，不关注“何时/以何种并发调度”。
 * 调度逻辑保留在 {@link com.example.blogger.scheduler.GenerationTaskScheduler} 中。
 */
@Service
public class GenerationTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(GenerationTaskExecutor.class);

    private final TitleGenerationTaskMapper taskMapper;
    private final TitleGenerationTaskService taskService;
    private final LLMService llmService;
    private final DocxGenerator docxGenerator;
    private final TitleLibraryService titleLibraryService;
    private final UserService userService;
    private final TaskInterruptManager interruptManager;
    private final AiFlavorRemover aiFlavorRemover;
    private final com.example.blogger.mapper.ImageLibraryMapper imageLibraryMapper;
    private final TrackMapper trackMapper;
    private final ContentCheckService contentCheckService;
    private final WritingStyleService writingStyleService;
    private final ArticleJsonParser articleJsonParser;
    private final ArticleStyleProcessor articleStyleProcessor;
    private final ArticleRenderer articleRenderer;

    public GenerationTaskExecutor(TitleGenerationTaskMapper taskMapper,
                                  TitleGenerationTaskService taskService,
                                  LLMService llmService,
                                  DocxGenerator docxGenerator,
                                  TitleLibraryService titleLibraryService,
                                  UserService userService,
                                  TaskInterruptManager interruptManager,
                                  AiFlavorRemover aiFlavorRemover,
                                  com.example.blogger.mapper.ImageLibraryMapper imageLibraryMapper,
                                  TrackMapper trackMapper,
                                  ContentCheckService contentCheckService,
                                  WritingStyleService writingStyleService,
                                  ArticleJsonParser articleJsonParser,
                                  ArticleStyleProcessor articleStyleProcessor,
                                  ArticleRenderer articleRenderer) {
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.llmService = llmService;
        this.docxGenerator = docxGenerator;
        this.titleLibraryService = titleLibraryService;
        this.userService = userService;
        this.interruptManager = interruptManager;
        this.aiFlavorRemover = aiFlavorRemover;
        this.imageLibraryMapper = imageLibraryMapper;
        this.trackMapper = trackMapper;
        this.contentCheckService = contentCheckService;
        this.writingStyleService = writingStyleService;
        this.articleJsonParser = articleJsonParser;
        this.articleStyleProcessor = articleStyleProcessor;
        this.articleRenderer = articleRenderer;
    }

    /**
     * 执行单个文章生成任务。
     * <p>
     * 整体流水线：
     * 1. 将关联标题库状态标记为“生成中”。
     * 2. 依次执行 prompt 准备、正文生成、去 AI 味、风格替换、图片插入、样式处理、渲染、DOCX 生成、
     *    违禁词检测、文件关联更新、贴图生成。
     * 3. 任何阶段被停止则更新进度为“已停止”并回退标题库状态；
     *    任何阶段抛异常则更新任务为 failed 并回退标题库状态。
     *
     * @param task 待执行的 pending/processing 任务
     */
    public void executeTask(TitleGenerationTask task) {
        log.info("[GenerationTaskExecutor] ========== 任务开始: id={}, titleLibraryId={}, title={} ==========",
                task.getId(), task.getTitleLibraryId(), task.getTitle());
        long taskStartTime = System.currentTimeMillis();
        try {
            // 0. 将标题库生成状态置为“生成中”，前端可据此展示生成进度
            titleLibraryService.updateGenerateStatus(task.getTitleLibraryId(), 2);

            // 1. 准备提示词：追加系统指令，约束模型输出格式
            String prompt = preparePrompt(task);

            // 2. 调用大模型生成正文，并解析为 ArticleBlock 列表
            List<ArticleBlock> blocks = generateContent(task, prompt);

            // 2.5 章节标题生成（当前产品策略跳过）
            // blocks = generateChapterTitles(task, blocks);

            // 3. 去除 AI 味
            blocks = removeAiFlavor(task, blocks);

            // 3.6 根据写作风格库替换词汇
            blocks = applyWritingStyle(task, blocks);

            // 3.5 插入配图
            // 图片插入仍基于文本，先渲染为文本，插入图片后再解析回 blocks
            String contentBeforeImage = articleRenderer.render(blocks);
            ImageInsertResult imageResult = insertImage(task, contentBeforeImage);
            if (imageResult.imageInserted) {
                blocks = insertImageBlock(blocks, imageResult.content);
            }

            // 6. 文章样式处理
            blocks = articleStyleProcessor.process(blocks);

            // 7. 渲染为最终文本
            String content = articleRenderer.render(blocks);
            taskService.updateGeneratedContent(task.getId(), content);

            // 4. 生成 DOCX
            DocxResult docx = generateDocx(task, content);

            // 4.6 违禁词/敏感词检测，结果写入标题库
            checkBannedWords(task, content);

            // 4.5 更新标题库的文件关联信息
            updateTitleLibraryFile(task, docx.fileUrl, docx.fileName);

            // 5 & 6. 任务状态置为 completed，并再次同步标题库文件关联
            completeTask(task, docx.fileUrl, docx.fileName);

            // 7. 基于 DOCX 自动生成手机端分享贴图
            generateImagePosts(task);

            long totalTime = System.currentTimeMillis() - taskStartTime;
            log.info("[GenerationTaskExecutor] ========== 任务完成: id={}, fileUrl={}, 总耗时{}ms ==========",
                    task.getId(), docx.fileUrl, totalTime);

        } catch (InterruptedException ie) {
            // 任务被手动停止：保留当前进度步骤，状态回退为未生成
            log.warn("[GenerationTaskExecutor] [任务{}] 任务被停止", task.getId());
            taskService.updateProgress(task.getId(), task.getProgressStep(), "已停止");
            titleLibraryService.updateGenerateStatus(task.getTitleLibraryId(), 0);
        } catch (Exception e) {
            // 任意阶段抛异常：任务标记 failed，标题库状态回退，异常信息持久化
            log.error("[GenerationTaskExecutor] [任务{}] 任务处理失败: error={}", task.getId(), e.getMessage(), e);
            taskService.updateFailed(task.getId(), e.getMessage());
            titleLibraryService.updateGenerateStatus(task.getTitleLibraryId(), 0);
        }
    }

    // ===================== 阶段步骤方法：每个方法对应原 executeTask 中的一个阶段 =====================

    /**
     * 阶段 1：准备提示词。
     * <p>
     * 从任务中取出原始 prompt，并追加系统指令（禁止输出思考过程、禁止重复标题、要求 <h3> 章节小标题等）。
     * 此阶段会检查任务是否已被停止，并更新任务进度为“构建提示词完成”。
     *
     * @param task 当前任务
     * @return 追加系统指令后的最终 prompt
     * @throws InterruptedException 当任务状态为 stopped 时抛出，进入停止处理流程
     */
    private String preparePrompt(TitleGenerationTask task) throws InterruptedException {
        log.info("[GenerationTaskExecutor] [任务{}] Step 1/6: 构建提示词", task.getId());
        checkStopped(task.getId());

        String prompt = task.getPrompt();
        // 追加系统指令：禁止模型输出思考过程，避免内容被 think 标签包裹导致误删
        if (prompt != null && !prompt.contains("\u3010\u7cfb\u7edf\u6307\u4ee4\u3011")) {
            String provider = llmService.getSelectedModelType();
            if ("minimax".equals(provider)) {
                prompt += "\n\n【系统指令】"
                        + "请直接输出 JSON 数组，不要任何前言、总结、markdown 代码块或思考过程。"
                        + "文章最开头不要重复写总标题。"
                        + "正文中间请根据内容需要插入1-3个章节小标题，标题写成 '01 | 标题内容' 形式。"
                        + "\n\nJSON 数组字段：type('section'/'paragraph'), title, marker, markerText, content, styleHint('normal'/'emphasis'/'story'/'tip')。"
                        + "content 中可用 <s></s> 标记重点词句。"
                        + "\n\n示例：[{\"type\":\"section\",\"title\":\"01 | 示例标题\",\"marker\":\"01\",\"markerText\":\"示例标题\",\"content\":\"示例正文\",\"styleHint\":\"normal\"}]";
            } else {
                prompt += "\n\n【系统指令】"
                        + "请直接输出 JSON 数组，不要输出任何思考过程，不要复述用户要求，不要加任何前言或总结。"
                        + "输出必须是可以被标准 JSON 解析器解析的合法 JSON，不要包裹在 markdown 代码块中。"
                        + "禁止使用 <think>、<thinking>、<thought>、<reasoning> 等标签包裹内容。"
                        + "不要用英文分析任务，直接开始写中文文章。"
                        + "正文中间请根据内容需要插入1-3个章节小标题（用于区分段落和观点），使用 <h3>标题内容</h3> 标签包裹，最多不超过3个。"
                        + "文章最开头不要重复写总标题。"
                        + "\n\nJSON 数组中每个元素代表文章的一个块，字段如下："
                        + "type: 'section' 或 'paragraph';"
                        + "title: 完整小标题字符串，如有序号请写成 '01 | 标题内容' 形式;"
                        + "marker: 从 title 解析出的序号，如 '01'，没有则填 null;"
                        + "markerText: 去掉 marker 后的标题文本，没有则填 null;"
                        + "content: 该章节的正文内容，允许使用 <s></s> 标记需要着重加强的词句;"
                        + "styleHint: 可选 'normal' / 'emphasis' / 'story' / 'tip';"
                        + "\n\n示例：[{\"type\":\"section\",\"title\":\"01 | 示例标题\",\"marker\":\"01\",\"markerText\":\"示例标题\",\"content\":\"示例正文\",\"styleHint\":\"normal\"}]";
            }
        }

        taskService.updateProgress(task.getId(), 1, "构建提示词完成，准备生成...");
        log.info("[GenerationTaskExecutor] [任务{}] Step 1/6: 提示词构建完成, prompt长度={}",
                task.getId(), prompt != null ? prompt.length() : 0);
        return prompt;
    }

    /**
     * 阶段 2：调用大模型生成正文。
     * <p>
     * 使用 {@link LLMService#generateContent(String)} 调用大模型；调用前后通过
     * {@link TaskInterruptManager} 注册/注销当前线程，以支持“停止任务”时中断 LLM 阻塞调用。
     * 返回内容会先经 {@link AiFlavorRemover#removeThinkingTags(String)} 过滤思考标签，
     * 随后使用 {@link ArticleJsonParser#parse(String)} 解析为 {@link ArticleBlock} 列表，
     * 并将渲染后的文本持久化到任务的 generated_content 字段。
     *
     * @param task          当前任务
     * @param articlePrompt 阶段 1 准备好的 prompt
     * @return 大模型生成的正文解析后的 ArticleBlock 列表
     * @throws InterruptedException 任务被停止或线程被中断时抛出
     */
    private List<ArticleBlock> generateContent(TitleGenerationTask task, String articlePrompt) throws InterruptedException {
        log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: 调用大模型生成正文", task.getId());
        checkStopped(task.getId());
        log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: 调用LLM, prompt长度={}", task.getId(), articlePrompt.length());

        long llmStart = System.currentTimeMillis();
        taskService.updateProgress(task.getId(), 2, "大模型生成中...");
        interruptManager.register(task.getId(), Thread.currentThread());
        String rawContent;
        boolean interrupted = false;
        try {
            rawContent = llmService.generateContent(articlePrompt);
        } finally {
            interruptManager.unregister(task.getId());
            interrupted = Thread.interrupted(); // 清除并获取中断状态
        }
        if (interrupted) {
            throw new InterruptedException("任务被停止");
        }
        log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: LLM返回完成, 耗时{}ms, 内容长度={}",
                task.getId(), System.currentTimeMillis() - llmStart, rawContent.length());

        // 先过滤 think 标签，避免二次请求大模型时 prompt 携带思考过程
        rawContent = aiFlavorRemover.removeThinkingTags(rawContent);
        log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: 过滤think标签后长度={}", task.getId(), rawContent.length());

        List<ArticleBlock> blocks = articleJsonParser.parse(rawContent);
        log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: JSON解析完成, 共{}个block", task.getId(), blocks.size());

        taskService.updateProgress(task.getId(), 2, "正文生成完成");
        log.info("[GenerationTaskExecutor] [任务{}] Step 2/6: 正文生成完成", task.getId());
        return blocks;
    }

    /**
     * 阶段 2.5：章节标题生成（当前策略为跳过）。
     * <p>
     * 原设计意图是根据正文生成 1~3 个章节小标题并插入到合适位置；目前产品策略暂不需要，
     * 因此仅更新进度并透传原文。后续如需启用，可在此方法内调用大模型生成标题并合并。
     *
     * @param task    当前任务
     * @param content 阶段 2 生成的正文
     * @return 保持不变的内容
     */
    private String generateChapterTitles(TitleGenerationTask task, String content) {
        log.info("[GenerationTaskExecutor] [任务{}] Step 2.5/6: 跳过章节标题生成", task.getId());
        taskService.updateProgress(task.getId(), 2, "跳过章节标题生成");
        taskService.updateGeneratedContent(task.getId(), content);
        return content;
    }

    /**
     * 阶段 3：去除 AI 味。
     * <p>
     * 调用 {@link AiFlavorRemover#removeAiFlavor(List)} 对 ArticleBlock 列表进行清洗，
     * 去除机械化表达、过度总结、AI 常用句式等，使文章更像人工撰写。
     * 此阶段会检查任务是否已被停止，并更新任务进度。
     *
     * @param task    当前任务
     * @param blocks  阶段 2 生成的 ArticleBlock 列表
     * @return 去除 AI 味后的 ArticleBlock 列表
     * @throws InterruptedException 当任务状态为 stopped 时抛出
     */
    private List<ArticleBlock> removeAiFlavor(TitleGenerationTask task, List<ArticleBlock> blocks) throws InterruptedException {
        log.info("[GenerationTaskExecutor] [任务{}] Step 3/6: 开始去除AI味", task.getId());
        checkStopped(task.getId());
        taskService.updateProgress(task.getId(), 3, "去除AI味中...");

        long aiFlavorStart = System.currentTimeMillis();
        List<ArticleBlock> cleanedBlocks = aiFlavorRemover.removeAiFlavor(blocks);
        log.info("[GenerationTaskExecutor] [任务{}] Step 3/6: 去除AI味完成, 耗时{}ms, block数={}",
                task.getId(), System.currentTimeMillis() - aiFlavorStart, cleanedBlocks.size());
        taskService.updateProgress(task.getId(), 3, "去除AI味完成");
        return cleanedBlocks;
    }

    /**
     * 阶段 3.6：写作风格词替换。
     * <p>
     * 根据系统配置/用户写作风格库，对 ArticleBlock 列表进行风格化词汇替换。
     * 该阶段失败不影响任务完成，会记录 warn 日志并继续使用原文。
     *
     * @param task    当前任务
     * @param blocks  阶段 3 清洗后的 ArticleBlock 列表
     * @return 风格替换后的 ArticleBlock 列表
     */
    private List<ArticleBlock> applyWritingStyle(TitleGenerationTask task, List<ArticleBlock> blocks) {
        log.info("[GenerationTaskExecutor] [任务{}] Step 3.6/6: 开始写作风格词替换", task.getId());
        try {
            long styleStart = System.currentTimeMillis();
            List<ArticleBlock> styledBlocks = writingStyleService.applyStyle(blocks);
            log.info("[GenerationTaskExecutor] [任务{}] Step 3.6/6: 风格词替换完成, 耗时{}ms, block数={}",
                    task.getId(), System.currentTimeMillis() - styleStart, styledBlocks.size());
            return styledBlocks;
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] [任务{}] Step 3.6/6: 风格词替换失败, 跳过: {}", task.getId(), e.getMessage());
            return blocks;
        }
    }

    /**
     * 阶段 3.5：插入配图。
     * <p>
     * 根据标题库所属赛道，按以下优先级获取图片：
     * <ol>
     *   <li>使用文章标题前 10 个字符作为关键词，调用 Python 脚本从百度下载图片；</li>
     *   <li>若下载失败，则从图片库中按赛道随机获取一张图片。</li>
     * </ol>
     * 获取到图片后，将其以 <img> 标签形式随机插入正文前 50% 的某个普通段落之后。
     * 图片相关异常会被捕获并记录，不影响后续 DOCX 生成。
     *
     * @param task    当前任务
     * @param content 阶段 3.6 风格化后的正文（文本形式）
     * @return 包含插入后正文及是否插入了图片的 {@link ImageInsertResult}
     */
    private ImageInsertResult insertImage(TitleGenerationTask task, String content) {
        log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 开始插入图片", task.getId());
        try {
            TitleLibrary titleLib = titleLibraryService.getById(task.getTitleLibraryId());
            if (titleLib == null || titleLib.getTrackId() == null || titleLib.getTrackId().isEmpty()) {
                log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 标题库无赛道信息, 跳过图片插入", task.getId());
                return new ImageInsertResult(content, false);
            }

            com.example.blogger.entity.ImageLibrary image = null;

            // 1. 优先根据关键字从百度下载图片（Step 2.5 已跳过，使用标题作为 fallback 关键词）
            String searchKeyword = task.getTitle();
            if (searchKeyword != null && searchKeyword.length() > 10) {
                searchKeyword = searchKeyword.substring(0, 10);
            }
            log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: keyword 为空，使用标题作为 fallback keyword={}",
                    task.getId(), searchKeyword);

            if (searchKeyword != null && !searchKeyword.isEmpty()) {
                Track track = trackMapper.findById(titleLib.getTrackId());
                if (track != null) {
                    log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 尝试关键字下载图片, keyword={}, track={}",
                            task.getId(), searchKeyword, track.getName());
                    image = downloadImageByKeyword(searchKeyword, titleLib.getTrackId(), track.getName());
                    if (image != null) {
                        log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 关键字下载图片成功, url={}",
                                task.getId(), image.getUrl());
                    } else {
                        log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 关键字下载图片失败, 将回退到图片库", task.getId());
                    }
                }
            }

            // 2. 如果下载失败，从图片库随机获取
            if (image == null) {
                log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 从图片库随机获取", task.getId());
                image = imageLibraryMapper.findRandomByTrackId(titleLib.getTrackId());
                if (image != null) {
                    log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 图片库获取成功, url={}", task.getId(), image.getUrl());
                } else {
                    log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 图片库也未找到匹配图片", task.getId());
                }
            }

            if (image != null) {
                String inserted = insertImageIntoContent(content, image);
                log.info("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 图片已插入到正文", task.getId());
                return new ImageInsertResult(inserted, true);
            }
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] [任务{}] Step 3.5/6: 插入图片失败, 跳过: {}", task.getId(), e.getMessage());
        }
        return new ImageInsertResult(content, false);
    }

    /**
     * 将图片插入后的文本重新解析为 ArticleBlock 列表。
     * <p>
     * 如果 insertImage 没有插入图片，直接返回原 blocks；
     * 否则将文本重新解析为 blocks。
     *
     * @param blocks           插入图片前的 ArticleBlock 列表
     * @param contentWithImage 插入图片后的文本
     * @return 更新后的 ArticleBlock 列表
     */
    private List<ArticleBlock> insertImageBlock(List<ArticleBlock> blocks, String contentWithImage) {
        if (blocks == null || blocks.isEmpty()) {
            return blocks;
        }
        // 有图片插入，简单策略：把文本重新解析为 blocks
        return parseBlocksFromRenderedText(contentWithImage, blocks);
    }

    private static List<ArticleBlock> parseBlocksFromRenderedText(String text, List<ArticleBlock> originalBlocks) {
        List<ArticleBlock> result = new ArrayList<>();
        String[] paragraphs = text.split("\\n\\n+");
        for (int i = 0; i < paragraphs.length; i++) {
            String trimmed = paragraphs[i].trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("<h3>") && trimmed.endsWith("</h3>")) {
                String title = trimmed.substring(4, trimmed.length() - 5);
                StringBuilder contentBuilder = new StringBuilder();
                int j = i + 1;
                while (j < paragraphs.length) {
                    String next = paragraphs[j].trim();
                    if (next.isEmpty() || next.startsWith("<h3>") || next.startsWith("<img")) {
                        break;
                    }
                    if (contentBuilder.length() > 0) {
                        contentBuilder.append("\n\n");
                    }
                    contentBuilder.append(next);
                    j++;
                }
                ArticleBlock originalSection = findOriginalByTitle(originalBlocks, title);
                String marker = originalSection != null ? originalSection.getMarker() : null;
                String markerText = originalSection != null ? originalSection.getMarkerText() : null;
                String styleHint = originalSection != null ? originalSection.getStyleHint() : "normal";
                Map<String, Object> renderMeta = originalSection != null && originalSection.getRenderMeta() != null
                        ? new HashMap<>(originalSection.getRenderMeta()) : new HashMap<>();
                result.add(ArticleBlock.section(title, marker, markerText, contentBuilder.toString(), styleHint));
                result.get(result.size() - 1).setRenderMeta(renderMeta);
                i = j - 1;
            } else if (trimmed.startsWith("<img")) {
                result.add(ArticleBlock.image(trimmed));
            } else {
                String styleHint = "normal";
                Map<String, Object> renderMeta = new HashMap<>();
                if (originalBlocks != null) {
                    for (ArticleBlock ob : originalBlocks) {
                        if (ArticleBlock.TYPE_PARAGRAPH.equals(ob.getType()) && trimmed.equals(ob.getContent())) {
                            styleHint = ob.getStyleHint();
                            renderMeta = ob.getRenderMeta() != null ? new HashMap<>(ob.getRenderMeta()) : new HashMap<>();
                            break;
                        }
                    }
                }
                ArticleBlock p = ArticleBlock.paragraph(trimmed, styleHint);
                p.setRenderMeta(renderMeta);
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 在原始 block 列表中根据标题查找对应的 section block。
     */
    private static ArticleBlock findOriginalByTitle(List<ArticleBlock> original, String title) {
        if (original == null) return null;
        for (ArticleBlock block : original) {
            if (ArticleBlock.TYPE_SECTION.equals(block.getType()) && block.getTitle() != null && block.getTitle().equals(title)) {
                return block;
            }
        }
        return null;
    }

    /**
     * 阶段 4：生成 DOCX 文件。
     * <p>
     * 根据任务标题生成安全文件名，写入 {@code uploads/articles/} 目录；
     * 同时读取推荐用户的主题色、标题字号、正文字号配置传给 {@link DocxGenerator}。
     * 此阶段会检查任务是否已被停止，并更新任务进度为“文件写入完成”。
     *
     * @param task    当前任务
     * @param content 阶段 3.5 插入图片后的正文
     * @return 包含生成文件 URL 和文件名的 {@link DocxResult}
     * @throws Exception DOCX 生成工具抛出的异常，或任务被停止时抛出
     */
    private DocxResult generateDocx(TitleGenerationTask task, String content) throws Exception {
        log.info("[GenerationTaskExecutor] [任务{}] Step 4/6: 开始生成DOCX文件", task.getId());
        checkStopped(task.getId());

        String safeTitle = task.getTitle() != null ? task.getTitle() : "untitled";
        safeTitle = safeTitle.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]", "").trim();
        safeTitle = safeTitle.replaceAll("\\s+", "，");
        if (safeTitle.isEmpty()) {
            safeTitle = "article_" + task.getTitleLibraryId();
        }
        String fileName = safeTitle + "_" + task.getId() + ".docx";
        String articlesDir = System.getProperty("user.dir") + File.separator + "uploads" + File.separator + "articles";
        File dir = new File(articlesDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String filePath = articlesDir + File.separator + fileName;

        taskService.updateProgress(task.getId(), 4, "写入文件中...");

        // 获取用户主题色和字号配置
        String themeColor = null;
        Integer titleFontSize = null;
        Integer contentFontSize = null;
        try {
            TitleLibrary titleLib = titleLibraryService.getById(task.getTitleLibraryId());
            if (titleLib != null && titleLib.getRecommendUserId() != null) {
                User user = userService.getById(titleLib.getRecommendUserId());
                if (user != null) {
                    if (user.getThemeColor() != null && !user.getThemeColor().isEmpty()) {
                        themeColor = user.getThemeColor();
                    }
                    titleFontSize = user.getTitleFontSize();
                    contentFontSize = user.getContentFontSize();
                    log.info("[GenerationTaskExecutor] [任务{}] Step 4/6: 用户样式配置: themeColor={}, titleFontSize={}, contentFontSize={}",
                            task.getId(), themeColor, titleFontSize, contentFontSize);
                }
            }
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] [任务{}] Step 4/6: 获取用户样式配置失败, 使用默认: {}", task.getId(), e.getMessage());
        }

        long docxStart = System.currentTimeMillis();
        docxGenerator.generateDocx(task.getTitle(), content, filePath, themeColor, titleFontSize, contentFontSize);
        log.info("[GenerationTaskExecutor] [任务{}] Step 4/6: DOCX生成完成, 耗时{}ms, path={}",
                task.getId(), System.currentTimeMillis() - docxStart, filePath);
        taskService.updateProgress(task.getId(), 4, "文件写入完成");

        String fileUrl = "/uploads/articles/" + fileName;
        return new DocxResult(fileUrl, fileName);
    }

    /**
     * 阶段 4.6：违禁词/敏感词检测。
     * <p>
     * 调用 {@link ContentCheckService#checkContent(String)} 对正文进行检测，
     * 并将检测结果（JSON）写入关联标题库的 banned_word_check_result 字段。
     * 检测失败不影响任务完成，仅记录 warn 日志。
     *
     * @param task    当前任务
     * @param content 阶段 4 生成 DOCX 时使用的正文
     */
    private void checkBannedWords(TitleGenerationTask task, String content) {
        log.info("[GenerationTaskExecutor] [任务{}] Step 4.6/6: 开始违禁词检测", task.getId());
        try {
            long checkStart = System.currentTimeMillis();
            ContentCheckService.CheckResult checkResult = contentCheckService.checkContent(content);
            ObjectMapper mapper = new ObjectMapper();
            String checkResultJson = mapper.writeValueAsString(checkResult);
            titleLibraryService.updateBannedWordCheckResult(task.getTitleLibraryId(), checkResultJson);
            log.info("[GenerationTaskExecutor] [任务{}] Step 4.6/6: 违禁词检测完成, 耗时{}ms, totalChars={}, matches={}",
                    task.getId(), System.currentTimeMillis() - checkStart, checkResult.getTotalChars(), checkResult.getMatches().size());
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] [任务{}] Step 4.6/6: 违禁词检测失败, 不影响任务完成: {}", task.getId(), e.getMessage());
        }
    }

    /**
     * 阶段 4.5：更新 TitleLibrary 文件关联。
     * <p>
     * 将生成的 DOCX 文件 URL 和文件名写入标题库表的 generated_file_url / generated_file_name 字段，
     * 便于前端下载和邮件推送时定位文件。
     * 更新失败不影响任务完成，仅记录 warn 日志。
     *
     * @param task     当前任务
     * @param fileUrl  生成文件的访问 URL，如 {@code /uploads/articles/xxx.docx}
     * @param fileName 生成文件的文件名
     */
    private void updateTitleLibraryFile(TitleGenerationTask task, String fileUrl, String fileName) {
        log.info("[GenerationTaskExecutor] [任务{}] Step 4.5/6: 更新TitleLibrary文件关联", task.getId());
        try {
            TitleLibrary titleLib = titleLibraryService.getById(task.getTitleLibraryId());
            if (titleLib != null) {
                titleLibraryService.updateGeneratedFile(titleLib.getId(), fileUrl, fileName);
                log.info("[GenerationTaskExecutor] [任务{}] Step 4.5/6: TitleLibrary文件关联更新成功", task.getId());
            }
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] [任务{}] Step 4.5/6: 更新TitleLibrary文件关联失败, 不影响任务完成: {}",
                    task.getId(), e.getMessage());
        }
    }

    /**
     * 阶段 5 & 6：完成任务并再次更新 TitleLibrary 关联。
     * <p>
     * 阶段 5：将任务状态更新为 completed，记录完成时间，进度置为“已完成”。
     * 阶段 6：再次更新标题库文件关联（兼容历史逻辑，确保字段一致性）。
     *
     * @param task     当前任务
     * @param fileUrl  生成文件的访问 URL
     * @param fileName 生成文件的文件名
     */
    private void completeTask(TitleGenerationTask task, String fileUrl, String fileName) {
        log.info("[GenerationTaskExecutor] [任务{}] Step 5/6: 更新任务状态为completed", task.getId());
        taskMapper.updateCompleted(task.getId(), "completed", fileUrl, fileName, LocalDateTime.now(), LocalDateTime.now());
        taskService.updateProgress(task.getId(), 5, "已完成");

        log.info("[GenerationTaskExecutor] [任务{}] Step 6/6: 更新TitleLibrary关联文件", task.getId());
        titleLibraryService.updateGeneratedFile(task.getTitleLibraryId(), fileUrl, fileName);
    }

    /**
     * 阶段 7：自动生成文章贴图。
     * <p>
     * 调用 Python 脚本将已生成的 DOCX 文章切分为适合手机端分享的竖图（贴图），
     * 图片 URL 列表会保存到标题库的 image_post_urls 字段。
     * 贴图生成失败不影响任务完成，仅记录 warn 日志。
     *
     * @param task 当前任务
     */
    private void generateImagePosts(TitleGenerationTask task) {
        log.info("[GenerationTaskExecutor] [任务{}] Step 7/6: 自动生成文章贴图", task.getId());
        try {
            List<String> images = titleLibraryService.generateImagePosts(task.getTitleLibraryId(), null, null, null);
            log.info("[GenerationTaskExecutor] [任务{}] 贴图生成成功, 共{}张", task.getId(), images.size());
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] [任务{}] 贴图生成失败, 不影响任务完成: {}", task.getId(), e.getMessage());
        }
    }

    // ===================== 辅助方法 =====================

    private void checkStopped(String taskId) throws InterruptedException {
        TitleGenerationTask current = taskMapper.findById(taskId);
        if (current != null && "stopped".equals(current.getStatus())) {
            throw new InterruptedException("任务已停止");
        }
    }

    /**
     * 将图片标记随机插入到文章前50%的某个普通段落之后（独占一个段落，前后换行）。
     */
    private String insertImageIntoContent(String content, com.example.blogger.entity.ImageLibrary image) {
        if (content == null || content.isEmpty() || image == null || image.getUrl() == null) {
            return content;
        }
        String[] paragraphs = content.split("\n\n+");
        if (paragraphs.length == 0) return content;

        int half = Math.max(1, (paragraphs.length + 1) / 2);
        // 收集前50%中的普通段落索引（排除标题和空段落）
        List<Integer> normalIndices = new ArrayList<>();
        for (int i = 0; i < half; i++) {
            String p = paragraphs[i].trim();
            if (!p.isEmpty() && !p.startsWith("<h3>") && !p.startsWith("<h1>")) {
                normalIndices.add(i);
            }
        }
        if (normalIndices.isEmpty()) {
            // 退而求其次，找任意非空段落
            for (int i = 0; i < half; i++) {
                if (!paragraphs[i].trim().isEmpty()) {
                    normalIndices.add(i);
                    break;
                }
            }
        }
        if (normalIndices.isEmpty()) return content;

        int targetIndex = normalIndices.get((int) (Math.random() * normalIndices.size()));
        String imgTag = "<img src=\"" + image.getUrl() + "\">";

        // 在目标段落之后插入一个独立的图片段落
        List<String> newParagraphs = new ArrayList<>(Arrays.asList(paragraphs));
        newParagraphs.add(targetIndex + 1, imgTag);
        return String.join("\n\n", newParagraphs);
    }

    /**
     * 根据关键字调用 Python 脚本从百度下载图片，成功则保存到图片库并返回。
     * 只接受 baidu- 开头的图片（排除 picsum 回退）。
     */
    private com.example.blogger.entity.ImageLibrary downloadImageByKeyword(String keyword, String trackId, String trackName) {
        String scriptPath = resolveScriptPath();
        if (scriptPath == null) {
            log.warn("[GenerationTaskExecutor] 找不到下载脚本");
            return null;
        }

        String tempDirName = UUID.randomUUID().toString().replace("-", "");
        Path tempOutputDir = Paths.get(System.getProperty("user.dir"), "uploads", "temp_downloads", tempDirName);
        try {
            Files.createDirectories(tempOutputDir);
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] 创建临时下载目录失败: {}", e.getMessage());
            return null;
        }

        List<String> command = new ArrayList<>();
        command.add("python3");
        command.add(scriptPath);
        command.add(tempOutputDir.toString());
        command.add("--count");
        command.add("1");
        command.add("--source");
        command.add("baidu");
        command.add("--category");
        command.add(trackName);
        command.add("--keyword");
        command.add(keyword);

        log.info("[GenerationTaskExecutor] 执行关键字图片下载: {}", String.join(" ", command));

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[GenerationTaskExecutor] 关键字图片下载超时");
                return null;
            }

            int exitCode = process.exitValue();
            String stdout = output.toString();
            if (exitCode != 0) {
                log.warn("[GenerationTaskExecutor] 关键字图片下载脚本失败, exitCode={}, stdout={}", exitCode, stdout);
                return null;
            }

            // 查找 baidu- 开头的图片文件（排除 picsum 回退）
            Path categoryDir = tempOutputDir.resolve(trackName);
            if (!Files.exists(categoryDir) || !Files.isDirectory(categoryDir)) {
                log.warn("[GenerationTaskExecutor] 下载目录不存在: {}", categoryDir);
                return null;
            }

            Path targetImage = null;
            try (Stream<Path> paths = Files.list(categoryDir)) {
                targetImage = paths
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.startsWith("baidu-") && (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"));
                        })
                        .findFirst()
                        .orElse(null);
            }

            if (targetImage == null) {
                log.warn("[GenerationTaskExecutor] 未找到百度来源的图片文件，可能搜索无结果或回退到了picsum");
                return null;
            }

            // 复制到 uploads/images/
            String uploadDir = System.getProperty("user.dir") + "/uploads/images/";
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String ext = "";
            String originalName = targetImage.getFileName().toString();
            if (originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            } else {
                ext = ".jpg";
            }
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            int randomNum = (int) (Math.random() * 10000);
            String newName = "baidu-" + timestamp + String.format("%04d", randomNum) + ext;

            // 极小概率重名检查
            com.example.blogger.entity.ImageLibrary existing = imageLibraryMapper.findByName(newName);
            if (existing != null) {
                newName = "baidu-" + timestamp + String.format("%04d", (int) (Math.random() * 10000)) + ext;
            }

            String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
            Path destPath = uploadPath.resolve(fileName);
            Files.copy(targetImage, destPath, StandardCopyOption.REPLACE_EXISTING);

            com.example.blogger.entity.ImageLibrary image = new com.example.blogger.entity.ImageLibrary();
            image.setId(UUID.randomUUID().toString().replace("-", ""));
            image.setName(newName);
            image.setUrl("/uploads/images/" + fileName);
            image.setCategories(new ObjectMapper().writeValueAsString(Collections.singletonList(trackId)));
            imageLibraryMapper.insert(image);

            log.info("[GenerationTaskExecutor] 关键字图片下载成功: name={}, url={}", newName, image.getUrl());
            return image;
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] 关键字图片下载异常: {}", e.getMessage());
            return null;
        } finally {
            // 清理临时目录
            try {
                deleteDirectory(tempOutputDir);
            } catch (Exception e) {
                log.warn("[GenerationTaskExecutor] 清理临时目录失败: {}", e.getMessage());
            }
        }
    }

    private String resolveScriptPath() {
        Path directPath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "py", "download_category_images.py");
        if (Files.exists(directPath)) {
            return directPath.toString();
        }
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("py/download_category_images.py")) {
            if (is != null) {
                Path tempScript = Files.createTempFile("download_category_images", ".py");
                Files.copy(is, tempScript, StandardCopyOption.REPLACE_EXISTING);
                tempScript.toFile().deleteOnExit();
                return tempScript.toString();
            }
        } catch (Exception e) {
            log.warn("[GenerationTaskExecutor] 从 classpath 读取脚本失败: {}", e.getMessage());
        }
        return null;
    }

    private void deleteDirectory(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (Exception e) {
                    log.warn("[GenerationTaskExecutor] 删除文件失败: {}", e.getMessage());
                }
            });
        }
    }

    /**
     * DOCX 生成结果。
     */
    private static class DocxResult {
        final String fileUrl;
        final String fileName;

        DocxResult(String fileUrl, String fileName) {
            this.fileUrl = fileUrl;
            this.fileName = fileName;
        }
    }

    /**
     * 图片插入结果。
     */
    private static class ImageInsertResult {
        final String content;
        final boolean imageInserted;

        ImageInsertResult(String content, boolean imageInserted) {
            this.content = content;
            this.imageInserted = imageInserted;
        }
    }
}
