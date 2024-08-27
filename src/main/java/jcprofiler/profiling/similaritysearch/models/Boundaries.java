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

package jcprofiler.profiling.similaritysearch.models;

/**
 * Value class used for highlighting areas of chart.
 * Contains indices and real values on those indices.
 * 
 * @author Martin Podhora
 */
public class Boundaries implements Comparable<Boundaries> {
    private final double lowerBound;
    private final double upperBound;
    private final int firstIndex;
    private final int lastIndex;
    
    public Boundaries(double lowerBound, double upperBound, int beginingIndex, int endingIndex) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.firstIndex = beginingIndex;
        this.lastIndex = endingIndex;
    }
    
    public double getLowerBound() {
        return lowerBound;
    }

    public double getUpperBound() {
        return upperBound;
    }
    
    public int getFirstIndex() {
        return firstIndex;
    }
    
    public int getLastIndex() {
        return lastIndex;
    }

    @Override
    public int compareTo(Boundaries b) {
        if (this.getLowerBound() > b.getLowerBound())
            return 1;
        if (this.getLowerBound() < b.getLowerBound())
            return -1;
        return 0;    }

    public long getLowerBoundNano() {
        return (long) (this.getLowerBound() * 1000000);
    }

    public long getUpperBoundNano() {
        return  (long) (this.getUpperBound() * 1000000);
    }
}
