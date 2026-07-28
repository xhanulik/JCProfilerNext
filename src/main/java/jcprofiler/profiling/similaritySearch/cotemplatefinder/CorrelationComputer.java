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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes, for a single mask character and a single candidate window offset, the average
 * correlation between each of that character's occurrences and their own average segment.
 * Ported unchanged (same math) from the CO Template Finder module of
 * scrutiny-power-traces-analyzer (muni.cotemplate.module.CorrelationComputer).
 *
 * @author Martin Podhora
 */
public class CorrelationComputer implements Runnable {
    private final double[] voltage;
    private final HashMap<Character, double[]> correlations;
    private final Map.Entry<Character, List<IntRange>> characterIntervals;
    private final int characterCount;
    private final int segmentWidth;
    private final int windowIndexFrom;
    private final int windowIndexTo;
    private final int takeNth;
    private final double initialNumber;

    public CorrelationComputer(
            double[] voltage,
            HashMap<Character, double[]> correlations,
            Map.Entry<Character, List<IntRange>> characterIntervals,
            int characterCount,
            int segmentWidth,
            int windowIndexFrom,
            int windowIndexTo,
            int takeNth,
            double initialNumber) {
        this.voltage = voltage;
        this.correlations = correlations;
        this.characterIntervals = characterIntervals;
        this.characterCount = characterCount;
        this.segmentWidth = segmentWidth;
        this.windowIndexFrom = windowIndexFrom;
        this.windowIndexTo = windowIndexTo;
        this.takeNth = takeNth;
        this.initialNumber = initialNumber;
    }

    @Override
    public void run() {
        for (int windowIndex = windowIndexFrom; windowIndex < windowIndexTo; windowIndex += takeNth) {
            computeCorrelationForCharacterAndWindowIndex(
                    voltage,
                    correlations,
                    characterIntervals,
                    characterCount,
                    segmentWidth,
                    windowIndex);
        }

        postprocess(correlations.get(characterIntervals.getKey()));
    }

    private void computeCorrelationForCharacterAndWindowIndex(
            double[] voltage,
            HashMap<Character, double[]> correlations,
            Map.Entry<Character, List<IntRange>> characterIntervals,
            int characterCount,
            int segmentWidth,
            int windowIndex) {
        double[] averageSegment = computeAverageSegment(
                voltage,
                characterIntervals,
                characterCount,
                segmentWidth,
                windowIndex);

        double averageCorrelation = computeAverageCorrelation(
                voltage,
                characterIntervals,
                characterCount,
                segmentWidth,
                windowIndex,
                averageSegment);

        correlations.get(characterIntervals.getKey())[windowIndex] = averageCorrelation;
    }

    private double computeAverageCorrelation(double[] voltage, Map.Entry<Character, List<IntRange>> characterIntervals, int characterCount, int segmentWidth, int windowIndex, double[] averageSegment) {
        double averageCorrelation = 0;
        for (IntRange interval : characterIntervals.getValue()) {
            double segmentCorrelation = correlationCoefficientStable(voltage, averageSegment, windowIndex + interval.getFirstIndex(), windowIndex + interval.getLastIndex(), segmentWidth);
            averageCorrelation += segmentCorrelation;
        }

        return averageCorrelation / characterCount;
    }

    private double[] computeAverageSegment(double[] voltage, Map.Entry<Character, List<IntRange>> characterIntervals, int characterCount, int segmentWidth, int windowIndex) {
        double[] averageSegment = new double[segmentWidth];
        for (IntRange interval : characterIntervals.getValue()) {
            int segmentIndex = 0;
            for (int traceIndex = interval.getFirstIndex(); traceIndex < interval.getLastIndex(); traceIndex++) {
                averageSegment[segmentIndex] += voltage[windowIndex + traceIndex] / characterCount;
                segmentIndex++;
            }
        }
        return averageSegment;
    }

    public static double correlationCoefficientStable(double[] voltage, double[] averageSegment, int intervalFrom, int intervalTo, int n) {
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double squareSumX = 0;
        double squareSumY = 0;
        int segmentIndex = 0;
        for (int intervalIndex = intervalFrom; intervalIndex < intervalTo; intervalIndex++) {
            sumX = sumX + voltage[intervalIndex];
            sumY = sumY + averageSegment[segmentIndex];
            sumXY = sumXY + voltage[intervalIndex] * averageSegment[segmentIndex];
            squareSumX = squareSumX + voltage[intervalIndex] * voltage[intervalIndex];
            squareSumY = squareSumY + averageSegment[segmentIndex] * averageSegment[segmentIndex];
            segmentIndex++;
        }

        double corr = (n * sumXY - sumX * sumY) / Math.sqrt(((n * squareSumX - sumX * sumX)*(n * squareSumY - sumY * sumY))+0.00001);
        return corr;
    }

    private double[] postprocess(double[] distances) {
        double previousValidNumber = getFirstNoninitialNumber(distances, initialNumber);
        for (int i = 0; i < distances.length; i++) {
            if (distances[i] < -1) {
                distances[i] = previousValidNumber;
            }

            previousValidNumber = distances[i];
        }

        return distances;
    }

    private double getFirstNoninitialNumber(double[] distances, double initialNumber) {
        for (int i = 0; i < distances.length; i++) {
            if (distances[i] > initialNumber) {
                return distances[i];
            }
        }

        return 0;
    }
}
