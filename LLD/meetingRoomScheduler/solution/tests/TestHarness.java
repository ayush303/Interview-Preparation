package LLD.meetingRoomScheduler.solution.tests;

import java.util.ArrayList;
import java.util.List;

/**
 * A dependency-free scenario runner.
 *
 * This repository has no build system and no JUnit on the classpath, so rather than
 * introduce Maven/Gradle for one test class, the suite runs with plain
 * `javac` + `java` exactly like MeetingSchedulerDemo does.
 *
 * Each test is presented as a numbered SCENARIO in the same narrated style as
 * MeetingSchedulerDemo, so the output reads as a story rather than a list of
 * assertions:
 *
 *   ========== SCENARIO 1: Title ==========
 *   What it proves : one sentence
 *   Setup          : the fixture
 *
 *   ...live output, including real [Email] / [Calendar] observer lines...
 *
 *   Result: ✅ PASS — why
 *
 * Four outcomes, not two:
 *
 *   PASS    the guarantee holds
 *   FAIL    the guarantee is broken — a real regression
 *   XFAIL   an *expected* failure: a known defect documented in
 *           concurrency-and-thread-safety.md that has not been fixed yet
 *   XPASS   a known defect that now passes — verify and promote the test
 *
 * XFAIL matters. A concurrency suite that simply omits the tests for known bugs
 * teaches you nothing; one that fails the build on them can never be committed.
 * Marking them expected-to-fail keeps the defect visible on every run and turns
 * the day it starts passing into a signal rather than silence.
 */
public final class TestHarness {

    public enum Outcome { PASS, FAIL, XFAIL, XPASS }

    public static final class Result {
        final int number;
        final String name;
        final Outcome outcome;
        final String detail;

        Result(int number, String name, Outcome outcome, String detail) {
            this.number = number;
            this.name = name;
            this.outcome = outcome;
            this.detail = detail;
        }
    }

    private static final int WIDTH = 78;

    private final List<Result> results = new ArrayList<>();
    private final String suiteName;
    private int counter = 0;

    public TestHarness(String suiteName) {
        this.suiteName = suiteName;
        System.out.println("=".repeat(WIDTH));
        System.out.println("  " + suiteName);
        System.out.println("=".repeat(WIDTH));
        System.out.println("  Every scenario below runs real threads against the real");
        System.out.println("  MeetingScheduler singleton. Nothing is mocked.");
    }

    /** A scenario that must pass. */
    public void scenario(String title, String proves, String setup, ThrowingRunnable body) {
        run(title, proves, setup, body, false);
    }

    /**
     * A scenario asserting behaviour the code does NOT yet have.
     * Failing is the expected result and does not fail the suite;
     * passing is reported loudly as XPASS so the marker can be removed.
     */
    public void knownDefect(String title, String proves, String setup, ThrowingRunnable body) {
        run(title, proves, setup, body, true);
    }

    private void run(String title, String proves, String setup,
                     ThrowingRunnable body, boolean expectedToFail) {
        int n = ++counter;
        System.out.println();
        System.out.println("========== SCENARIO " + n + ": " + title + " ==========");
        if (expectedToFail) {
            System.out.println("STATUS         : ⚠️  KNOWN DEFECT — this scenario is expected to FAIL");
        }
        System.out.println("What it proves : " + proves);
        System.out.println("Setup          : " + setup);
        System.out.println();

        try {
            body.run();
            System.out.println();
            if (expectedToFail) {
                System.out.println("Result: ⚠️  XPASS — expected this to fail, but it PASSED.");
                System.out.println("        Has the defect been fixed? Verify, then promote this");
                System.out.println("        scenario from knownDefect(...) to scenario(...).");
                results.add(new Result(n, title, Outcome.XPASS, "expected failure but passed"));
            } else {
                System.out.println("Result: ✅ PASS");
                results.add(new Result(n, title, Outcome.PASS, ""));
            }
        } catch (AssertionError | Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            System.out.println();
            if (expectedToFail) {
                System.out.println("Result: ❌ XFAIL (expected) — " + msg);
                results.add(new Result(n, title, Outcome.XFAIL, msg));
            } else {
                System.out.println("Result: ❌ FAIL — " + msg);
                results.add(new Result(n, title, Outcome.FAIL, msg));
            }
        }
    }

    // ---- output helpers used by the scenarios ----------------------------

    /** An indented narration line inside a scenario. */
    public static void say(String line) {
        System.out.println("  " + line);
    }

    public static void say(String format, Object... args) {
        System.out.println("  " + String.format(format, args));
    }

    /** A labelled fact, aligned so a block of them lines up. */
    public static void fact(String label, Object value) {
        System.out.printf("  %-28s : %s%n", label, value);
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
        System.out.println("=".repeat(WIDTH));
        System.out.println("  SUMMARY");
        System.out.println("=".repeat(WIDTH));

        int pass = 0, fail = 0, xfail = 0, xpass = 0;
        for (Result r : results) {
            String tag;
            switch (r.outcome) {
                case PASS:  tag = "  PASS "; pass++;  break;
                case FAIL:  tag = "  FAIL "; fail++;  break;
                case XFAIL: tag = " XFAIL "; xfail++; break;
                default:    tag = " XPASS "; xpass++; break;
            }
            System.out.printf("  [%s] Scenario %-2d  %s%n", tag, r.number, r.name);
            if (!r.detail.isEmpty()) {
                System.out.println("            └─ " + r.detail);
            }
        }

        System.out.println("-".repeat(WIDTH));
        System.out.printf("  %d passed, %d failed, %d known defects (expected failures), "
                + "%d unexpected passes%n", pass, fail, xfail, xpass);

        if (xfail > 0) {
            System.out.println();
            System.out.println("  NOTE: XFAIL entries are documented defects, not test bugs.");
            System.out.println("        See solution/concurrency-and-thread-safety.md §8 and §9.");
        }
        if (xpass > 0) {
            System.out.println();
            System.out.println("  ACTION: an XPASS means a known defect now passes. Verify the fix,");
            System.out.println("          then promote the scenario from knownDefect() to scenario().");
        }
        System.out.println("=".repeat(WIDTH));

        return (fail == 0) ? 0 : 1;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
