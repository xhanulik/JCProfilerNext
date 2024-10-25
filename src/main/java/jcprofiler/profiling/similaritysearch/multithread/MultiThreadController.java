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

import jcprofiler.profiling.similaritysearch.distancemeasure.DistanceMeasure;
import jcprofiler.profiling.similaritysearch.models.Trace;
import jcprofiler.profiling.similaritysearch.Similarity;
import jcprofiler.profiling.similaritysearch.strategies.SimilaritySearchStrategy;
import jcprofiler.profiling.similaritysearch.strategies.TopNStrategy;

import java.util.SortedSet;

/**
 * In future ThreadPoolExecutor should be used.
 * 
 * @author Martin Podhora
 */
public class MultiThreadController {
    public static SortedSet<Similarity> searchForSimilarities(Trace trace, Trace operation, DistanceMeasure distanceAlgorithm, int TopNStrategyCount) throws InterruptedException {
        int numberOfProcessors = Runtime.getRuntime().availableProcessors();
        int delimitingNumber = trace.getDataCount() / numberOfProcessors;
        Thread[] threads = new Thread[numberOfProcessors];
        SimilaritySearchStrategy[] strategies = new SimilaritySearchStrategy[numberOfProcessors];
        SimilaritySearchStrategy resultStrategy = new TopNStrategy(TopNStrategyCount);
        createAndStartThreads(numberOfProcessors, strategies, threads, delimitingNumber, trace, operation, distanceAlgorithm, TopNStrategyCount);
        stopThreadsAndMergeResults(numberOfProcessors, threads, strategies, resultStrategy);
        return resultStrategy.getSimilarities();
    }

    private static void stopThreadsAndMergeResults(int numberOfProcessors, Thread[] threads, SimilaritySearchStrategy[] strategies, SimilaritySearchStrategy resultStrategy) throws InterruptedException {
        for (int i = 0; i < numberOfProcessors; i++) {
            threads[i].join();
        }
        for (int i = 0; i < numberOfProcessors; i++) {
            strategies[i].getSimilarities().forEach((similarity) -> resultStrategy.addSimilarity(similarity));
        }
    }

    private static void createAndStartThreads(int numberOfProcessors
            , SimilaritySearchStrategy[] strategies
            , Thread[] threads
            , int delimitingNumber
            , Trace trace
            , Trace operation
            , DistanceMeasure distanceAlgorithm
            , int TopNStrategyCount) {
        for (int i = 0; i < numberOfProcessors; i++) {
            strategies[i] = new TopNStrategy(TopNStrategyCount);
            threads[i] = new Thread(new ProcessDataChunkTask(i * delimitingNumber
                    , (i + 1) * delimitingNumber
                    , trace
                    , operation
                    , distanceAlgorithm
                    , strategies[i]));
            threads[i].start();
        }
    }
}
