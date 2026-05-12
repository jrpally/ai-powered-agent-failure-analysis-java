# Java Failure Analyzer

A JUnit 5 extension that automatically analyzes test failures using GitHub Copilot AI. When a test fails, the extension captures the failure context — exception type, stack trace, and optionally the test and production source code — and sends it to the GitHub Copilot API for an intelligent, structured diagnosis.

---

## Features

- Hooks into JUnit 5 via `TestWatcher` — zero boilerplate in your tests
- Sends failure context (exception, stack trace, source code) to GitHub Copilot
- Returns a structured analysis: **Root Cause → Analysis → Suggested Fix → Prevention**
- Supports multiple AI providers: GitHub Copilot, GitHub Models, OpenAI, or any OpenAI-compatible endpoint
- Optionally saves analysis output to files for CI artifact archiving
- Auto-registration via JUnit's service loader (no annotation required)

---

## Requirements

- Java 25+
- Maven 3.9+
- A GitHub Personal Access Token (PAT) with GitHub Copilot or GitHub Models access

---

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.failureanalyzer</groupId>
    <artifactId>java-failure-analyzer</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

---

## Quick Start

### Option 1 — Per-class annotation

```java
import io.github.failureanalyzer.FailureAnalyzerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(FailureAnalyzerExtension.class)
class MyServiceTest {

    @Test
    void myTest() {
        // Any failure here is automatically analyzed by Copilot AI
    }
}
```

### Option 2 — Auto-registration (recommended)

Add to `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

The extension registers itself automatically via the `META-INF/services` service-loader file bundled in the JAR. No annotation needed on any test class.

Then set your token:

```bash
export GITHUB_TOKEN=ghp_yourPersonalAccessToken
mvn test
```

---

## Configuration

All options are read from **system properties** (e.g. `-Dfailureanalyzer.model=gpt-4o`) or **environment variables**, with system properties taking precedence.

| System Property                      | Environment Variable | Default                        | Description                                        |
| ------------------------------------ | -------------------- | ------------------------------ | -------------------------------------------------- |
| `failureanalyzer.github.token`       | `GITHUB_TOKEN`       | —                              | GitHub PAT (Copilot or GitHub Models access)       |
| `failureanalyzer.openai.api.key`     | `OPENAI_API_KEY`     | —                              | OpenAI-compatible API key (fallback provider)      |
| `failureanalyzer.endpoint`           | —                    | —                              | Custom chat completions endpoint URL               |
| `failureanalyzer.model`              | —                    | `gpt-4o`                       | Model name passed to the API                       |
| `failureanalyzer.include.source`     | —                    | `true`                         | Include test and production source in the prompt   |
| `failureanalyzer.save.to.file`       | —                    | `false`                        | Save analysis output to a file                     |
| `failureanalyzer.output.dir`         | —                    | `target/failure-analysis`      | Directory for saved analysis files                 |
| `failureanalyzer.max.tokens`         | —                    | `2000`                         | Maximum response tokens                            |
| `failureanalyzer.verbose`            | —                    | `false`                        | Print the full prompt sent to the API              |

### Provider resolution order

1. **Custom endpoint** — if `failureanalyzer.endpoint` is set
2. **GitHub Copilot** — if `GITHUB_TOKEN` is set (exchanges PAT for a short-lived session token)
3. **GitHub Models** — if `GITHUB_TOKEN` is set and Copilot token exchange fails
4. **OpenAI** — if `OPENAI_API_KEY` is set

---

## Example output

When a test fails, the analysis is printed to the console:

```
╔══════════════════════════════════════════════════════════════╗
║          🤖  GitHub Copilot — Failure Analysis              ║
╠══════════════════════════════════════════════════════════════╣
║  Test: stringReverseShouldWork                               ║
╠══════════════════════════════════════════════════════════════╣
  ## Root Cause
  The reverseString helper uses chars.length - 2 instead of
  chars.length - 1 as the initial right pointer, causing an
  off-by-one error that leaves the first and last characters
  in the wrong positions.

  ## Suggested Fix
  Change:  int right = chars.length - 2;
  To:      int right = chars.length - 1;
╚══════════════════════════════════════════════════════════════╝
```

---

## Saving analysis to files

Enable file output to archive analyses as CI artifacts:

```bash
mvn test -Dfailureanalyzer.save.to.file=true -Dfailureanalyzer.output.dir=target/failure-analysis
```

Files are written as `<TestName>_<timestamp>.txt` under the configured output directory.

---

## Project structure

```
src/
├── main/java/io/github/failureanalyzer/
│   ├── FailureAnalyzerExtension.java   # JUnit 5 TestWatcher + BeforeEachCallback
│   ├── ai/
│   │   └── CopilotClient.java          # HTTP client for the Copilot / OpenAI API
│   ├── config/
│   │   └── AnalyzerConfig.java         # Configuration resolution (env / sys props)
│   ├── model/
│   │   ├── AnalysisResult.java         # Holds the AI response and display logic
│   │   └── FailureContext.java         # Captures exception, stack trace, source code
│   └── source/
│       └── SourceCodeLoader.java       # Locates .java source files on disk
└── test/java/io/github/failureanalyzer/
    └── SampleFailingTest.java          # Demo tests that showcase the extension
```

---

## Dependencies

| Dependency             | Version | Scope    | Purpose                             |
| ---------------------- | ------- | -------- | ----------------------------------- |
| `junit-jupiter-api`    | 5.10.2  | provided | JUnit 5 extension API               |
| `okhttp`               | 4.12.0  | compile  | HTTP client for API calls           |
| `jackson-databind`     | 2.17.1  | compile  | JSON serialization / deserialization |
| `slf4j-api`            | 2.0.13  | compile  | Logging API (bring your own impl)   |
| `logback-classic`      | 1.5.6   | test     | Logging implementation (tests only) |

JUnit 5 is declared `provided` — the library works with whatever version your project supplies.

---

## Building from source

```bash
git clone https://github.com/jrpally/ai-powered-agent-failure-analysis-java.git
cd ai-powered-agent-failure-analysis-java
mvn clean install -DskipTests
```

Requires **Java 25** and **Maven 3.9+**.

---

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
