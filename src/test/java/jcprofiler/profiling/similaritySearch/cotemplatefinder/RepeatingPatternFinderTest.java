// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling.similaritySearch.cotemplatefinder;

import jcprofiler.profiling.similaritySearch.models.Trace;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RepeatingPatternFinder}.
 */
class RepeatingPatternFinderTest {
    private static final int DELIMITER_WIDTH = 200;
    private static final int DECOY_WIDTH = 350;
    private static final int K = 12;
    private static final int DECOY_COUNT = 5;
    private static final double IDLE_LEVEL = 0.2;
    private static final double IDLE_NOISE = 0.001;

    /**
     * A deliberately asymmetric waveform (a fundamental plus a phase-shifted second harmonic) -
     * a plain single-cycle sine is symmetric under bisection (each half is the mirror of the
     * other), which lets a half-width window match just as well as the full one; this shape
     * doesn't have that ambiguity, closer to how a real, non-sinusoidal operation would behave.
     */
    private static double[] burstWaveform(int width, double amplitude, Random random) {
        double[] waveform = new double[width];
        for (int i = 0; i < width; i++) {
            double phase = 2 * Math.PI * i / width;
            waveform[i] = IDLE_LEVEL + amplitude * (Math.sin(phase) + 0.5 * Math.sin(2 * phase + 1.0)) + 0.01 * random.nextGaussian();
        }
        return waveform;
    }

    /**
     * A trace with {@code K} delimiter-shaped bursts and {@code DECOY_COUNT} differently-sized
     * decoy bursts (standing in for other, unrelated repeating code), all separated by idle gaps.
     */
    private static Trace calibrationTraceWithDelimiterAndDecoys() {
        Random random = new Random(42);
        List<Double> voltage = new ArrayList<>();

        List<Boolean> schedule = new ArrayList<>();
        for (int i = 0; i < K; i++) schedule.add(true);
        for (int i = 0; i < DECOY_COUNT; i++) schedule.add(false);
        java.util.Collections.shuffle(schedule, random);

        for (boolean isDelimiter : schedule) {
            int idleGap = 100 + random.nextInt(200);
            for (int i = 0; i < idleGap; i++) voltage.add(IDLE_LEVEL + IDLE_NOISE * random.nextGaussian());

            double[] burst = isDelimiter ? burstWaveform(DELIMITER_WIDTH, 0.2, random) : burstWaveform(DECOY_WIDTH, 0.2, random);
            for (double v : burst) voltage.add(v);
        }
        for (int i = 0; i < 150; i++) voltage.add(IDLE_LEVEL + IDLE_NOISE * random.nextGaussian());

        double[] voltageArray = voltage.stream().mapToDouble(Double::doubleValue).toArray();
        double[] time = new double[voltageArray.length];
        for (int i = 0; i < time.length; i++) time[i] = i;
        return new Trace("V", "s", voltageArray.length, voltageArray, time);
    }

    @Test
    void extractOneOccurrenceFindsExpectedWidth() throws InterruptedException {
        Trace calibrationTrace = calibrationTraceWithDelimiterAndDecoys();

        Trace occurrence = RepeatingPatternFinder.extractOneOccurrence(calibrationTrace, K);

        // real captures showed up to roughly +/-30% width error even once correctly identifying
        // the right operation (some overshoot into whatever consistently follows it is expected,
        // since that's what the cliff check alone can't fully rule out) - a wide tolerance here
        // reflects that this is inherently an approximate answer, not a search for exactness.
        assertTrue(occurrence.getDataCount() >= DELIMITER_WIDTH * 0.5 && occurrence.getDataCount() <= DELIMITER_WIDTH * 2.5,
                "expected width roughly close to " + DELIMITER_WIDTH + " but was " + occurrence.getDataCount());
    }

    @Test
    void extractOneOccurrenceMatchesTheKnownPatternNotTheDecoy() throws InterruptedException {
        Trace calibrationTrace = calibrationTraceWithDelimiterAndDecoys();

        Trace occurrence = RepeatingPatternFinder.extractOneOccurrence(calibrationTrace, K);

        // burst boundary detection has no reason to land on the exact same sample offset as a
        // freshly-generated reference waveform, so check the best correlation across a small
        // range of alignments rather than a single zero-lag comparison.
        double[] reference = burstWaveform(DELIMITER_WIDTH, 0.2, new Random(1));
        double bestCorrelation = Double.NEGATIVE_INFINITY;
        for (int shift = -DELIMITER_WIDTH; shift <= 2 * DELIMITER_WIDTH; shift++) {
            double[][] aligned = alignWithShift(reference, occurrence.getVoltage(), shift);
            if (aligned == null) continue;
            double correlation = new PearsonsCorrelation().correlation(aligned[0], aligned[1]);
            bestCorrelation = Math.max(bestCorrelation, correlation);
        }
        assertTrue(bestCorrelation > 0.7, "expected strong correlation with the known delimiter pattern at some alignment, best was " + bestCorrelation);
    }

    /**
     * Returns {reference[from..], extracted[shift-adjusted..]} trimmed to a common, overlapping
     * length for the given shift, or {@code null} if the shift leaves no overlap.
     */
    private static double[][] alignWithShift(double[] reference, double[] extracted, int shift) {
        int refFrom = Math.max(0, -shift);
        int extFrom = Math.max(0, shift);
        int len = Math.min(reference.length - refFrom, extracted.length - extFrom);
        if (len <= DELIMITER_WIDTH / 2) return null;
        return new double[][]{
                Arrays.copyOfRange(reference, refFrom, refFrom + len),
                Arrays.copyOfRange(extracted, extFrom, extFrom + len)
        };
    }

    @Test
    void rejectsNonPositiveK() {
        Trace calibrationTrace = calibrationTraceWithDelimiterAndDecoys();
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
