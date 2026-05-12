# Stop Guessing Why Your Tests Fail — Let GitHub Copilot Tell You

*How a 10-line annotation turns every JUnit failure into an AI-powered diagnosis*

---

You've been there. A test goes red in CI at 2 AM. The stack trace points to line 47 of a file you didn't write. The assertion message says `expected: <true> but was: <false>`. Congratulations — you now own a mystery.

What if the moment a test failed, an AI expert appeared in your console and said: "Here's the root cause, here's why it happened, and here's the exact fix"?

That's what **Java Failure Analyzer** does.

---

## The idea in one sentence

Java Failure Analyzer is a JUnit 5 extension that intercepts test failures and automatically sends the failure context — exception, stack trace, and the actual source code — to GitHub Copilot, then prints a structured diagnosis back to your console.

No plugins. No dashboards. No configuration files to write. One annotation.

---

## How it works

JUnit 5 has a little-known extension point called `TestWatcher`. It fires a callback every time a test fails, passes, or is aborted. Java Failure Analyzer hooks into that callback:

```
Test fails
    → JUnit calls testFailed(context, throwable)
        → Extension captures failure context
            → Sends to GitHub Copilot API
                → Prints structured analysis to console
```

The "failure context" sent to the AI includes:

- The exception type and message
- The full stack trace (filtered to remove JUnit internals)
- The **test source code** (loaded from `src/test/java` at runtime)
- The **production source code** of the class being tested (inferred automatically)

That last two points are the key. The model doesn't just see a stack trace — it sees the *actual code that caused the problem*, the same way a human reviewer would.

---

## The output

When a test fails, this appears in your console:

```
╔══════════════════════════════════════════════════════════════╗
║          🤖  GitHub Copilot — Failure Analysis              ║
╠══════════════════════════════════════════════════════════════╣
║  Test: stringReverseShouldWork                               ║
╠══════════════════════════════════════════════════════════════╣

  ## Root Cause
  The `reverseString` helper initializes the right pointer to
  `chars.length - 2` instead of `chars.length - 1`, leaving the
  first and last characters unswapped.

  ## Analysis
  For the input "hello" (length 5), the right pointer starts at
  index 3 instead of 4. The swap loop therefore never touches
  index 4 ('o'), producing "lleho" instead of "olleh".

  ## Suggested Fix
  Change:
    int right = chars.length - 2;
  To:
    int right = chars.length - 1;

  ## Prevention
  Add a boundary test with a single-character string and a
  two-character string to cover the edge cases of the swap loop.

╚══════════════════════════════════════════════════════════════╝
```

Root cause. Analysis. Suggested fix. Prevention. In one shot, before you've even opened the file.

---

## Zero-friction setup

### Option A — Annotate one class

```java
@ExtendWith(FailureAnalyzerExtension.class)
class PaymentServiceTest {
    // All failures in this class get analyzed automatically
}
```

### Option B — Activate for every test in the project

Add one line to `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

The JAR ships a `META-INF/services` file that registers the extension via Java's service loader. That single property switches it on globally — no annotation on any test class.

Then set your token and run:

```bash
export GITHUB_TOKEN=ghp_yourToken
mvn test
```

---

## Provider flexibility

The extension isn't locked to one AI backend. It resolves providers in this order:

1. **Custom endpoint** — point it at any OpenAI-compatible API
2. **GitHub Copilot** — exchanges your PAT for a short-lived session token automatically
3. **GitHub Models** — uses your PAT directly, simpler setup
4. **OpenAI** — drop in an `OPENAI_API_KEY` and it just works

Switching providers is a single environment variable change, no code changes required.

---

## Configuration without ceremony

Every option is a system property or environment variable — nothing to commit to your repository:

| What you want | How to set it |
|---|---|
| Use a specific model | `-Dfailureanalyzer.model=gpt-4.1` |
| Save analyses to files | `-Dfailureanalyzer.save.to.file=true` |
| See the full prompt sent to the API | `-Dfailureanalyzer.verbose=true` |
| Disable source code inclusion | `-Dfailureanalyzer.include.source=false` |
| Point at a self-hosted LLM | `-Dfailureanalyzer.endpoint=http://localhost:11434/v1/chat/completions` |

When `save.to.file` is enabled, each analysis is written to `target/failure-analysis/<TestName>_<timestamp>.txt` — perfect for attaching to CI job artifacts.

---

## The architecture

The project is small by design. Five classes, each with a single job:

```
FailureAnalyzerExtension   — JUnit hook, orchestrates the flow
CopilotClient              — HTTP calls to the AI provider
AnalyzerConfig             — Reads env vars and system properties
FailureContext             — Value object: exception + source code
SourceCodeLoader           — Finds .java files on disk at runtime
```

`SourceCodeLoader` is worth a closer look. When a test fails in class `com.example.PaymentServiceTest`, the loader scans `src/test/java/com/example/PaymentServiceTest.java` and also attempts to infer the production class under test (`PaymentService`) and load that source too. The AI receives the full picture without any developer involvement.

---

## Why this matters for teams

**Faster triage.** A developer opening a failing build sees the diagnosis immediately. No context-switching to open files, no mental stack walking.

**Better bug reports.** The "Suggested Fix" section gives reviewers concrete code changes to evaluate, not just symptoms.

**Knowledge transfer.** Junior developers get the same quality of diagnosis that a senior would write — every time, instantly.

**CI artifact value.** With `save.to.file=true`, your CI pipeline archives analyses alongside test reports. Post-mortem reviews become much easier.

---

## What it isn't

Java Failure Analyzer is deliberately narrow in scope. It does not:

- Replace a proper test framework or assertion library
- Fix your code automatically
- Run between test steps or intercept flaky retries
- Add latency to passing tests (the API is only called on failure)

It's a read-only observer that speaks up exactly when you need it.

---

## Getting started in 5 minutes

1. Add the dependency (Java 25, Maven 3.9+):

```xml
<dependency>
    <groupId>io.github.failureanalyzer</groupId>
    <artifactId>java-failure-analyzer</artifactId>
    <version>1.0.0</version>
    <scope>test</scope>
</dependency>
```

2. Enable auto-registration in `src/test/resources/junit-platform.properties`:

```properties
junit.jupiter.extensions.autodetection.enabled=true
```

3. Set your GitHub token:

```bash
export GITHUB_TOKEN=ghp_yourPersonalAccessToken
```

4. Run your tests. Break something intentionally. Read the diagnosis.

---

The best debugging tool is the one that runs automatically. Java Failure Analyzer turns every red test into a teaching moment — and the teacher never sleeps.

---

*Source code: [github.com/failureanalyzer/java-failure-analyzer](https://github.com/failureanalyzer/java-failure-analyzer)*
