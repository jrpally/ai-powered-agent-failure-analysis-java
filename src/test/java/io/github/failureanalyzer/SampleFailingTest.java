package io.github.failureanalyzer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstration test class showing the FailureAnalyzerExtension in action.
 *
 * <p>Each failing test will trigger an AI-powered analysis printed to the console.
 *
 * <p><strong>Setup:</strong> set the {@code GITHUB_TOKEN} environment variable to
 * a GitHub Personal Access Token that has GitHub Copilot or GitHub Models access.
 */
@ExtendWith(FailureAnalyzerExtension.class)
class SampleFailingTest {

    // -------------------------------------------------------------------------
    // Failing tests — used to demonstrate the AI analyzer
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Sum of list elements should equal expected value")
    void sumShouldMatchExpected() {
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
        int actual = numbers.stream().mapToInt(Integer::intValue).sum();

        // Intentionally wrong expected value to trigger failure + AI analysis
        assertEquals(20, actual, "Sum of [1,2,3,4,5] should be 20");
    }

    @Test
    @DisplayName("String reverse should produce correct result")
    void stringReverseShouldWork() {
        String input = "hello";
        String reversed = reverseString(input);

        // Bug: reverseString has an off-by-one error
        assertEquals("olleh", reversed, "Reversed 'hello' should be 'olleh'");
    }

    @Test
    @DisplayName("Null check should not throw NullPointerException")
    void nullCheckShouldNotThrow() {
        String value = null;

        // This will throw NullPointerException — the AI will explain why
        int length = value.length();
        assertTrue(length >= 0);
    }

    @Test
    @DisplayName("List index access should be within bounds")
    void listAccessShouldBeInBounds() {
        List<String> items = Arrays.asList("apple", "banana");

        // IndexOutOfBoundsException — the AI will identify the root cause
        String third = items.get(5);
        assertNotNull(third);
    }

    // -------------------------------------------------------------------------
    // Passing test — confirms the extension does not interfere with passing tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Addition should work correctly")
    void additionShouldWork() {
        assertEquals(4, 2 + 2, "Basic addition should work");
    }

    // -------------------------------------------------------------------------
    // Helper methods with intentional bugs for demonstration
    // -------------------------------------------------------------------------

    /**
     * Reverses a string. Contains an intentional off-by-one bug.
     */
    private String reverseString(String input) {
        if (input == null || input.isEmpty()) return input;
        char[] chars = input.toCharArray();
        int left = 0;
        int right = chars.length - 2; // Bug: should be chars.length - 1

        while (left < right) {
            char temp = chars[left];
            chars[left] = chars[right];
            chars[right] = temp;
            left++;
            right--;
        }
        return new String(chars);
    }
}
