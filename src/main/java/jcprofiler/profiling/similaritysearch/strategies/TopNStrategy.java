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

package jcprofiler.profiling.similaritysearch.strategies;

import jcprofiler.profiling.similaritysearch.Similarity;
import jcprofiler.profiling.similaritysearch.util.OverlappingPair;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 *
 * @author Martin Podhora
 */
public class TopNStrategy implements SimilaritySearchStrategy {
    
    private final SortedSet<Similarity> similarities;
    private final int topNOccurences;
    
    public TopNStrategy(int topNOccurences) {
        this.topNOccurences = topNOccurences;
        this.similarities = new TreeSet<>();
    }
    
    @Override
    public void addSimilarity(Similarity similarity) {
        if (similarities.isEmpty()) {
            similarities.add(similarity);
            return;
        }
        
        if (similarity.getDistance() < similarities.last().getDistance() || !isFull())
        {
            OverlappingPair<Boolean, Similarity> overlapping = isOverlapping(similarity);
            if (overlapping.getFirstValue()&& overlapping.getSecondValue().getDistance() > similarity.getDistance()) {
                similarities.remove(overlapping.getSecondValue());
                similarities.add(similarity);
                return;
            }
            if (!overlapping.getFirstValue() && isFull()) {
                similarities.remove(similarities.last());                
                similarities.add(similarity);
                return;
            }
            if (!overlapping.getFirstValue() && !isFull()) {
                similarities.add(similarity);
            }
        }
    }
    
    private boolean isFull() {
        return similarities.size() >= topNOccurences;
    }
    
    private OverlappingPair<Boolean, Similarity> isOverlapping(Similarity similarityToAdd) {
        for (Similarity similarity : similarities) {
            if (similarity.getLastIndex() > similarityToAdd.getFirstIndex() && similarity.getFirstIndex() < similarityToAdd.getLastIndex()) 
                return new OverlappingPair<>(true, similarity);
        }
        return new OverlappingPair<>(false, null);
    }
    
    @Override
    public SortedSet<Similarity> getSimilarities() {
        return similarities;
    } 
}
