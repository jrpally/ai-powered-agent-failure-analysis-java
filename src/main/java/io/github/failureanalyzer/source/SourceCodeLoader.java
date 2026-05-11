package io.github.failureanalyzer.source;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Locates and loads Java source files from common Maven/Gradle project layouts.
 *
 * <p>Searched source roots (relative to working directory):
 * <ul>
 *   <li>{@code src/main/java} — production source</li>
 *   <li>{@code src/test/java} — test source</li>
 *   <li>{@code src/main/kotlin} — Kotlin production source</li>
 *   <li>{@code src/test/kotlin} — Kotlin test source</li>
 * </ul>
 */
public class SourceCodeLoader {

    private static final Logger log = LoggerFactory.getLogger(SourceCodeLoader.class);

    private static final String[] SOURCE_ROOTS = {
        "src/main/java",
        "src/test/java",
        "src/main/kotlin",
        "src/test/kotlin",
        "src/main/groovy",
        "src/test/groovy"
    };

    private SourceCodeLoader() {}

    /**
     * Attempts to load the source code for the given class.
     *
     * @param clazz the class whose source to find
     * @return source code string, or {@code null} if not found
     */
    public static String loadSourceCode(Class<?> clazz) {
        if (clazz == null) return null;
        return loadSourceCode(clazz.getName());
    }

    /**
     * Attempts to load the source code for the given fully-qualified class name.
     *
     * @param fullyQualifiedClassName e.g. {@code com.example.MyService}
     * @return source code string, or {@code null} if not found
     */
    public static String loadSourceCode(String fullyQualifiedClassName) {
        if (fullyQualifiedClassName == null || fullyQualifiedClassName.isBlank()) return null;

        // Strip inner class suffixes (e.g. OuterClass$Inner → OuterClass)
        String topLevel = fullyQualifiedClassName.contains("$")
                ? fullyQualifiedClassName.substring(0, fullyQualifiedClassName.indexOf('$'))
                : fullyQualifiedClassName;

        String relativePath = topLevel.replace('.', '/') + ".java";

        Path workingDir = Paths.get(System.getProperty("user.dir", "."));

        for (String root : SOURCE_ROOTS) {
            Path candidate = workingDir.resolve(root).resolve(relativePath);
            Optional<String> content = readFile(candidate);
            if (content.isPresent()) {
                log.debug("Loaded source for '{}' from {}", fullyQualifiedClassName, candidate);
                return content.get();
            }
        }

        log.debug("Source not found for '{}'", fullyQualifiedClassName);
        return null;
    }

    /**
     * Loads a source file from an absolute path.
     *
     * @param absolutePath absolute path to the file
     * @return file contents, or {@code null} if not readable
     */
    public static String loadFromPath(String absolutePath) {
        if (absolutePath == null) return null;
        return readFile(Paths.get(absolutePath)).orElse(null);
    }

    /**
     * Tries to resolve the production class under test from a test class name.
     * Uses the convention that {@code FooTest → Foo} and {@code FooTests → Foo}.
     *
     * @param testClass the JUnit test class
     * @return source code of the inferred class under test, or {@code null}
     */
    public static String loadTestedClassSource(Class<?> testClass) {
        if (testClass == null) return null;
        String name = testClass.getName();

        // Strip common suffixes: Test, Tests, IT, Spec
        String candidate = stripTestSuffix(name);
        if (candidate.equals(name)) return null; // nothing stripped — skip inference

        return loadSourceCode(candidate);
    }

    // -------------------------------------------------------------------------

    private static Optional<String> readFile(Path path) {
        if (!Files.exists(path) || !Files.isRegularFile(path)) return Optional.empty();
        try {
            return Optional.of(Files.readString(path));
        } catch (IOException e) {
            log.warn("Could not read source file {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private static String stripTestSuffix(String className) {
        String[] suffixes = {"Tests", "Test", "IT", "Spec", "Specification"};
        for (String suffix : suffixes) {
            if (className.endsWith(suffix)) {
                return className.substring(0, className.length() - suffix.length());
            }
        }
        return className;
    }
}
