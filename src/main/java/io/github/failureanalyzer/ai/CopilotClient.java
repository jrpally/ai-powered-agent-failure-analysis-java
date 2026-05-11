package io.github.failureanalyzer.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.failureanalyzer.config.AnalyzerConfig;
import io.github.failureanalyzer.model.AnalysisResult;
import io.github.failureanalyzer.model.FailureContext;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP client that sends failure context to the GitHub Copilot API (or a compatible endpoint)
 * and returns an AI-generated analysis.
 *
 * <h3>Provider resolution order:</h3>
 * <ol>
 *   <li>Custom endpoint — if {@code failureanalyzer.endpoint} is set</li>
 *   <li>GitHub Copilot — if {@code GITHUB_TOKEN} is set (exchanges PAT for session token)</li>
 *   <li>GitHub Models — if {@code GITHUB_TOKEN} is set (uses PAT directly, simpler)</li>
 *   <li>OpenAI — if {@code OPENAI_API_KEY} is set</li>
 * </ol>
 */
public class CopilotClient {

    private static final Logger log = LoggerFactory.getLogger(CopilotClient.class);

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final String SYSTEM_PROMPT =
            "You are an expert Java developer and test engineer specialized in diagnosing test failures. " +
            "When given a failing test's details, you provide a concise, actionable analysis. " +
            "Structure your response with these sections:\n\n" +
            "## Root Cause\n" +
            "One or two sentences explaining the exact reason for the failure.\n\n" +
            "## Analysis\n" +
            "Detailed technical explanation of why this occurred, referencing specific code or assertions.\n\n" +
            "## Suggested Fix\n" +
            "Concrete code change(s) or action(s) to resolve the failure. " +
            "Include code snippets where relevant.\n\n" +
            "## Prevention\n" +
            "Brief advice on how to prevent this class of failure in future tests.";

    private final AnalyzerConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Cached Copilot session token (refreshed automatically when expired)
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    public CopilotClient(AnalyzerConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Analyzes a test failure using the configured AI provider.
     *
     * @param context failure context captured by the JUnit extension
     * @return analysis result (may indicate failure if the API is unavailable)
     */
    public AnalysisResult analyze(FailureContext context) {
        String prompt = buildPrompt(context);

        if (config.isVerbose()) {
            log.info("=== Failure Analyzer Prompt ===\n{}", prompt);
        }

        // --- Provider resolution ---
        try {
            if (config.hasCustomEndpoint()) {
                return callOpenAICompatible(context, prompt,
                        config.getCustomEndpoint(), resolveCustomApiKey());
            }

            if (config.hasGitHubToken()) {
                // Try GitHub Copilot first (token exchange), fall back to GitHub Models
                AnalysisResult result = tryGitHubCopilot(context, prompt);
                if (result != null) return result;

                log.debug("Copilot token exchange failed or unavailable; trying GitHub Models");
                return callOpenAICompatible(context, prompt,
                        AnalyzerConfig.GITHUB_MODELS_ENDPOINT,
                        "Bearer " + config.getGithubToken());
            }

            if (config.hasOpenAiKey()) {
                return callOpenAICompatible(context, prompt,
                        "https://api.openai.com/v1/chat/completions",
                        "Bearer " + config.getOpenAiApiKey());
            }

            return AnalysisResult.failure(context.getTestDisplayName(),
                    "No AI provider configured. Set GITHUB_TOKEN or OPENAI_API_KEY environment variable, " +
                    "or configure failureanalyzer.endpoint system property.");

        } catch (Exception e) {
            log.error("Unexpected error during AI analysis", e);
            return AnalysisResult.failure(context.getTestDisplayName(),
                    "Unexpected error: " + e.getMessage());
        }
    }

    // =========================================================================
    // GitHub Copilot (token exchange flow)
    // =========================================================================

    private AnalysisResult tryGitHubCopilot(FailureContext context, String prompt) {
        try {
            String sessionToken = getOrRefreshCopilotToken();
            if (sessionToken == null) return null;

            return callOpenAICompatible(context, prompt,
                    AnalyzerConfig.COPILOT_COMPLETIONS_ENDPOINT,
                    "Bearer " + sessionToken);
        } catch (Exception e) {
            log.debug("GitHub Copilot call failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Returns a valid Copilot session token, refreshing the cache if needed.
     * The session token is exchanged from the GitHub PAT and expires in ~25 minutes.
     */
    private String getOrRefreshCopilotToken() throws IOException {
        CachedToken cached = cachedToken.get();
        if (cached != null && cached.isValid()) {
            return cached.token;
        }

        log.debug("Exchanging GitHub PAT for Copilot session token");
        Request request = new Request.Builder()
                .url(AnalyzerConfig.COPILOT_TOKEN_ENDPOINT)
                .addHeader("Authorization", "token " + config.getGithubToken())
                .addHeader("Accept", "application/json")
                .addHeader("Editor-Version", "vscode/1.89.0")
                .addHeader("Editor-Plugin-Version", "copilot-chat/0.16.0")
                .addHeader("User-Agent", "JavaFailureAnalyzer/1.0.0")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.debug("Copilot token exchange returned HTTP {}", response.code());
                return null;
            }

            JsonNode json = objectMapper.readTree(response.body().string());
            String token = json.path("token").asText(null);
            int refreshIn = json.path("refresh_in").asInt(1500);

            if (token == null || token.isBlank()) return null;

            // Cache with a safety buffer of 60 seconds before actual expiry
            Instant expiry = Instant.now().plusSeconds(refreshIn - 60);
            cachedToken.set(new CachedToken(token, expiry));
            log.debug("Copilot session token acquired (valid for ~{}s)", refreshIn - 60);
            return token;
        }
    }

    // =========================================================================
    // Generic OpenAI-compatible chat completions call
    // =========================================================================

    private AnalysisResult callOpenAICompatible(
            FailureContext context,
            String userPrompt,
            String endpoint,
            String authHeader) throws IOException {

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModel());
        body.put("temperature", 0.2);
        body.put("max_tokens", config.getMaxTokens());
        body.put("stream", false);

        ArrayNode messages = body.putArray("messages");

        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", SYSTEM_PROMPT);

        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);

        String bodyJson = objectMapper.writeValueAsString(body);

        Request request = new Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", authHeader)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Editor-Version", "vscode/1.89.0")
                .addHeader("Editor-Plugin-Version", "copilot-chat/0.16.0")
                .addHeader("Copilot-Integration-Id", "java-failure-analyzer")
                .addHeader("User-Agent", "JavaFailureAnalyzer/1.0.0")
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        log.debug("Sending failure analysis request to {}", endpoint);

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                return AnalysisResult.failure(context.getTestDisplayName(),
                        "Empty response from AI endpoint (HTTP " + response.code() + ")");
            }

            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                log.warn("AI endpoint returned HTTP {}: {}", response.code(), responseBody);
                return AnalysisResult.failure(context.getTestDisplayName(),
                        "AI endpoint error HTTP " + response.code() + ": " + truncate(responseBody, 200));
            }

            JsonNode json = objectMapper.readTree(responseBody);
            String content = json.path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asText(null);

            if (content == null || content.isBlank()) {
                return AnalysisResult.failure(context.getTestDisplayName(),
                        "AI returned an empty analysis.");
            }

            return AnalysisResult.success(context.getTestDisplayName(), content.trim());
        }
    }

    // =========================================================================
    // Prompt construction
    // =========================================================================

    private String buildPrompt(FailureContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("A JUnit 5 test has failed. Please analyze the failure.\n\n");

        sb.append("## Test Information\n");
        sb.append("- **Test class**: `").append(ctx.getTestClassName()).append("`\n");
        sb.append("- **Test method**: `").append(ctx.getTestMethodName()).append("`\n");
        sb.append("- **Display name**: ").append(ctx.getTestDisplayName()).append("\n\n");

        sb.append("## Exception\n");
        sb.append("```\n").append(ctx.getExceptionSummary()).append("\n```\n\n");

        sb.append("## Stack Trace\n");
        sb.append("```\n").append(ctx.getRelevantStackTrace()).append("\n```\n\n");

        if (ctx.hasTestSourceCode()) {
            sb.append("## Test Source Code\n");
            sb.append("```java\n").append(ctx.getTestSourceCode()).append("\n```\n\n");
        }

        if (ctx.hasTestedClassSourceCode()) {
            sb.append("## Source Code Under Test\n");
            sb.append("```java\n").append(ctx.getTestedClassSourceCode()).append("\n```\n\n");
        }

        sb.append("Please provide your analysis following the requested structure.");
        return sb.toString();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String resolveCustomApiKey() {
        if (config.hasOpenAiKey()) return "Bearer " + config.getOpenAiApiKey();
        if (config.hasGitHubToken()) return "Bearer " + config.getGithubToken();
        return "";
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    // =========================================================================
    // Token cache
    // =========================================================================

    private static class CachedToken {
        final String token;
        final Instant expiresAt;

        CachedToken(String token, Instant expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
