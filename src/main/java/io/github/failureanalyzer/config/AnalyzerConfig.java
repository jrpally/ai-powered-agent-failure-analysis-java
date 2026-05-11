package io.github.failureanalyzer.config;

/**
 * Configuration for the Java Failure Analyzer extension.
 *
 * <p>Configuration is resolved in the following priority order:
 * <ol>
 *   <li>System properties (e.g. {@code -Dfailureanalyzer.github.token=...})</li>
 *   <li>Environment variables (e.g. {@code GITHUB_TOKEN=...})</li>
 *   <li>Built-in defaults</li>
 * </ol>
 *
 * <h3>Supported system properties / environment variables:</h3>
 * <pre>
 *   failureanalyzer.github.token   / GITHUB_TOKEN       — GitHub PAT (Copilot or GitHub Models access)
 *   failureanalyzer.openai.api.key / OPENAI_API_KEY     — OpenAI-compatible API key (fallback)
 *   failureanalyzer.endpoint                            — Custom chat completions endpoint URL
 *   failureanalyzer.model                               — Model name (default: gpt-4o)
 *   failureanalyzer.include.source  (true/false)        — Include source code in prompt (default: true)
 *   failureanalyzer.save.to.file    (true/false)        — Save analysis to file (default: false)
 *   failureanalyzer.output.dir                          — Output directory (default: target/failure-analysis)
 *   failureanalyzer.max.tokens                          — Max response tokens (default: 2000)
 *   failureanalyzer.verbose         (true/false)        — Print full prompts (default: false)
 * </pre>
 */
public class AnalyzerConfig {

    private static final String PROP_PREFIX = "failureanalyzer.";

    // GitHub Copilot token exchange endpoint
    public static final String COPILOT_TOKEN_ENDPOINT =
            "https://api.github.com/copilot_internal/v2/token";

    // GitHub Copilot chat completions endpoint
    public static final String COPILOT_COMPLETIONS_ENDPOINT =
            "https://api.githubcopilot.com/chat/completions";

    // GitHub Models endpoint (simpler: PAT used directly, no token exchange)
    public static final String GITHUB_MODELS_ENDPOINT =
            "https://models.inference.ai.azure.com/chat/completions";

    private final String githubToken;
    private final String openAiApiKey;
    private final String customEndpoint;
    private final String model;
    private final boolean includeSourceCode;
    private final boolean saveToFile;
    private final String outputDirectory;
    private final int maxTokens;
    private final boolean verbose;

    private AnalyzerConfig(
            String githubToken,
            String openAiApiKey,
            String customEndpoint,
            String model,
            boolean includeSourceCode,
            boolean saveToFile,
            String outputDirectory,
            int maxTokens,
            boolean verbose) {
        this.githubToken = githubToken;
        this.openAiApiKey = openAiApiKey;
        this.customEndpoint = customEndpoint;
        this.model = model != null ? model : "gpt-4o";
        this.includeSourceCode = includeSourceCode;
        this.saveToFile = saveToFile;
        this.outputDirectory = outputDirectory != null ? outputDirectory : "target/failure-analysis";
        this.maxTokens = maxTokens > 0 ? maxTokens : 2000;
        this.verbose = verbose;
    }

    /**
     * Loads configuration from system properties and environment variables.
     */
    public static AnalyzerConfig fromEnvironment() {
        String githubToken = getConfig("github.token", "GITHUB_TOKEN");
        String openAiApiKey = getConfig("openai.api.key", "OPENAI_API_KEY");
        String customEndpoint = getConfig("endpoint", null);
        String model = getConfig("model", null);
        boolean includeSource = Boolean.parseBoolean(
                getConfigWithDefault("include.source", "true"));
        boolean saveToFile = Boolean.parseBoolean(
                getConfigWithDefault("save.to.file", "false"));
        String outputDir = getConfig("output.dir", null);
        int maxTokens = parseIntSafe(getConfigWithDefault("max.tokens", "2000"));
        boolean verbose = Boolean.parseBoolean(
                getConfigWithDefault("verbose", "false"));

        return new AnalyzerConfig(
                githubToken, openAiApiKey, customEndpoint, model,
                includeSource, saveToFile, outputDir, maxTokens, verbose);
    }

    public boolean hasGitHubToken() {
        return githubToken != null && !githubToken.isBlank();
    }

    public boolean hasOpenAiKey() {
        return openAiApiKey != null && !openAiApiKey.isBlank();
    }

    public boolean hasCustomEndpoint() {
        return customEndpoint != null && !customEndpoint.isBlank();
    }

    public String getGithubToken() { return githubToken; }
    public String getOpenAiApiKey() { return openAiApiKey; }
    public String getCustomEndpoint() { return customEndpoint; }
    public String getModel() { return model; }
    public boolean isIncludeSourceCode() { return includeSourceCode; }
    public boolean isSaveToFile() { return saveToFile; }
    public String getOutputDirectory() { return outputDirectory; }
    public int getMaxTokens() { return maxTokens; }
    public boolean isVerbose() { return verbose; }

    private static String getConfig(String key, String envVar) {
        String propValue = System.getProperty(PROP_PREFIX + key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue.trim();
        }
        if (envVar != null) {
            String envValue = System.getenv(envVar);
            if (envValue != null && !envValue.isBlank()) {
                return envValue.trim();
            }
        }
        return null;
    }

    private static String getConfigWithDefault(String key, String defaultValue) {
        String value = getConfig(key, null);
        return value != null ? value : defaultValue;
    }

    private static int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 2000;
        }
    }

    @Override
    public String toString() {
        return "AnalyzerConfig{" +
                "hasGitHubToken=" + hasGitHubToken() +
                ", hasOpenAiKey=" + hasOpenAiKey() +
                ", customEndpoint='" + customEndpoint + '\'' +
                ", model='" + model + '\'' +
                ", includeSourceCode=" + includeSourceCode +
                ", saveToFile=" + saveToFile +
                ", outputDirectory='" + outputDirectory + '\'' +
                ", maxTokens=" + maxTokens +
                '}';
    }
}
