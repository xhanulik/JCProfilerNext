// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling.similaritySearch.cotemplatefinder;

import jcprofiler.profiling.similaritySearch.models.Trace;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RepeatingPatternFinder}.
 */
class RepeatingPatternFinderTest {
    private static final int PATTERN_WIDTH = 40;
    private static final int K = 4;

    private static double[] pattern() {
        double[] pattern = new double[PATTERN_WIDTH];
        for (int i = 0; i < PATTERN_WIDTH; i++) {
            pattern[i] = Math.sin(2 * Math.PI * i / PATTERN_WIDTH);
        }
        return pattern;
    }

    private static Trace calibrationTraceWithKRepeats(double[] pattern, int k) {
        Random random = new Random(42);
        int dataCount = pattern.length * k;
        double[] voltage = new double[dataCount];
        double[] time = new double[dataCount];
        for (int i = 0; i < dataCount; i++) {
            voltage[i] = pattern[i % pattern.length] + 0.01 * random.nextGaussian();
            time[i] = i;
        }
        return new Trace("V", "s", dataCount, voltage, time);
    }

    @Test
    void extractOneOccurrenceFindsExpectedWidth() throws InterruptedException {
        double[] pattern = pattern();
        Trace calibrationTrace = calibrationTraceWithKRepeats(pattern, K);

        Trace occurrence = RepeatingPatternFinder.extractOneOccurrence(calibrationTrace, K);

        assertTrue(Math.abs(occurrence.getDataCount() - PATTERN_WIDTH) <= PATTERN_WIDTH * 0.1,
                "expected width close to " + PATTERN_WIDTH + " but was " + occurrence.getDataCount());
    }

    @Test
    void extractOneOccurrenceMatchesTheKnownPattern() throws InterruptedException {
        double[] pattern = pattern();
        Trace calibrationTrace = calibrationTraceWithKRepeats(pattern, K);

        Trace occurrence = RepeatingPatternFinder.extractOneOccurrence(calibrationTrace, K);

        double[] reference = java.util.Arrays.copyOf(pattern, Math.min(pattern.length, occurrence.getDataCount()));
        double[] extracted = java.util.Arrays.copyOf(occurrence.getVoltage(), reference.length);

        double correlation = new PearsonsCorrelation().correlation(reference, extracted);
        assertTrue(correlation > 0.85, "expected strong correlation with the known pattern, was " + correlation);
    }

    @Test
    void rejectsNonPositiveK() {
        Trace calibrationTrace = calibrationTraceWithKRepeats(pattern(), K);
        assertEquals(IllegalArgumentException.class,
                assertThrowsAny(() -> RepeatingPatternFinder.extractOneOccurrence(calibrationTrace, 0)).getClass());
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static Exception assertThrowsAny(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            return e;
        }
        throw new AssertionError("Expected an exception to be thrown");
    }
}
