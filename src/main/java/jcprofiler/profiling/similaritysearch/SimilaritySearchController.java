/**
 * MIT License
 *
 * Copyright (c) 2019 martinftlsx
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package jcprofiler.profiling.similaritysearch;

import jcprofiler.profiling.similaritysearch.distancemeasure.DistanceMeasure;
import jcprofiler.profiling.similaritysearch.distancemeasure.ManhattanDistance;
import jcprofiler.profiling.similaritysearch.filters.LowPassFilter;
import jcprofiler.profiling.similaritysearch.models.Trace;
import jcprofiler.profiling.similaritysearch.multithread.MultiThreadController;

import java.util.SortedSet;

/**
 *
 * @author Martin Podhora
 */
public class SimilaritySearchController {
    public static final int CUTOFF_FREQUENCY = 10000;
    public static final int JUMPING_DISTANCE = 10;
    private static final float TOLERATED_SAMPLING_RATIO_UPPER_BOUND = 1.05f;
    private static final float TOLERATED_SAMPLING_RATIO_LOWER_BOUND = 0.95f;
    private static final double TOLERATED_Y_AXIS_DIFFERENCE_UPPER_BOUND = 5;
    private static final double TOLERATED_Y_AXIS_DIFFERENCE_LOWER_BOUND = -5;
    public static final ManhattanDistance MANHATTAN_DISTANCE_ALGORITHM = new ManhattanDistance();
    
    private static Trace removeFromOperation(float toRetainRatio, Trace operation) {
        int retained = 0;
        float retainedRatio = 0;
        int toRetain = Math.round(operation.getDataCount() * toRetainRatio);
        double[] operationVoltage = new double[toRetain];
        double voltageMaximum = Double.NEGATIVE_INFINITY;
        double voltageMinimum = Double.POSITIVE_INFINITY;

        for (int i = 0; i < operation.getDataCount(); i++) {
            if (retainedRatio < toRetainRatio) {
                operationVoltage[retained] = operation.getVoltageOnPosition(i);
                if (operationVoltage[retained] > voltageMaximum)
                    voltageMaximum = operationVoltage[retained];
                if (operationVoltage[retained] < voltageMinimum)
                    voltageMinimum = operationVoltage[retained];
                retained++;
            }
            retainedRatio = retained / (i + 1f);
        }
        return new Trace(operation.getVoltageUnit(), operation.getTimeUnit(), retained, operationVoltage, null, voltageMaximum, voltageMinimum);
    }
    
    private static Trace addToOperation(float toAddRatio, Trace operation) {
        int toAdd = Math.round(operation.getDataCount() * toAddRatio);
        double[] operationVoltage = new double[toAdd];
        int previousDataCounter = 0;
        //Adding first because assignement in for cycle needs first element to be added
        operationVoltage[0] = operation.getVoltageOnPosition(0);
        int added = 1;
        float addedRatio = 1f;
        
        for (int i = 1; i < toAdd; i++) {
            if (addedRatio < toAddRatio) {
                operationVoltage[i] = (operationVoltage[i - 1] + operation.getVoltageOnPosition(previousDataCounter)) / 2;
                added++;
            } else {
                operationVoltage[i] = operation.getVoltageOnPosition(previousDataCounter);
                previousDataCounter++;
            }
            addedRatio = added / (previousDataCounter + 1f);
        }
        return new Trace(operation.getVoltageUnit(), operation.getTimeUnit(), toAdd, operationVoltage, null, operation.getMaximalVoltage(), operation.getMinimalVoltage());
    }
    
    /**
     * TO ADJUST SIZE OF ARRAY WHILE REMOVING
     * 
     * @param trace
     * @param traceSamplingFrequency
     * @param operation
     * @param operationSamplingFrequency
     * @return 
     */
    private static Trace adjustSamplingFrequency(Trace trace, int traceSamplingFrequency, Trace operation, int operationSamplingFrequency) {
        float traceToOperationRatio = 1f * traceSamplingFrequency / operationSamplingFrequency;
        if (traceToOperationRatio < TOLERATED_SAMPLING_RATIO_LOWER_BOUND)
            return removeFromOperation(traceToOperationRatio, operation);
        if (traceToOperationRatio > TOLERATED_SAMPLING_RATIO_UPPER_BOUND)
            return addToOperation(traceToOperationRatio, operation);
        return operation;
    }
    
    private static Trace applyFilterMakeCopy(Trace trace, int cutoffFrequency) {
        LowPassFilter lowPassFilter = new LowPassFilter(trace.getSamplingFrequency(), cutoffFrequency);
        Trace traceCopy = lowPassFilter.applyLowPassFilterMakeCopy(trace);
        return traceCopy;
    }
    
    private static void moveAlongYAxis(Trace trace, Trace operation) {
        double distance = trace.getMaximalVoltage() - operation.getMaximalVoltage();
        if (distance < TOLERATED_Y_AXIS_DIFFERENCE_UPPER_BOUND && distance > TOLERATED_Y_AXIS_DIFFERENCE_LOWER_BOUND) 
            return;
        for (int i = 0; i < operation.getDataCount(); i++) {
            operation.setVoltageOnPosition(operation.getVoltageOnPosition(i) + distance, i);
        }
    }
    
    public static SortedSet<Similarity> searchTraceForOperation(Trace trace, Trace operation, DistanceMeasure distanceAlgorithm, int TopNStrategyCount) throws InterruptedException {
        Trace traceCopy = applyFilterMakeCopy(trace, CUTOFF_FREQUENCY);
        Trace operationCopy = applyFilterMakeCopy(operation, CUTOFF_FREQUENCY);

        Trace modifiedOperation = adjustSamplingFrequency(traceCopy, trace.getSamplingFrequency(), operationCopy, operation.getSamplingFrequency());
       
        //moveAlongYAxis(traceOperationCopies.getKey(), traceOperationCopies.getValue()); Needed functionality? What options do I have? According to max, min, 0 or other constant. Move trace or operation?
        
        return MultiThreadController.searchForSimilarities(traceCopy, modifiedOperation, distanceAlgorithm, TopNStrategyCount);
    }
}