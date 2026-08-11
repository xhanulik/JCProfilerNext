// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling.similaritySearch.cotemplatefinder;

import jcprofiler.profiling.similaritySearch.Similarity;
import jcprofiler.profiling.similaritySearch.SimilaritySearchController;
import jcprofiler.profiling.similaritySearch.models.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

/**
 * Given a calibration trace known to contain {@code k} occurrences of one delimiter operation
 * (scattered anywhere in the trace, not necessarily contiguous, evenly spaced, or separated by
 * idle time), finds and extracts one real, raw occurrence of that operation.
 * <p>
 * Neither of two simpler approaches tried first held up against real captures:
 * <ul>
 *     <li>the CO Template Finder's mask/width-correlation search
 *     ({@link MaskTemplateSearch}/{@link CorrelationComputer}, kept elsewhere in this package)
 *     assumes the {@code k} repeats are packed back-to-back with nothing else in the trace -
 *     real captures are typically mostly dead time before/after the actual activity, and a flat
 *     stretch trivially "self-correlates" well (every window of it looks alike simply because
 *     there's no signal there), so the search can lock onto idle time instead of the delimiter;</li>
 *     <li>segmenting the trace into activity "bursts" by local variance and grouping them by
 *     duration assumes idle time actually separates individual delimiter occurrences - in
 *     practice the code between occurrences can be just as continuously active as the delimiter
 *     itself, so the whole active region merges into one burst instead of {@code k} of them.</li>
 * </ul>
 * Both approaches also share a deeper problem: correlation/self-similarity alone cannot tell the
 * delimiter apart from any other structure that also happens to repeat, e.g. the very operation
 * being profiled - the same input is measured repeatedly, so the code between delimiter
 * occurrences can look similar from one repeat to the next too.
 * <p>
 * This instead reuses {@link SimilaritySearchController#searchTraceForOperation}, the same
 * Manhattan-distance top-{@code k} matcher {@code SpaTimeProfiler.extractTimes} already relies
 * on to locate a *known* delimiter template later - which is already proven to correctly find
 * all {@code k} real occurrences once given the right template. The problem this class solves is
 * only where that template comes from in the first place:
 * <ol>
 *     <li>sweep a wide, geometrically-spaced range of candidate widths, since neither the
 *     delimiter's duration nor its electrical shape is known in advance;</li>
 *     <li>for each candidate width, cheaply pick one plausible occurrence: the single window of
 *     that width with the highest variance anywhere in the trace (a real operation should stand
 *     out from its surroundings somewhat, even if not enough to cleanly separate every
 *     occurrence via a hard threshold as burst segmentation needed);</li>
 *     <li>use that window as a candidate template and run the proven matcher to find its best
 *     {@code k} matches. The quality of that result is what actually distinguishes the correct
 *     width, unlike a raw correlation score: at the true delimiter width, all {@code k} matches
 *     should be genuinely similar (since {@code k} real occurrences exist); at, say, the width of
 *     the delimiter plus the following profiled code, there are only as many true matches as
 *     there are trap boundaries - far fewer than {@code k} - so the matcher is forced to include
 *     much poorer matches to fill the requested count, and that shows up directly as a much
 *     worse worst-of-{@code k} distance;</li>
 *     <li>among candidate widths whose worst-of-{@code k} distance (normalized per sample, so
 *     widths are comparable) is close to the best found, prefer the smallest - a delimiter is
 *     presumably a compact, deliberately-chosen marker, not the entire operation under test.</li>
 * </ol>
 *
 * @author Veronika Hanulikova
 */
public class RepeatingPatternFinder {
    private static final Logger log = LoggerFactory.getLogger(RepeatingPatternFinder.class);

    private static final int MIN_WIDTH = 10;
    // Candidate widths are swept from MIN_WIDTH up to half the trace length, so there's room for
    // at least two non-overlapping occurrences of the largest candidate.
    private static final double WIDTH_GROWTH_FACTOR = 1.4;

    // A candidate's normalized worst-of-k distance must be at least this much better than its
    // background (random-pair) distance to be considered a match at all, regardless of width.
    private static final double MAX_ACCEPTABLE_SCORE = 0.5;

    // Number of non-overlapping local variance maxima tried as candidate positions per width.
    private static final int POSITIONS_PER_WIDTH = 3;

    // To confirm a candidate is distinctly k-occurring (not just short/generic enough to match
    // many more places than that), this many matches beyond k are also requested, and the
    // (k + CLIFF_EXTRA_MATCHES)-th distance must be at least CLIFF_RATIO_THRESHOLD times the
    // k-th - i.e. there must be a clear quality drop-off right after the k good matches run out.
    private static final int CLIFF_EXTRA_MATCHES = 5;
    private static final double CLIFF_RATIO_THRESHOLD = 3.0;

    private static class Candidate {
        final int width;
        final int position;
        final double normalizedScore;

        Candidate(int width, int position, double normalizedScore) {
            this.width = width;
            this.position = position;
            this.normalizedScore = normalizedScore;
        }
    }

    /**
     * Extracts one real, raw occurrence of the delimiter operation that occurs {@code k} times
     * somewhere inside {@code calibrationTrace}.
     *
     * @param calibrationTrace trace containing {@code k} occurrences of one delimiter operation
     * @param k                number of delimiter occurrences in {@code calibrationTrace}
     * @return a new {@link Trace} holding the raw samples of one occurrence
     */
    public static Trace extractOneOccurrence(Trace calibrationTrace, int k) throws InterruptedException {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive, was " + k);
        }
        if (calibrationTrace.getDataCount() < k) {
            throw new IllegalArgumentException("Calibration trace is too short to contain " + k + " occurrences");
        }

        int n = calibrationTrace.getDataCount();
        double[] voltage = calibrationTrace.getVoltage();
        double[] prefixSum = new double[n + 1];
        double[] prefixSumSq = new double[n + 1];
        for (int i = 0; i < n; i++) {
            prefixSum[i + 1] = prefixSum[i] + voltage[i];
            prefixSumSq[i + 1] = prefixSumSq[i] + voltage[i] * voltage[i];
        }

        List<Integer> widths = geometricWidths(n);
        log.info("Testing {} candidate widths from {} to {} samples", widths.size(), widths.get(0), widths.get(widths.size() - 1));

        List<Candidate> candidates = new ArrayList<>();
        for (int width : widths) {
            for (int position : localMaximumVariancePositions(prefixSum, prefixSumSq, n, width, POSITIONS_PER_WIDTH)) {
                Trace candidateTemplate = sliceOccurrence(calibrationTrace, new IntRange(position, position + width));

                SortedSet<Similarity> matches;
                try {
                    matches = SimilaritySearchController.searchTraceForOperation(
                            calibrationTrace, candidateTemplate, SimilaritySearchController.MANHATTAN_DISTANCE_ALGORITHM, k + CLIFF_EXTRA_MATCHES);
                } catch (RuntimeException e) {
                    log.debug("Width {} at {}: candidate search failed ({}), skipping", width, position, e.getMessage());
                    continue;
                }

                if (matches.size() < k) {
                    log.debug("Width {} at {}: only found {} of {} matches, skipping", width, position, matches.size(), k);
                    continue;
                }

                List<Similarity> sortedMatches = new ArrayList<>(matches);
                double kthDistance = sortedMatches.get(k - 1).getDistance();

                // If there really are only k genuine occurrences, asking for a few more than k
                // should turn up noticeably worse matches past the k-th - a "cliff". Without that
                // cliff, this candidate isn't distinctly k-occurring: either it's short/generic
                // enough to also match plenty of unrelated places (undercounting the true
                // duration), or it doesn't correspond to a real repeating operation at all.
                if (sortedMatches.size() >= k + CLIFF_EXTRA_MATCHES) {
                    double extraDistance = sortedMatches.get(k - 1 + CLIFF_EXTRA_MATCHES).getDistance();
                    double cliffRatio = kthDistance <= 0 ? Double.POSITIVE_INFINITY : extraDistance / kthDistance;
                    if (cliffRatio < CLIFF_RATIO_THRESHOLD) {
                        log.debug("Width {} at {}: no quality cliff after match {} (ratio {}), skipping",
                                width, position, k, cliffRatio);
                        continue;
                    }
                }

                double background = backgroundDistance(voltage, width, position);
                if (background <= 0) {
                    continue;
                }
                double normalizedScore = kthDistance / background;
                log.debug("Width {} at {}: worst-of-{} distance {}, background distance {}, normalized score {}",
                        width, position, k, kthDistance, background, normalizedScore);
                candidates.add(new Candidate(width, position, normalizedScore));
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No candidate width found " + k + " sufficiently similar occurrences in the calibration trace");
        }

        // Any sub-window of a genuinely k-occurring operation is itself contained in all k
        // occurrences, so it trivially also passes the cliff check above - a single short,
        // distinctive edge inside the delimiter can look just as cleanly "k-occurring" as the
        // whole thing. That means the smallest passing candidate is not a safe choice; the
        // largest one that still passes is, since growing past the delimiter's real extent (into
        // whatever comes next, which differs from occurrence to occurrence) is exactly what
        // should eventually break the cliff.
        Candidate chosen = candidates.stream()
                .filter(c -> c.normalizedScore <= MAX_ACCEPTABLE_SCORE)
                .max(Comparator.<Candidate>comparingInt(c -> c.width).thenComparing(c -> -c.normalizedScore))
                .orElseThrow(() -> new IllegalStateException(
                        "No candidate width found " + k + " occurrences with a convincing enough match quality"));

        log.info("Chosen occurrence: width {} at position {} (normalized score {})",
                chosen.width, chosen.position, chosen.normalizedScore);
        return sliceOccurrence(calibrationTrace, new IntRange(chosen.position, chosen.position + chosen.width));
    }

    // Number of unrelated windows sampled to estimate the "typical" (background) distance for a
    // given width - i.e. how similar two random windows of that width tend to be anyway. Without
    // this, raw Manhattan distance shrinks with width regardless of whether a match means
    // anything: a tiny window can look deceptively "well matched" almost everywhere simply
    // because there's very little data for two windows to differ over, not because it captured
    // anything real. Scoring against this per-width background instead of the raw distance is
    // what makes scores comparable across widths.
    private static final int BACKGROUND_SAMPLE_COUNT = 30;

    private static double backgroundDistance(double[] voltage, int width, int excludePosition) {
        double[] template = Arrays.copyOfRange(voltage, excludePosition, excludePosition + width);
        int n = voltage.length;
        int lastStart = n - width;
        if (lastStart <= 0) {
            return Double.NaN;
        }
        int step = Math.max(1, lastStart / BACKGROUND_SAMPLE_COUNT);

        List<Double> distances = new ArrayList<>();
        for (int pos = 0; pos <= lastStart; pos += step) {
            if (Math.abs(pos - excludePosition) < width) {
                continue;
            }
            distances.add(SimilaritySearchController.MANHATTAN_DISTANCE_ALGORITHM.compute(template, voltage, pos));
        }
        if (distances.isEmpty()) {
            return Double.NaN;
        }
        Collections.sort(distances);
        return distances.get(distances.size() / 2);
    }

    private static List<Integer> geometricWidths(int dataCount) {
        int maxWidth = Math.max(MIN_WIDTH + 1, dataCount / 2);
        List<Integer> widths = new ArrayList<>();
        double width = MIN_WIDTH;
        while ((int) width <= maxWidth) {
            widths.add((int) width);
            width *= WIDTH_GROWTH_FACTOR;
        }
        return widths;
    }

    /**
     * Finds up to {@code count} non-overlapping windows of the given width with the highest
     * variance, using prefix sums so computing variance at every position only costs one O(n)
     * pass. The single highest-variance window alone is not reliable: a one-off event (like the
     * trace's own idle-to-active transition) can have higher variance than any individual,
     * genuinely-repeating occurrence, especially at larger widths where it dominates the window.
     * Picking several non-overlapping peaks instead means one such one-off event can knock out at
     * most one of them, leaving the others free to land on real occurrences.
     */
    private static List<Integer> localMaximumVariancePositions(double[] prefixSum, double[] prefixSumSq, int n, int width, int count) {
        int lastStart = n - width;
        if (lastStart < 0) {
            return List.of();
        }

        double[] variances = new double[lastStart + 1];
        for (int start = 0; start <= lastStart; start++) {
            double sum = prefixSum[start + width] - prefixSum[start];
            double sumSq = prefixSumSq[start + width] - prefixSumSq[start];
            double mean = sum / width;
            variances[start] = sumSq / width - mean * mean;
        }

        boolean[] excluded = new boolean[lastStart + 1];
        List<Integer> positions = new ArrayList<>();
        for (int pick = 0; pick < count; pick++) {
            int bestStart = -1;
            double bestVariance = Double.NEGATIVE_INFINITY;
            for (int start = 0; start <= lastStart; start++) {
                if (!excluded[start] && variances[start] > bestVariance) {
                    bestVariance = variances[start];
                    bestStart = start;
                }
            }
            if (bestStart == -1) {
                break;
            }
            positions.add(bestStart);
            int exclusionFrom = Math.max(0, bestStart - width);
            int exclusionTo = Math.min(lastStart, bestStart + width);
            Arrays.fill(excluded, exclusionFrom, exclusionTo + 1, true);
        }
        return positions;
    }

    private static Trace sliceOccurrence(Trace trace, IntRange occurrence) {
        double[] voltage = Arrays.copyOfRange(trace.getVoltage(), occurrence.getFirstIndex(), occurrence.getLastIndex());
        double[] time = Arrays.copyOfRange(trace.getTime(), occurrence.getFirstIndex(), occurrence.getLastIndex());

        double voltageMaximum = Double.NEGATIVE_INFINITY;
        double voltageMinimum = Double.POSITIVE_INFINITY;
        for (double v : voltage) {
            if (v > voltageMaximum) voltageMaximum = v;
            if (v < voltageMinimum) voltageMinimum = v;
        }

        return new Trace(trace.getVoltageUnit(), trace.getTimeUnit(), voltage.length, voltage, time, voltageMaximum, voltageMinimum);
    }
}
