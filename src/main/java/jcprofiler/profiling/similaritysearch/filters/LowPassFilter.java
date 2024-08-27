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


package jcprofiler.profiling.similaritysearch.filters;

import jcprofiler.profiling.similaritysearch.models.Trace;
import uk.me.berndporr.iirj.Butterworth;

import java.util.Arrays;

/**
 * Class that represents low-pass filter.
 * It is used to filter voltage array of the trace.
 * 
 * @author Martin Podhora
 */
public class LowPassFilter {
    private int samplingFrequency;
    private int cutOffFrequency;
    public static final int ORDER = 1;
    
    /**
     * Constructor
     * 
     * @param samplingFreq
     * @param cutOffFreq
     */
    public LowPassFilter(int samplingFreq, int cutOffFreq) {
        this.samplingFrequency = samplingFreq;
        this.cutOffFrequency = cutOffFreq;
    }
    
    /**
     * Method used to filter voltage values of trace
     * 
     * @param trace 
     */
    public void applyLowPassFilterInPlace(Trace trace) {
        double filteredValue;
        Butterworth butterworth = new Butterworth();
        butterworth.lowPass(ORDER, samplingFrequency, cutOffFrequency);
        for (int i = 0; i < trace.getDataCount(); i++) {
            filteredValue = butterworth.filter(trace.getVoltageOnPosition(i));
            trace.setVoltageOnPosition(filteredValue, i);
        }
    }
    
    public Trace applyLowPassFilterMakeCopy(Trace trace) {
        Trace traceCopy = new Trace(trace.getVoltageUnit(), trace.getTimeUnit(), trace.getDataCount(), Arrays.copyOf(trace.getVoltage(), trace.getDataCount()), null, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        applyLowPassFilterInPlace(traceCopy);
        return traceCopy;
    }
    
    public void setSamplingFrequency(int samplingFrequency) {
        this.samplingFrequency = samplingFrequency;
    }
    
    public void setCutOffFrequency(int cutOffFrequency) {
        this.cutOffFrequency = cutOffFrequency;
    }
}
