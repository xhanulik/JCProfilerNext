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

package jcprofiler.profiling.similaritysearch.multithread;

import jcprofiler.profiling.similaritysearch.Similarity;
import jcprofiler.profiling.similaritysearch.SimilaritySearchController;
import jcprofiler.profiling.similaritysearch.distancemeasure.DistanceMeasure;
import jcprofiler.profiling.similaritysearch.models.Trace;
import jcprofiler.profiling.similaritysearch.strategies.SimilaritySearchStrategy;

/**
 *
 * @author Martin Podhora
 */
public class ProcessDataChunkTask implements Runnable {
    private final int firstIndex;
    private final int lastIndex;
    private final Trace preprocessedTrace;
    private final Trace preprocessedOperation;
    private final DistanceMeasure distanceAlgorithm;
    private final SimilaritySearchStrategy similaritySearchStrategy;

    
    public ProcessDataChunkTask(int firstIndex
            , int lastIndex
            , Trace preprocessedTrace
            , Trace preprocessedOperation
            , DistanceMeasure distanceAlgorithm
            , SimilaritySearchStrategy similaritySearchStrategy) {
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.preprocessedTrace = preprocessedTrace;
        this.preprocessedOperation = preprocessedOperation;
        this.distanceAlgorithm = distanceAlgorithm;
        this.similaritySearchStrategy = similaritySearchStrategy;

    }
    
    @Override
    public void run() {
        int firstIndexCounter = firstIndex;
        int lastIndexCounter = firstIndex + preprocessedOperation.getDataCount();
        int stoppingIndex = lastIndex + preprocessedOperation.getDataCount();
        if (stoppingIndex > preprocessedTrace.getDataCount()) stoppingIndex = preprocessedTrace.getDataCount();
        while (lastIndexCounter < stoppingIndex) {
            Similarity similarity = new Similarity(firstIndexCounter
                    , lastIndexCounter
                    , distanceAlgorithm.compute(preprocessedOperation.getVoltage(), preprocessedTrace.getVoltage(), firstIndexCounter));
            similaritySearchStrategy.addSimilarity(similarity);
            firstIndexCounter += SimilaritySearchController.JUMPING_DISTANCE;
            lastIndexCounter += SimilaritySearchController.JUMPING_DISTANCE;

        }
    }
    
}
