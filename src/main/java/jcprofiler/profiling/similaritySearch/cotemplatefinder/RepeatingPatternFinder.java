// SPDX-FileCopyrightText: 2019-2026 Martin Podhora (martinftlsx)
// SPDX-FileCopyrightText: 2025-2026 Veronika Hanulikova <xhanulik@gmail.com>
// SPDX-License-Identifier: MIT

/**
 * This file is copied from the SPA-Cryptographic-Operations-Extractor,
 * originally developed by Martin Podhora (https://github.com/crocs-muni/SPA-Cryptographic-Operations-Extractor),
 * and licensed under MIT license.
 *
 * Original code licensed under the MIT License:
 * Copyright (c) 2019 martinftlsx
 *
 * Modifications:
 * Copyright (c) 2026 Veronika Hanulikova
 *
 * Licensed under the MIT License.
 * See LICENSES/MIT.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.profiling.similaritySearch.cotemplatefinder;

import jcprofiler.profiling.similaritySearch.models.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Given a calibration trace known to contain {@code k} consecutive, back-to-back repeats of one
 * sub-operation of unknown duration, finds the best-correlated repeat width and offset (using
 * the unmodified {@link MaskTemplateSearch}/{@link CorrelationComputer} CO Template Finder
 * algorithm) and slices out one real, raw occurrence of that sub-operation.
 * <p>
 * The pieces that make this different from the original CO Template Finder module are new,
 * not part of the ported algorithm:
 * <ul>
 *     <li>the original always searched a pre-known list of candidate durations read from a JSON
 *     configuration; here only {@code k} is known, so a range of candidate sample-widths is
 *     scanned instead, derived from the calibration trace's length and {@code k};</li>
 *     <li>each candidate width is first searched with a coarse window stride scaled to that
 *     width, then the single best candidate is re-searched at full (1-sample) resolution in a
 *     small neighborhood around the coarse hit. Real calibration traces are captured with the
 *     same acquisition window as any other measurement (millions of samples), while a single
 *     repeat is typically a tiny fraction of that - searching every window position at every
 *     candidate width (as the original config-driven search did, for a handful of pre-known
 *     widths) is computationally intractable at that scale, so this coarse-to-fine search keeps
 *     the total cost roughly independent of the trace length;</li>
 *     <li>the original's own output is a de-noised average across all {@code k} occurrences
 *     ({@code createAverageTemplates}/{@code createMaskTemplate}); this returns one real,
 *     unaveraged occurrence instead.</li>
 * </ul>
 *
 * @author Veronika Hanulikova
 */
public class RepeatingPatternFinder {
    private static final Logger log = LoggerFactory.getLogger(RepeatingPatternFinder.class);

    private static final char PATTERN_CHARACTER = 'A';
    private static final double WIDTH_RANGE_MARGIN = 0.5;
    private static final int WIDTH_RANGE_STEPS = 50;

    // Coarse window stride, as a fraction of the candidate width being tested. Smaller is safer
    // (less likely to step over a narrow correlation peak) but proportionally slower.
    private static final double COARSE_STRIDE_FRACTION = 0.25;

    /**
     * Extracts one real, raw occurrence of the sub-operation that repeats {@code k} times,
     * back-to-back, inside {@code calibrationTrace}.
     *
     * @param calibrationTrace trace containing exactly {@code k} consecutive repeats of one
     *                         unknown-duration sub-operation and nothing else
     * @param k                number of consecutive repeats in {@code calibrationTrace}
     * @return a new {@link Trace} holding the raw samples of one clean occurrence
     */
    public static Trace extractOneOccurrence(Trace calibrationTrace, int k) throws InterruptedException {
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive, was " + k);
        }
        if (calibrationTrace.getDataCount() < k) {
            throw new IllegalArgumentException("Calibration trace is too short to contain " + k + " repeats");
        }

        char[] mask = new char[k];
        Arrays.fill(mask, PATTERN_CHARACTER);
        double[] voltage = calibrationTrace.getVoltage();

        MaskTemplateSearch maskTemplateSearch = new MaskTemplateSearch();
        MaskTemplateSearch.SearchResult best = null;
        int bestCoarseTakeNth = 1;
        int candidateNumber = 0;
        List<Integer> widths = candidateWidths(calibrationTrace.getDataCount(), k);
        log.info("Searching for a {}-times repeating pattern across {} candidate widths ({}..{} samples)",
                k, widths.size(), widths.isEmpty() ? 0 : widths.get(0), widths.isEmpty() ? 0 : widths.get(widths.size() - 1));

        for (int width : widths) {
            candidateNumber++;
            int coarseTakeNth = Math.max(1, (int) (width * COARSE_STRIDE_FRACTION));
            HashMap<Character, Integer> characterWidths = new HashMap<>();
            characterWidths.put(PATTERN_CHARACTER, width);

            MaskTemplateSearch.SearchResult result = maskTemplateSearch.search(voltage, mask, characterWidths, coarseTakeNth);
            log.debug("Candidate width {}/{}: {} samples, stride {} -> {}",
                    candidateNumber, widths.size(), width, coarseTakeNth,
                    result == null ? "does not fit" : "correlation " + result.correlationValue);
            if (result != null && (best == null || result.correlationValue > best.correlationValue)) {
                best = result;
                bestCoarseTakeNth = coarseTakeNth;
            }
        }

        if (best == null) {
            throw new IllegalStateException("No candidate width fit inside the calibration trace");
        }

        log.info("Best coarse match: width {}, correlation {}, refining locally", best.wholeTemplateSize / k, best.correlationValue);
        MaskTemplateSearch.SearchResult refined = refine(voltage, mask, best, bestCoarseTakeNth, maskTemplateSearch);
        return sliceFirstOccurrence(calibrationTrace, refined);
    }

    /**
     * Candidate per-repeat sample-widths to search, centered on the naive
     * {@code dataCount / k} estimate with a margin either side, since the real duration is
     * unknown.
     */
    private static List<Integer> candidateWidths(int dataCount, int k) {
        int centerWidth = Math.max(1, dataCount / k);
        int minWidth = Math.max(1, (int) (centerWidth * (1 - WIDTH_RANGE_MARGIN)));
        // maxWidth is intentionally allowed to exceed dataCount / k: MaskTemplateSearch.search
        // returns null for any width that doesn't fit k repeats inside the trace, so candidates
        // above the naive estimate are simply skipped rather than needing to be excluded here.
        int maxWidth = (int) (centerWidth * (1 + WIDTH_RANGE_MARGIN));
        int step = Math.max(1, (maxWidth - minWidth) / WIDTH_RANGE_STEPS);

        List<Integer> widths = new ArrayList<>();
        for (int width = minWidth; width <= maxWidth; width += step) {
            widths.add(width);
        }
        return widths;
    }

    // Factor by which the refinement stride shrinks each round. A single jump straight from the
    // coarse stride to 1-sample resolution would still mean re-scanning a neighborhood sized by
    // the (large) coarse stride at full (width * k) cost per position - which can cost more than
    // the entire coarse scan. Instead, each round only has to search a small, constant number of
    // positions (about REFINEMENT_FACTOR * 2) at the new, finer stride, so the total refinement
    // cost stays bounded regardless of how coarse the very first stride was.
    private static final int REFINEMENT_FACTOR = 4;

    /**
     * Repeatedly halves/quarters the window stride, each round re-searching only a small
     * neighborhood around the current best hit at that finer stride, until reaching full
     * (1-sample) resolution. This recovers the exact offset the coarse stride may have stepped
     * over, without ever re-scanning a large window range at full resolution.
     */
    private static MaskTemplateSearch.SearchResult refine(
            double[] voltage,
            char[] mask,
            MaskTemplateSearch.SearchResult coarse,
            int coarseTakeNth,
            MaskTemplateSearch maskTemplateSearch) throws InterruptedException {
        HashMap<Character, Integer> characterWidths = new HashMap<>();
        characterWidths.put(PATTERN_CHARACTER, coarse.wholeTemplateSize / mask.length);

        MaskTemplateSearch.SearchResult current = coarse;
        int stride = coarseTakeNth;
        while (stride > 1) {
            int nextStride = Math.max(1, stride / REFINEMENT_FACTOR);
            // the true optimum is within one stride of the previous round's hit
            int margin = stride;
            int sliceStart = Math.max(0, current.windowIndex - margin);
            int sliceEnd = Math.min(voltage.length, current.windowIndex + margin + current.wholeTemplateSize);
            double[] slice = Arrays.copyOfRange(voltage, sliceStart, sliceEnd);

            MaskTemplateSearch.SearchResult refined = maskTemplateSearch.search(slice, mask, characterWidths, nextStride);
            if (refined != null) {
                current = new MaskTemplateSearch.SearchResult(
                        sliceStart + refined.windowIndex,
                        refined.correlationValue,
                        refined.wholeTemplateSize,
                        refined.intervals);
            } else {
                log.warn("Refinement round at stride {} failed unexpectedly, keeping the previous hit", nextStride);
            }
            stride = nextStride;
        }

        return current;
    }

    private static Trace sliceFirstOccurrence(Trace calibrationTrace, MaskTemplateSearch.SearchResult result) {
        IntRange firstOccurrence = result.intervals.get(PATTERN_CHARACTER).get(0);
        int firstIndex = result.windowIndex + firstOccurrence.getFirstIndex();
        int lastIndex = result.windowIndex + firstOccurrence.getLastIndex();

        double[] voltage = Arrays.copyOfRange(calibrationTrace.getVoltage(), firstIndex, lastIndex);
        double[] time = Arrays.copyOfRange(calibrationTrace.getTime(), firstIndex, lastIndex);

        double voltageMaximum = Double.NEGATIVE_INFINITY;
        double voltageMinimum = Double.POSITIVE_INFINITY;
        for (double v : voltage) {
            if (v > voltageMaximum) voltageMaximum = v;
            if (v < voltageMinimum) voltageMinimum = v;
        }

        return new Trace(
                calibrationTrace.getVoltageUnit(),
                calibrationTrace.getTimeUnit(),
                voltage.length,
                voltage,
                time,
                voltageMaximum,
                voltageMinimum);
    }
}
