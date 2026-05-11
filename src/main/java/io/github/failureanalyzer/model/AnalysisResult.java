package io.github.failureanalyzer.model;

/**
 * Holds the result of an AI-powered failure analysis.
 */
public class AnalysisResult {

    private final String testName;
    private final String rawAnalysis;
    private final boolean successful;
    private final String errorMessage;

    private AnalysisResult(
            String testName,
            String rawAnalysis,
            boolean successful,
            String errorMessage) {
        this.testName = testName;
        this.rawAnalysis = rawAnalysis;
        this.successful = successful;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful analysis result.
     */
    public static AnalysisResult success(String testName, String rawAnalysis) {
        return new AnalysisResult(testName, rawAnalysis, true, null);
    }

    /**
     * Creates a failed analysis result (e.g. API unavailable or misconfigured).
     */
    public static AnalysisResult failure(String testName, String errorMessage) {
        return new AnalysisResult(testName, null, false, errorMessage);
    }

    public String getTestName() { return testName; }
    public String getRawAnalysis() { return rawAnalysis; }
    public boolean isSuccessful() { return successful; }
    public String getErrorMessage() { return errorMessage; }

    /**
     * Returns a human-readable summary suitable for console output.
     */
    public String toDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║          🤖  GitHub Copilot — Failure Analysis              ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");
        sb.append("║  Test: ").append(truncate(testName, 55)).append("\n");
        sb.append("╠══════════════════════════════════════════════════════════════╣\n");

        if (successful && rawAnalysis != null) {
            for (String line : rawAnalysis.split("\n")) {
                sb.append("  ").append(line).append("\n");
            }
        } else {
            sb.append("  ⚠ Analysis failed: ").append(errorMessage).append("\n");
        }

        sb.append("╚══════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    @Override
    public String toString() {
        return successful ? rawAnalysis : ("Analysis failed: " + errorMessage);
    }
}
