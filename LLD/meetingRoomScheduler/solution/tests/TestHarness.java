package LLD.meetingRoomScheduler.solution.tests;

import java.util.ArrayList;
import java.util.List;

/**
 * A dependency-free test harness.
 *
 * This repository has no build system and no JUnit on the classpath, so rather than
 * introduce Maven/Gradle for one test class, the suite runs with plain
 * `javac` + `java` exactly like MeetingSchedulerDemo does.
 *
 * The harness understands three outcomes, not two:
 *
 *   PASS    the guarantee holds
 *   FAIL    the guarantee is broken — a real regression
 *   XFAIL   an *expected* failure: a known defect documented in
 *           concurrency-and-thread-safety.md that has not been fixed yet
 *
 * XFAIL matters. A concurrency suite that simply omits the tests for known bugs
 * teaches you nothing; one that fails the build on them can never be committed.
 * Marking them expected-to-fail keeps the defect visible on every run and turns
 * the day it starts passing into a signal ("XPASS") rather than silence.
 */
public final class TestHarness {

    public enum Outcome { PASS, FAIL, XFAIL, XPASS }

    public static final class Result {
        final String name;
        final Outcome outcome;
        final String detail;

        Result(String name, Outcome outcome, String detail) {
            this.name = name;
            this.outcome = outcome;
            this.detail = detail;
        }
    }

    private final List<Result> results = new ArrayList<>();
    private final String suiteName;

    public TestHarness(String suiteName) {
        this.suiteName = suiteName;
    }

    /** A test that must pass. */
    public void test(String name, ThrowingRunnable body) {
        run(name, body, false);
    }

    /**
     * A test asserting behaviour the code does NOT yet have.
     * Failing is the expected result and does not fail the suite;
     * passing is reported loudly as XPASS so the marker can be removed.
     */
    public void knownDefect(String name, ThrowingRunnable body) {
        run(name, body, true);
    }

    private void run(String name, ThrowingRunnable body, boolean expectedToFail) {
        try {
            body.run();
            results.add(new Result(name,
                    expectedToFail ? Outcome.XPASS : Outcome.PASS,
                    expectedToFail ? "expected to fail but PASSED — has this been fixed?" : ""));
        } catch (AssertionError | Exception e) {
            results.add(new Result(name,
                    expectedToFail ? Outcome.XFAIL : Outcome.FAIL,
                    e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    // ---- assertions -------------------------------------------------------

    public static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " — expected " + expected + " but was " + actual);
        }
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    // ---- reporting --------------------------------------------------------

    /** @return process exit code: 0 when nothing unexpected happened. */
    public int report() {
        System.out.println();
        System.out.println("=".repeat(78));
        System.out.println("  " + suiteName);
        System.out.println("=".repeat(78));

        int pass = 0, fail = 0, xfail = 0, xpass = 0;
        for (Result r : results) {
            String tag;
            switch (r.outcome) {
                case PASS:  tag = "  PASS "; pass++;  break;
                case FAIL:  tag = "  FAIL "; fail++;  break;
                case XFAIL: tag = " XFAIL "; xfail++; break;
                default:    tag = " XPASS "; xpass++; break;
            }
            System.out.println("[" + tag + "] " + r.name);
            if (!r.detail.isEmpty()) {
                System.out.println("           └─ " + r.detail);
            }
        }

        System.out.println("-".repeat(78));
        System.out.printf("  %d passed, %d failed, %d known defects (expected failures), %d unexpected passes%n",
                pass, fail, xfail, xpass);

        if (xfail > 0) {
            System.out.println();
            System.out.println("  NOTE: XFAIL entries are documented defects, not test bugs.");
            System.out.println("        See solution/concurrency-and-thread-safety.md sections 8 and 9.");
        }
        if (xpass > 0) {
            System.out.println();
            System.out.println("  ACTION: an XPASS means a known defect now passes. Verify the fix,");
            System.out.println("          then promote the test from knownDefect() to test().");
        }
        System.out.println("=".repeat(78));

        return (fail == 0) ? 0 : 1;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
