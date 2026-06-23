package com.example.blogger.scheduler;

import com.example.blogger.entity.Config;
import com.example.blogger.entity.TitleGenerationTask;
import com.example.blogger.mapper.ConfigMapper;
import com.example.blogger.mapper.TitleGenerationTaskMapper;
import com.example.blogger.service.GenerationTaskExecutor;
import com.example.blogger.service.TitleGenerationTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文章生成任务定时调度器。
 * 负责定时扫描 pending 任务、控制并发、并将任务委派给 {@link GenerationTaskExecutor} 执行。
 * 本身不包含任务生成逻辑，只关注“何时/以何种并发执行任务”。
 */
@Component
public class GenerationTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(GenerationTaskScheduler.class);
    private static final String CONCURRENCY_CONFIG_KEY = "generation_task_concurrency";
    private static final int DEFAULT_CONCURRENCY = 1;
    private static final int MAX_CONCURRENCY = 10;

    private final TitleGenerationTaskMapper taskMapper;
    private final TitleGenerationTaskService taskService;
    private final ConfigMapper configMapper;
    private final GenerationTaskExecutor generationTaskExecutor;

    private final ExecutorService executorService;
    private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    public GenerationTaskScheduler(TitleGenerationTaskMapper taskMapper,
                                   TitleGenerationTaskService taskService,
                                   ConfigMapper configMapper,
                                   GenerationTaskExecutor generationTaskExecutor) {
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.configMapper = configMapper;
        this.generationTaskExecutor = generationTaskExecutor;
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENCY);
    }

    /**
     * 定时扫描 pending 任务，根据配置的并发数提交到线程池执行
     */
    @Scheduled(fixedDelay = 10000)
    public void processTasks() {
        int concurrency = getConcurrency();
        int availableSlots = concurrency - runningTasks.size();

        if (availableSlots <= 0) {
            log.debug("[GenerationTaskScheduler] 当前运行 {} 个任务，并发上限 {}，无可用槽位", runningTasks.size(), concurrency);
            return;
        }

        log.debug("[GenerationTaskScheduler] 当前运行 {} 个任务，并发上限 {}，本次可启动 {} 个新任务",
                runningTasks.size(), concurrency, availableSlots);

        for (int i = 0; i < availableSlots; i++) {
            TitleGenerationTask task = taskMapper.findOnePending();
            if (task == null) {
                log.debug("[GenerationTaskScheduler] 无 pending 任务，停止扫描");
                break;
            }

            int pendingCount = taskMapper.countByStatus("pending");
            log.info("[GenerationTaskScheduler] 开始处理任务: id={}, 标题: {}, 队列中还剩 {} 个 pending 任务",
                    task.getId(), task.getTitle(), pendingCount);

            // 立即标记为 processing，防止被其他调度周期重复取
            taskService.updateStatus(task.getId(), "processing", null);
            taskService.updateProcessStartedAt(task.getId(), LocalDateTime.now());
            taskService.updateProgress(task.getId(), 1, "构建提示词...");

            runningTasks.add(task.getId());
            executorService.submit(() -> {
                try {
                    generationTaskExecutor.executeTask(task);
                } finally {
                    runningTasks.remove(task.getId());
                }
            });
        }
    }

    private int getConcurrency() {
        try {
            Config cfg = configMapper.findByKey(CONCURRENCY_CONFIG_KEY);
            if (cfg != null && cfg.getConfigValue() != null) {
                int val = Integer.parseInt(cfg.getConfigValue().trim());
                return Math.max(1, Math.min(MAX_CONCURRENCY, val));
            }
        } catch (Exception e) {
            log.warn("[GenerationTaskScheduler] 读取并发配置失败，使用默认值 {}", DEFAULT_CONCURRENCY);
        }
        return DEFAULT_CONCURRENCY;
    }
}
