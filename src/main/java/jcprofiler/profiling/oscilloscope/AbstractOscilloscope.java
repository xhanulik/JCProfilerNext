package jcprofiler.profiling.oscilloscope;

import jcprofiler.profiling.oscilloscope.drivers.PicoScope4000Driver;
import jcprofiler.profiling.oscilloscope.drivers.PicoScope6000Driver;
import jcprofiler.profiling.similaritysearch.models.Trace;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

public abstract class AbstractOscilloscope {
    final private boolean DEBUG = false;
    protected int PICO_VARIANT_INFO = 3;

    /**
     * Array of implemented oscilloscope drivers
     */
    static Class<?>[] oscilloscopeDrivers = {
            PicoScope4000Driver.class,
            PicoScope6000Driver.class
    };

//    protected static Args args = null;

//    protected AbstractOscilloscope(Args args) {
//        AbstractOscilloscope.args = args;
//    }

    /**
     * Convert ADC to volt values
     * @param voltValue value in Volts
     * @param voltageRange range of voltage values
     * @param maxAdcValue max ADC value
     * @return ADC value
     */
    protected double volt2Adc(double voltValue, double voltageRange, double maxAdcValue) {
        return (voltValue / voltageRange) * maxAdcValue;
    }

    /**
     *
     * @param adcValues
     * @param maxAdcValue
     * @param voltageRange
     * @return
     */
    protected static double[] adc2Volt(short[] adcValues, int maxAdcValue, double voltageRange) {
        double[] voltages = new double[adcValues.length];
        for (int i = 0; i < adcValues.length; i++) {
            voltages[i] = (adcValues[i] / (double) maxAdcValue) * voltageRange;
        }
        return voltages;
    }

    protected static int getSamplingFrequency(int timeIntervalNs) {
        return (int) (1 / (timeIntervalNs / 1_000_000_000.0));
    }

    protected void writeIntoCSV(double[] voltValues, int sampleNumber, Path filePath, int cutOffFrequency, int timeInterval) {
        int samplingFrequency = getSamplingFrequency(timeInterval);
        LowPassFilter filter = null;

        if (cutOffFrequency > 0)
            filter = new LowPassFilter(samplingFrequency, cutOffFrequency);

        // Write into CSV file
        try (FileWriter writer = new FileWriter(filePath.toAbsolutePath().toFile())) {
            writer.append("Time,Channel\n");
            writer.append("(ms),(V)\n");
            writer.append(",\n");

            for (int i = 0; i < sampleNumber; i++) {
                if (filter != null)
                    writer.append(String.format("%9f,%.9f\n", (i * timeInterval) / 1e6, filter.applyLowPassFilter(voltValues[i])));
                else
                    writer.append(String.format("%9f,%.9f\n", (i * timeInterval) / 1e6, voltValues[i]));
            }

            System.out.println("Data has been written to " + filePath);

        } catch (IOException e) {
            throw new RuntimeException("Writing into CSV failed");
        }
    }

    protected Trace createTrace(double[] voltValues, int sampleNumber, int cutOffFrequency, int timeInterval) {
        int samplingFrequency = getSamplingFrequency(timeInterval);
        LowPassFilter filter = null;

        if (cutOffFrequency > 0)
            filter = new LowPassFilter(samplingFrequency, cutOffFrequency);

        double[] timeValues = new double[sampleNumber];
        for (int i = 0; i < sampleNumber; i++) {
            timeValues[i] = (i * timeInterval) / 1e6;
            if (filter != null)
                voltValues[i] = filter.applyLowPassFilter(voltValues[i]);
        }

        return new Trace("V", "ms", sampleNumber, voltValues, timeValues);
    }

    /**
     * Debug print
     * @param format A format string
     * @param args Arguments
     */
    protected void printDebug(String format, Object ... args) {
        if (DEBUG) {
            System.out.printf(format, args);
        }
    }

    /** Factory method
     *
     * @return constructed {@link AbstractOscilloscope} object
     */
    public static AbstractOscilloscope create() {
        for (Class<?> driver : oscilloscopeDrivers) {
            try {
                Constructor<?> constructor = driver.getConstructor();
                AbstractOscilloscope device = (AbstractOscilloscope) constructor.newInstance();
                if (device.connect()) {
                    return device;
                }
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        throw new RuntimeException("No oscilloscope connected!");
    }

    public abstract boolean connect();
    public abstract void setup();
    public abstract void startMeasuring();
    public abstract void stopDevice();
    public abstract void store(Path file, int cutOffFrequency) throws IOException;
    public abstract Trace getTrace(int cutOffFrequency);
    public abstract void finish();
}
