package io.github.failureanalyzer.model;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Captures the full context of a test failure for AI analysis.
 */
public class FailureContext {

    private final String testDisplayName;
    private final String testClassName;
    private final String testMethodName;
    private final Throwable throwable;
    private final String testSourceCode;
    private final String testedClassSourceCode;

    public FailureContext(
            String testDisplayName,
            String testClassName,
            String testMethodName,
            Throwable throwable,
            String testSourceCode,
            String testedClassSourceCode) {
        this.testDisplayName = testDisplayName;
        this.testClassName = testClassName;
        this.testMethodName = testMethodName;
        this.throwable = throwable;
        this.testSourceCode = testSourceCode;
        this.testedClassSourceCode = testedClassSourceCode;
    }

    public String getTestDisplayName() { return testDisplayName; }
    public String getTestClassName() { return testClassName; }
    public String getTestMethodName() { return testMethodName; }
    public Throwable getThrowable() { return throwable; }
    public String getTestSourceCode() { return testSourceCode; }
    public String getTestedClassSourceCode() { return testedClassSourceCode; }

    public boolean hasTestSourceCode() {
        return testSourceCode != null && !testSourceCode.isBlank();
    }

    public boolean hasTestedClassSourceCode() {
        return testedClassSourceCode != null && !testedClassSourceCode.isBlank();
    }

    /**
     * Returns the exception type and message in a single line.
     */
    public String getExceptionSummary() {
        if (throwable == null) return "Unknown failure (no exception)";
        String type = throwable.getClass().getName();
        String msg = throwable.getMessage();
        return msg != null ? type + ": " + msg : type;
    }

    /**
     * Returns the full stack trace as a string.
     */
    public String getFullStackTrace() {
        if (throwable == null) return "";
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Returns the stack trace trimmed to the most relevant frames.
     * Filters out JUnit internals to highlight test and application frames.
     */
    public String getRelevantStackTrace() {
        if (throwable == null) return "";
        StackTraceElement[] elements = throwable.getStackTrace();
        String filtered = Arrays.stream(elements)
                .filter(e -> !e.getClassName().startsWith("org.junit.")
                        && !e.getClassName().startsWith("sun.reflect.")
                        && !e.getClassName().startsWith("java.lang.reflect.")
                        && !e.getClassName().startsWith("com.intellij.")
                        && !e.getClassName().startsWith("org.gradle."))
                .map(e -> "  at " + e)
                .collect(Collectors.joining("\n"));

        return throwable.getClass().getName() + ": " + throwable.getMessage() + "\n" + filtered;
    }

    @Override
    public String toString() {
        return "FailureContext{" +
                "testClass='" + testClassName + '\'' +
                ", testMethod='" + testMethodName + '\'' +
                ", exception='" + getExceptionSummary() + '\'' +
                '}';
    }
}
