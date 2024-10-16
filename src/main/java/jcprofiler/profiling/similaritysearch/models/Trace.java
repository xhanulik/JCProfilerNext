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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * This class represents trace consisting of time and voltage.
 * 
 * @author Martin Podhora
 */
public class Trace {
    private static final int TIME_UNIT_CONSTANT = 1000;

    private final double[] voltageArray;

    private final double[] timeArray;

    private final int dataCount;
    /**
     * Unit of voltage, should be V or mV.
     */
    private final String voltageUnit;
    /**
     * Unit of time, should be ms or s.
     */
    private final String timeUnit;
    
    private double voltageMaximum;
    
    private double voltageMinimum;
        
    /**
     * Constructor that creates an instance of Trace.
     * 
     * @param dataCount - pairs (time, voltage) that are in the trace 
     * @param voltageUnit - unit of voltage
     * @param timeUnit - unit of time
     */
    public Trace(int dataCount, String voltageUnit, String timeUnit) {
        this.voltageUnit = voltageUnit;
        this.timeUnit = timeUnit;
        this.dataCount = dataCount;
        this.voltageArray = new double[dataCount];
        this.timeArray = new double[dataCount];
        this.voltageMaximum = Double.NEGATIVE_INFINITY;
        this.voltageMinimum = Double.POSITIVE_INFINITY;
    }
    
    public Trace(String voltageUnit, String timeUnit, int dataCount, double[] voltageArray, double[] timeArray, double voltageMaximum, double voltageMinimum) {
        this.voltageUnit = voltageUnit;
        this.timeUnit = timeUnit;
        this.dataCount = dataCount;
        this.voltageArray = voltageArray;
        this.timeArray = timeArray;
        this.voltageMaximum = voltageMaximum;
        this.voltageMinimum = voltageMinimum;
    }

    public Trace(String voltageUnit, String timeUnit, int dataCount, double[] voltageArray, double[] timeArray) {
        this.voltageUnit = voltageUnit;
        this.timeUnit = timeUnit;
        this.dataCount = dataCount;
        this.voltageArray = voltageArray;
        this.timeArray = timeArray;
        this.voltageMaximum = Double.NEGATIVE_INFINITY;
        this.voltageMinimum = Double.POSITIVE_INFINITY;
    }
    
    /**
     * Adds pair of data to the trace.
     * 
     * @param voltageValue
     * @param timeValue
     * @param position
     */
    public void addData(double voltageValue, double timeValue, int position) {
        voltageArray[position] = voltageValue;
        timeArray[position] = timeValue;
        if (voltageArray[position] < voltageMinimum) voltageMinimum = voltageArray[position];
        if (voltageArray[position] > voltageMaximum) voltageMaximum = voltageArray[position];
    }
    
    /**
     * Voltage getter, that returns whole array.
     * 
     * @return voltage array
     */
    public double[] getVoltage() {
        return voltageArray;
    }
    
    /**
     * Voltage getter, that returns only value on specified position.
     * 
     * @param position
     * @return 
     */
    public double getVoltageOnPosition(int position) {
        return voltageArray[position];
    }
    
    /**
     * Voltage setter, that sets value on specified position.
     * 
     * @param position
     * @param value 
     */
    public void setVoltageOnPosition(double value, int position) {
        voltageArray[position] = value;
        if (voltageArray[position] < voltageMinimum) voltageMinimum = voltageArray[position];
        if (voltageArray[position] > voltageMaximum) voltageMaximum = voltageArray[position];
    }
    
    /**
     * Time getter, that returns whole array.
     * 
     * @return time array
     */
    public double[] getTime() {
        return timeArray;
    }
    
    /**
     * Time getter, that returns only value on specified position.
     * 
     * @param position
     * @return 
     */
    public double getTimeOnPosition(int position) {
        return timeArray[position];
    }
    
    /**
     * Time setter, that sets value on specified position.
     * 
     * @param position
     * @param value 
     */
    public void setTimeOnPosition(int position, double value) {
        timeArray[position] = value;
    }
    
    /**
     * Voltage unit getter.
     * 
     * @return string representation of voltage unit
     */
    public String getVoltageUnit() {
        return voltageUnit;
    }
    
    /**
     * Time unit getter,
     * 
     * @return string representation of time unit
     */
    public String getTimeUnit() {
        return timeUnit;
    }
    
    /**
     * Data count getter.
     * 
     * @return number of pairs (time, voltage) in the trace
     */
    public int getDataCount() {
        return dataCount;
    }
    
    /**
     * Sampling frequency getter.
     * 
     * @return sampling frequency
     */
    public int getSamplingFrequency() {
        BigDecimal dT = BigDecimal.valueOf(timeArray[0])
                .abs()
                .setScale(10, RoundingMode.HALF_UP)
                .subtract(BigDecimal
                        .valueOf(timeArray[1])
                        .abs()
                        .setScale(10, RoundingMode.HALF_UP))
                .abs()
                .divide(new BigDecimal(TIME_UNIT_CONSTANT), 10, RoundingMode.HALF_UP);
        dT = BigDecimal.ONE.divide(dT, 10, RoundingMode.HALF_UP);
        return dT.intValue();
    }
    
    public double getMaximalVoltage() {
        return voltageMaximum;
    }
    
    public double getMinimalVoltage() {
        return voltageMinimum;
    }
}
