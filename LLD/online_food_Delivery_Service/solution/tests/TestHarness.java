package LLD.online_food_Delivery_Service.solution.tests;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A dependency-free test runner, in the same spirit as the meetingRoomScheduler
 * suite: this repository has no build system and no JUnit, so tests compile and
 * run with plain javac + java.
 *
 * Each test prints as a numbered, narrated scenario and ends in PASS or FAIL.
 * {@link #finish()} prints a summary and exits non-zero if anything failed, so
 * a suite can gate a script or CI step.
 */
public final class TestHarness {

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final int WIDTH = 78;

    private final String suiteName;
    private final List<String> failures = new ArrayList<>();
    private int passed = 0;
    private int counter = 0;

    public TestHarness(String suiteName, String description) {
        this.suiteName = suiteName;
        System.out.println("=".repeat(WIDTH));
        System.out.println("  " + suiteName);
        System.out.println("=".repeat(WIDTH));
        System.out.println("  " + description);
    }

    public void scenario(String title, String proves, ThrowingRunnable body) {
        int n = ++counter;
        System.out.println();
        System.out.println("---------- " + n + ". " + title + " ----------");
        System.out.println("What it proves : " + proves);
        try {
            body.run();
            passed++;
            System.out.println("Result: ✅ PASS");
        } catch (Throwable t) {
            String msg = t.getMessage() == null ? t.toString() : t.getMessage();
            failures.add(n + ". " + title + " — " + msg);
            System.out.println("Result: ❌ FAIL — " + msg);
        }
    }

    public void finish() {
        System.out.println();
        System.out.println("=".repeat(WIDTH));
        System.out.printf("  %s: %d passed, %d failed, %d total%n",
                suiteName, passed, failures.size(), counter);
        failures.forEach(f -> System.out.println("  ❌ " + f));
        System.out.println("=".repeat(WIDTH));
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    // --- assertions ---

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void checkEquals(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
        System.out.println("  ✔ " + what + " = " + actual);
    }

    public static <T extends Throwable> T checkThrows(Class<T> type, ThrowingRunnable body, String what) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                System.out.println("  ✔ " + what + " → " + type.getSimpleName() + ": " + t.getMessage());
                return type.cast(t);
            }
            throw new AssertionError(what + ": expected " + type.getSimpleName() + " but got " + t);
        }
        throw new AssertionError(what + ": expected " + type.getSimpleName() + " but nothing was thrown");
    }

    public static void note(String line) {
        System.out.println("  " + line);
    }
}
