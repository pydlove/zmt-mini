package com.example.blogger.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.blogger.entity.LLMConfig;
import com.example.blogger.mapper.LLMConfigMapper;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Service
public class LLMService {

    private static final Logger log = LoggerFactory.getLogger(LLMService.class);

    private static final String KIMI_BASE_URL = "https://api.kimi.com/coding";
    private static final String MINIMAX_API_URL = "https://api.minimax.chat/v1/chat/completions";

    private static final int CONNECT_TIMEOUT_MS = 300000;
    private static final int READ_TIMEOUT_MS = 3000000;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 5000;

    private final LLMConfigMapper llmConfigMapper;
    private final ObjectMapper objectMapper;

    // 复用 HttpClient 和 SSLContext，避免每次请求都重建连接
    private static final SSLContext SHARED_SSL_CONTEXT;
    private static final java.net.http.HttpClient SHARED_HTTP_CLIENT;

    static {
        SHARED_SSL_CONTEXT = createInsecureSslContext();
        SHARED_HTTP_CLIENT = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .sslContext(SHARED_SSL_CONTEXT)
                .build();
    }

    @Autowired
    public LLMService(LLMConfigMapper llmConfigMapper) {
        this.llmConfigMapper = llmConfigMapper;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取当前配置的默认模型类型
     */
    public String getSelectedModelType() {
        LLMConfig active = llmConfigMapper.findActive();
        String provider = (active != null && active.getProvider() != null) ? active.getProvider() : "kimi";
        log.info("[LLMService] 读取模型配置 activeProvider={}", provider);
        return provider;
    }

    /**
     * 调用大模型生成内容（流式，边收边拼接，避免 504 超时）
     */
    public String generateContent(String prompt) {
        String modelType = getSelectedModelType();
        log.info("[LLMService] 生成内容路由: modelType={}", modelType);
        if ("minimax".equals(modelType)) {
            return streamMinimax(prompt);
        } else {
            return streamKimi(prompt);
        }
    }

    // ==================== Kimi 流式 ====================

    private String streamKimi(String prompt) {

        log.info("[LLMService] Kimi prompt: {}", prompt);

        LLMConfig config = llmConfigMapper.findByProvider("kimi");
        String apiKey = config != null ? config.getApiKey() : null;
        String model = config != null ? config.getModel() : null;
        if (apiKey != null) apiKey = apiKey.trim();
        log.info("[LLMService] Kimi stream: apiKey length={}, model={}", apiKey != null ? apiKey.length() : 0, model);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Kimi API Key 未配置，请在系统配置中设置");
        }
        if (model == null || model.isEmpty()) {
            model = "kimi-k2-6";
        }

        String escapedPrompt;
        try {
            escapedPrompt = objectMapper.writeValueAsString(prompt);
        } catch (Exception e) {
            escapedPrompt = "\"" + prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
        final String bodyJson = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":" + escapedPrompt + "}],\"stream\":true,\"max_tokens\":4096}";

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(KIMI_BASE_URL + "/v1/chat/completions"))
                .timeout(java.time.Duration.ofMillis(READ_TIMEOUT_MS))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "claude-cli/2.1.110 (external, cli)")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson, java.nio.charset.StandardCharsets.UTF_8))
                .build();

        try {
            log.info("[LLMService] Kimi 流式请求开始");
            var response = SHARED_HTTP_CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines());
            int statusCode = response.statusCode();
            log.info("[LLMService] Kimi 流式状态码: {}", statusCode);
            if (statusCode != 200) {
                if (statusCode == 401 || statusCode == 403) {
                    throw new RuntimeException("Kimi API 认证失败 (" + statusCode + ")");
                }
                throw new RuntimeException("Kimi API 请求失败 (" + statusCode + ")");
            }

            StringBuilder content = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            int[] chunkCount = {0};
            int[] reasoningCount = {0};
            boolean[] firstLineLogged = {false};
            response.body().forEach(line -> {
                if (line.isEmpty()) return;
                if (!firstLineLogged[0]) {
                    log.debug("[LLMService] Kimi 流式首行: {}", line);
                    firstLineLogged[0] = true;
                }
                String data = null;
                if (line.startsWith("data:")) {
                    data = line.substring(5).trim();
                } else if (line.startsWith("{")) {
                    data = line.trim();
                }
                if (data == null || "[DONE]".equals(data)) return;
                try {
                    JsonNode chunk = objectMapper.readTree(data);
                    JsonNode delta = chunk.path("choices").path(0).path("delta");
                    if (delta.has("content")) {
                        String part = delta.get("content").asText();
                        if (part != null && !part.isEmpty()) {
                            content.append(part);
                            chunkCount[0]++;
                        }
                    }
                    if (delta.has("reasoning_content")) {
                        String part = delta.get("reasoning_content").asText();
                        if (part != null && !part.isEmpty()) {
                            reasoning.append(part);
                            reasoningCount[0]++;
                        }
                    }
                    if ((chunkCount[0] > 0 || reasoningCount[0] > 0) && (chunkCount[0] + reasoningCount[0]) % 200 == 0) {
                        log.info("[LLMService] Kimi 流式接收中... contentChunks={}, reasoningChunks={}, contentLen={}, reasoningLen={}", chunkCount[0], reasoningCount[0], content.length(), reasoning.length());
                    }
                } catch (Exception e) {
                    log.warn("[LLMService] Kimi 流式解析行失败: line={}, error={}", line, e.getMessage());
                }
            });

            String result = content.toString();
            if (result.isEmpty() && reasoning.length() > 0) {
                result = reasoning.toString();
                log.info("[LLMService] Kimi 流式 content 为空，使用 reasoning_content 回退");
            }
            log.info("[LLMService] Kimi 流式接收完成: contentChunks={}, reasoningChunks={}, 总长度={}, 预览: {}", chunkCount[0], reasoningCount[0], result.length(), result.length() > 200 ? result.substring(0, 200) + "..." : result);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Kimi API 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * Kimi 阻塞模式：用于 kimi-k2-6 reasoning_content 回退
     */
    private String callKimiBlockingWithReasoning(String prompt, String apiKey, String model) {
        String escapedPrompt;
        try {
            escapedPrompt = objectMapper.writeValueAsString(prompt);
        } catch (Exception e) {
            escapedPrompt = "\"" + prompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
        final String bodyJson = "{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":" + escapedPrompt + "}],\"stream\":false}";

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(KIMI_BASE_URL + "/v1/chat/completions"))
                .timeout(java.time.Duration.ofMillis(READ_TIMEOUT_MS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "claude-cli/2.1.110 (external, cli)")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson, java.nio.charset.StandardCharsets.UTF_8))
                .build();

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                java.net.http.HttpResponse<String> response = SHARED_HTTP_CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                int statusCode = response.statusCode();
                if (statusCode == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode choices = root.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode message = choices.get(0).get("message");
                        String content = message.has("content") ? message.get("content").asText() : "";
                        String reasoning = message.has("reasoning_content") ? message.get("reasoning_content").asText() : "";
                        if (content != null && !content.isEmpty()) return content;
                        if (reasoning != null && !reasoning.isEmpty()) return reasoning;
                        return "";
                    }
                    throw new RuntimeException("Kimi API 返回格式异常: " + response.body());
                }
                if (statusCode == 401 || statusCode == 403) {
                    throw new RuntimeException("Kimi API 认证失败 (" + statusCode + "): " + response.body());
                }
                if (statusCode == 504 || statusCode == 502 || statusCode == 503 || statusCode == 429) {
                    log.warn("[LLMService] Kimi 阻塞请求失败({})，尝试 {}/{}", statusCode, attempt, MAX_RETRIES);
                    if (attempt < MAX_RETRIES) sleep(RETRY_DELAY_MS);
                    else throw new RuntimeException("Kimi API 请求失败 (" + statusCode + "): " + response.body());
                } else {
                    throw new RuntimeException("Kimi API 请求失败 (" + statusCode + "): " + response.body());
                }
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("认证失败")) throw e;
                if (attempt < MAX_RETRIES) sleep(RETRY_DELAY_MS);
                else throw e;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES) sleep(RETRY_DELAY_MS);
                else throw new RuntimeException("Kimi API 调用失败: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Kimi API 重试 " + MAX_RETRIES + " 次后仍失败");
    }

    // ==================== MiniMax 流式 ====================

    private String streamMinimax(String prompt) {

        log.info("[LLMService] MiniMax prompt: {}", prompt);

        LLMConfig config = llmConfigMapper.findByProvider("minimax");
        String apiKey = config != null ? config.getApiKey() : null;
        String model = config != null ? config.getModel() : null;
        log.info("[LLMService] MiniMax stream: apiKey length={}, model={}", apiKey != null ? apiKey.length() : 0, model);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("MiniMax API Key 未配置");
        }
        if (model == null || model.isEmpty()) {
            model = "MiniMax-M2.7";
        }

        // 拆分 system prompt 和 user prompt
        String systemPrompt = "你是一位资深中文自媒体写手，擅长撰写自然流畅、口语化的公众号文章。";
        String userPrompt = prompt;
        int sysIdx = prompt.indexOf("【系统指令】");
        if (sysIdx >= 0) {
            // MiniMax 对长 system prompt 敏感，把 JSON 格式说明压缩后放到 system，原始写作要求保留在 user
            String jsonInstruction = prompt.substring(sysIdx);
            systemPrompt = "你是一位资深中文自媒体写手。请严格按以下要求输出：\n"
                    + "1. 直接输出合法 JSON 数组，不要 markdown 代码块、前言、总结或思考过程。\n"
                    + "2. 数组元素字段：type('section'/'paragraph'), title, marker, markerText, content, styleHint。\n"
                    + "3. 示例：[{\"type\":\"section\",\"title\":\"01 | 标题\",\"marker\":\"01\",\"markerText\":\"标题\",\"content\":\"正文\",\"styleHint\":\"normal\"}]";
            userPrompt = prompt.substring(0, sysIdx).trim() + "\n\n" + jsonInstruction;
        } else {
            systemPrompt += "请直接输出文章正文，不要输出思考过程，不要复述用户要求，不要加任何前言或总结。";
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", new Object[]{
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        });
        body.put("stream", true);
        body.put("max_tokens", 4096);
        body.put("temperature", 0.1);
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("序列化 MiniMax 请求体失败", e);
        }

        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(MINIMAX_API_URL))
                .timeout(java.time.Duration.ofMillis(READ_TIMEOUT_MS))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", "claude-cli/2.1.110 (external, cli)")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(bodyJson, java.nio.charset.StandardCharsets.UTF_8))
                .build();

        try {
            log.info("[LLMService] MiniMax 流式请求开始");
            var response = SHARED_HTTP_CLIENT.send(request, java.net.http.HttpResponse.BodyHandlers.ofLines());
            int statusCode = response.statusCode();
            log.info("[LLMService] MiniMax 流式状态码: {}", statusCode);
            if (statusCode != 200) {
                if (statusCode == 401 || statusCode == 403) {
                    throw new RuntimeException("MiniMax API 认证失败 (" + statusCode + ")");
                }
                throw new RuntimeException("MiniMax API 请求失败 (" + statusCode + ")");
            }

            StringBuilder content = new StringBuilder();
            int[] chunkCount = {0};
            response.body().forEach(line -> {
                if (line.isEmpty()) return;
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) return;
                    try {
                        JsonNode chunk = objectMapper.readTree(data);
                        JsonNode choices = chunk.path("choices");
                        if (choices.isArray() && choices.size() > 0) {
                            JsonNode delta = choices.get(0).path("delta");
                            if (delta.has("content")) {
                                String part = delta.get("content").asText();
                                if (part != null) {
                                    content.append(part);
                                    chunkCount[0]++;
                                    if (chunkCount[0] % 200 == 0) {
                                        log.info("[LLMService] MiniMax 流式接收中... 已收 {} chunks, 当前长度 {}", chunkCount[0], content.length());
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            });

            String result = content.toString();
            log.info("[LLMService] MiniMax 流式接收完成: 共 {} chunks, 总长度 {}, 预览: {}", chunkCount[0], result.length(), result.length() > 200 ? result.substring(0, 200) + "..." : result);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("MiniMax API 调用失败: " + e.getMessage(), e);
        }
    }

    // ==================== 工具方法 ====================

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 创建一个不验证服务器证书的 SSLContext，用于绕过 MiniMax 等 API 的证书信任问题。
     */
    private static SSLContext createInsecureSslContext() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("初始化 SSLContext 失败", e);
        }
    }
}
