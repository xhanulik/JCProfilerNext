// SPDX-FileCopyrightText: 2019-2026 Martin Podhora (martinftlsx)
// SPDX-License-Identifier: MIT

/**
 * This file is copied from the SPA-Cryptographic-Operations-Extractor,
 * originally developed by Martin Podhora (https://github.com/crocs-muni/SPA-Cryptographic-Operations-Extractor),
 * and licensed under MIT license.
 *
 * Original code licensed under the MIT License:
 * Copyright (c) 2019 martinftlsx
 *
 * Licensed under the MIT License.
 * See LICENSES/MIT.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.profiling.similaritySearch.cotemplatefinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * For one candidate set of per-mask-character widths, finds the trace window offset where the
 * mask's repeated characters correlate most strongly with their own average segment, and
 * combines the per-character correlations into a single weighted correlation series.
 * <p>
 * Ported unchanged (same algorithm) from the mask/interval bookkeeping and weighted-correlation
 * combination logic of
 * {@code muni.scrutiny.cmdapp.actions.COTemplateFinderAction} in scrutiny-power-traces-analyzer
 * (generateMaskIntervalsForTimes, initializeCorrelations, findMostCorrelatedSegment,
 * computeWeightedCorrelations) - JSON configuration, GPU computation and chart/report generation
 * were console-app/CLI-only concerns and are intentionally not ported, since they are not part
 * of the correlation algorithm itself. Per-character widths (in samples) are passed in directly
 * instead of being derived from a JSON configuration's durations-in-seconds.
 *
 * @author Martin Podhora
 */
public class MaskTemplateSearch {
    private static final double INITIAL_NUMBER = -2;

    public static class MaskIntervals {
        public final HashMap<Character, List<IntRange>> intervals;
        public final int wholeTemplateSize;

        public MaskIntervals(HashMap<Character, List<IntRange>> intervals, int wholeTemplateSize) {
            this.intervals = intervals;
            this.wholeTemplateSize = wholeTemplateSize;
        }
    }

    public static class SearchResult {
        public final int windowIndex;
        public final double correlationValue;
        public final int wholeTemplateSize;
        public final HashMap<Character, List<IntRange>> intervals;

        public SearchResult(int windowIndex, double correlationValue, int wholeTemplateSize, HashMap<Character, List<IntRange>> intervals) {
            this.windowIndex = windowIndex;
            this.correlationValue = correlationValue;
            this.wholeTemplateSize = wholeTemplateSize;
            this.intervals = intervals;
        }
    }

    /**
     * Runs one full search iteration for the given mask and per-character widths (in samples).
     *
     * @return {@code null} if the given widths don't fit inside the trace at all
     */
    public SearchResult search(double[] voltage, char[] mask, HashMap<Character, Integer> characterWidths, int takeNth) throws InterruptedException {
        HashMap<Character, Integer> characterCounts = countCharacters(mask);
        MaskIntervals maskIntervals = generateMaskIntervalsForWidths(mask, characterWidths);

        if (maskIntervals.wholeTemplateSize > voltage.length) {
            return null;
        }

        int endingIndex = voltage.length - maskIntervals.wholeTemplateSize;
        if (endingIndex <= 0) {
            return null;
        }

        HashMap<Character, double[]> correlations = initializeCorrelations(maskIntervals.intervals, endingIndex);
        for (Map.Entry<Character, List<IntRange>> characterIntervals : maskIntervals.intervals.entrySet()) {
            int characterCount = characterCounts.get(characterIntervals.getKey());
            int segmentWidth = characterWidths.get(characterIntervals.getKey());
            findMostCorrelatedSegment(voltage, endingIndex, correlations, characterIntervals, characterCount, segmentWidth, takeNth);
        }

        WeightedCorrelationResult wcr = computeWeightedCorrelations(characterCounts, characterWidths, voltage.length, correlations);
        return new SearchResult(wcr.maxCorrelationIndex, wcr.maxCorrelationValue, maskIntervals.wholeTemplateSize, maskIntervals.intervals);
    }

    private HashMap<Character, Integer> countCharacters(char[] mask) {
        HashMap<Character, Integer> counts = new HashMap<>();
        for (char c : mask) {
            counts.merge(c, 1, Integer::sum);
        }
        return counts;
    }

    private MaskIntervals generateMaskIntervalsForWidths(char[] mask, HashMap<Character, Integer> characterWidths) {
        HashMap<Character, List<IntRange>> maskIntervals = new HashMap<>();
        int lastIntervalIndex = 0;
        for (char maskCharacter : mask) {
            int width = characterWidths.get(maskCharacter);
            if (!maskIntervals.containsKey(maskCharacter)) {
                List<IntRange> elementIntervals = new ArrayList<>();
                elementIntervals.add(new IntRange(lastIntervalIndex, lastIntervalIndex + width));
                maskIntervals.put(maskCharacter, elementIntervals);
            } else {
                maskIntervals.get(maskCharacter).add(new IntRange(lastIntervalIndex, lastIntervalIndex + width));
            }

            lastIntervalIndex += width;
        }
        return new MaskIntervals(maskIntervals, lastIntervalIndex);
    }

    private HashMap<Character, double[]> initializeCorrelations(HashMap<Character, List<IntRange>> maskIntervals, int endingIndex) {
        HashMap<Character, double[]> correlations = new HashMap<>();
        for (Map.Entry<Character, List<IntRange>> characterIntervals : maskIntervals.entrySet()) {
            double[] voltage = new double[endingIndex];
            Arrays.fill(voltage, INITIAL_NUMBER);
            correlations.put(characterIntervals.getKey(), voltage);
        }
        return correlations;
    }

    private void findMostCorrelatedSegment(
            double[] voltage,
            int endingIndex,
            HashMap<Character, double[]> correlations,
            Map.Entry<Character, List<IntRange>> characterIntervals,
            int characterCount,
            int segmentWidth,
            int takeNth) throws InterruptedException {
        if (endingIndex > 1000) {
            int cores = Runtime.getRuntime().availableProcessors();
            ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(cores);
            double jump = (double) endingIndex / cores;
            for (int index = 0; index < endingIndex; index += jump) {
                executor.execute(new CorrelationComputer(
                        voltage,
                        correlations,
                        characterIntervals,
                        characterCount,
                        segmentWidth,
                        index,
                        Math.min((int) (index + jump), endingIndex),
                        takeNth,
                        INITIAL_NUMBER));
            }

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } else {
            CorrelationComputer correlationComputer = new CorrelationComputer(
                    voltage,
                    correlations,
                    characterIntervals,
                    characterCount,
                    segmentWidth,
                    0,
                    endingIndex,
                    takeNth,
                    INITIAL_NUMBER);
            correlationComputer.run();
        }
    }

    private WeightedCorrelationResult computeWeightedCorrelations(
            HashMap<Character, Integer> characterCounts,
            HashMap<Character, Integer> characterWidths,
            int voltageLength,
            HashMap<Character, double[]> correlations) {
        WeightedCorrelationResult wcr = new WeightedCorrelationResult(0, Double.MIN_VALUE);
        for (int correlationIndex = 0; correlationIndex < voltageLength; correlationIndex++) {
            double weightedCorrelationsNumerator = 0;
            double weightedCorrelationsDenominator = 0;
            for (Map.Entry<Character, double[]> characterCorrelations : correlations.entrySet()) {
                if (correlationIndex >= characterCorrelations.getValue().length) {
                    weightedCorrelationsNumerator = 0;
                    weightedCorrelationsDenominator = 1;
                    break;
                }

                int characterCount = characterCounts.get(characterCorrelations.getKey());
                int characterWidth = characterWidths.get(characterCorrelations.getKey());
                weightedCorrelationsNumerator += characterCount * characterWidth * characterCorrelations.getValue()[correlationIndex];
                weightedCorrelationsDenominator += characterCount * characterWidth;
            }

            double iterationCorr = weightedCorrelationsNumerator / weightedCorrelationsDenominator;
            if (wcr.maxCorrelationValue < iterationCorr) {
                wcr.maxCorrelationValue = iterationCorr;
                wcr.maxCorrelationIndex = correlationIndex;
            }
        }

        return wcr;
    }

    private static class WeightedCorrelationResult {
        int maxCorrelationIndex;
        double maxCorrelationValue;

        WeightedCorrelationResult(int maxCorrelationIndex, double maxCorrelationValue) {
            this.maxCorrelationIndex = maxCorrelationIndex;
            this.maxCorrelationValue = maxCorrelationValue;
        }
    }
}
