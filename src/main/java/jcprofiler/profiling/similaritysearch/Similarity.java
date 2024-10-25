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

/**
 *
 * @author Martin Podhora
 */
public class Similarity implements Comparable<Similarity> {
    private final int firstIndex;
    private final int lastIndex;
    private final double distance;
    
    public Similarity(int firstIndex, int lastIndex, double distance) {
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
        this.distance = distance;
    }
    
    public int getFirstIndex() {
        return firstIndex;
    }
    
    public int getLastIndex() {
        return lastIndex;
    }
    
    public double getDistance() {
        return distance;
    }
    
    @Override
    public String toString() {
        return "<" + firstIndex + ", "+ lastIndex +"> at distance: " + distance + "\n";
    }

    @Override
    public int compareTo(Similarity s) {
        if (this.getDistance() == s.getDistance()) // Be aware of the fact that 2 can have same distance and they are not the same.
            return 0;
        if (this.getDistance() > s.getDistance()) 
            return 1;
        return -1;    
    }
}
