package io.github.failureanalyzer;

import io.github.failureanalyzer.ai.CopilotClient;
import io.github.failureanalyzer.config.AnalyzerConfig;
import io.github.failureanalyzer.model.AnalysisResult;
import io.github.failureanalyzer.model.FailureContext;
import io.github.failureanalyzer.source.SourceCodeLoader;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * JUnit 5 extension that automatically analyzes test failures using GitHub Copilot AI.
 *
 * <h3>Usage — option 1: per-class annotation</h3>
 * <pre>{@code
 * @ExtendWith(FailureAnalyzerExtension.class)
 * class MyServiceTest {
 *     // ...
 * }
 * }</pre>
 *
 * <h3>Usage — option 2: auto-registration (recommended)</h3>
 * Add the following to {@code src/test/resources/junit-platform.properties}:
 * <pre>
 * junit.jupiter.extensions.autodetection.enabled=true
 * </pre>
 * Then add this library to your test classpath. The extension registers itself
 * via the {@code META-INF/services/org.junit.jupiter.api.extension.Extension}
 * service-loader file bundled in the JAR.
 *
 * <h3>Configuration</h3>
 * Set the {@code GITHUB_TOKEN} environment variable (or {@code OPENAI_API_KEY} as fallback).
 * See {@link AnalyzerConfig} for the full list of configuration options.
 */
public class FailureAnalyzerExtension implements TestWatcher, BeforeEachCallback {

    private static final Logger log = LoggerFactory.getLogger(FailureAnalyzerExtension.class);

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AnalyzerConfig config;
    private final CopilotClient copilotClient;

    /**
     * Default constructor — picks up configuration from environment / system properties.
     */
    public FailureAnalyzerExtension() {
        this.config = AnalyzerConfig.fromEnvironment();
        this.copilotClient = new CopilotClient(config);
        log.debug("FailureAnalyzerExtension initialized with config: {}", config);
    }

    /**
     * Constructor for tests / programmatic wiring.
     */
    public FailureAnalyzerExtension(AnalyzerConfig config) {
        this.config = config;
        this.copilotClient = new CopilotClient(config);
    }

    // =========================================================================
    // BeforeEachCallback — log that the extension is active
    // =========================================================================

    @Override
    public void beforeEach(ExtensionContext context) {
        log.trace("FailureAnalyzerExtension monitoring: {}", context.getDisplayName());
    }

    // =========================================================================
    // TestWatcher callbacks
    // =========================================================================

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        log.info("Test failed: '{}' — invoking AI failure analysis...",
                context.getDisplayName());

        FailureContext failureContext = buildFailureContext(context, cause);
        AnalysisResult result = copilotClient.analyze(failureContext);

        // Always print to console
        System.out.println(result.toDisplayString());

        if (result.isSuccessful()) {
            log.info("AI analysis complete for '{}'", context.getDisplayName());
        } else {
            log.warn("AI analysis could not be completed: {}", result.getErrorMessage());
        }

        // Optionally persist to file
        if (config.isSaveToFile()) {
            saveAnalysisToFile(context, result);
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        log.trace("Test passed: {}", context.getDisplayName());
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        log.trace("Test aborted: {}", context.getDisplayName());
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        log.trace("Test disabled: {} ({})", context.getDisplayName(),
                reason.orElse("no reason"));
    }

    // =========================================================================
    // Context assembly
    // =========================================================================

    private FailureContext buildFailureContext(ExtensionContext context, Throwable cause) {
        Class<?> testClass = context.getRequiredTestClass();
        String testClassName = testClass.getName();
        String testMethodName = context.getRequiredTestMethod().getName();
        String displayName = context.getDisplayName();

        String testSourceCode = null;
        String testedSourceCode = null;

        if (config.isIncludeSourceCode()) {
            // Load the test class source
            testSourceCode = SourceCodeLoader.loadSourceCode(testClass);

            // Attempt to load the production class under test
            testedSourceCode = SourceCodeLoader.loadTestedClassSource(testClass);

            if (testSourceCode != null) {
                log.debug("Test source loaded for '{}'", testClassName);
            } else {
                log.debug("Test source not found for '{}' — analysis will rely on stack trace only",
                        testClassName);
            }

            if (testedSourceCode != null) {
                log.debug("Production source loaded for inferred class under test");
            }
        }

        return new FailureContext(
                displayName,
                testClassName,
                testMethodName,
                cause,
                testSourceCode,
                testedSourceCode);
    }

    // =========================================================================
    // File output
    // =========================================================================

    private void saveAnalysisToFile(ExtensionContext context, AnalysisResult result) {
        try {
            Path outputDir = Paths.get(config.getOutputDirectory());
            Files.createDirectories(outputDir);

            String safeName = context.getDisplayName()
                    .replaceAll("[^a-zA-Z0-9_\\-]", "_")
                    .replaceAll("_{2,}", "_");

            String timestamp = LocalDateTime.now().format(FILE_TIMESTAMP);
            String fileName = timestamp + "_" + safeName + ".md";
            Path outputFile = outputDir.resolve(fileName);

            String content = buildMarkdownReport(context, result);
            Files.writeString(outputFile, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Failure analysis saved to: {}", outputFile.toAbsolutePath());

        } catch (IOException e) {
            log.warn("Could not save failure analysis to file: {}", e.getMessage());
        }
    }

    private String buildMarkdownReport(ExtensionContext context, AnalysisResult result) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        StringBuilder sb = new StringBuilder();

        sb.append("# Failure Analysis Report\n\n");
        sb.append("| Field | Value |\n|-------|-------|\n");
        sb.append("| Timestamp | ").append(timestamp).append(" |\n");
        sb.append("| Test | ").append(context.getDisplayName()).append(" |\n");
        sb.append("| Class | `").append(context.getRequiredTestClass().getName()).append("` |\n");
        sb.append("| Method | `").append(context.getRequiredTestMethod().getName()).append("` |\n\n");

        sb.append("## AI Analysis\n\n");
        if (result.isSuccessful()) {
            sb.append(result.getRawAnalysis()).append("\n");
        } else {
            sb.append("**Analysis failed**: ").append(result.getErrorMessage()).append("\n");
        }

        return sb.toString();
    }
}
