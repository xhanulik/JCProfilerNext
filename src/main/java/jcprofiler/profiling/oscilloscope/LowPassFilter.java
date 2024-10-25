package jcprofiler.profiling.oscilloscope;

import uk.me.berndporr.iirj.Butterworth;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that represents low-pass filter.
 * It is used to filter voltage array of the trace.
 * 
 * @author Martin Podhora
 */
public class LowPassFilter {
    public int samplingFrequency;
    public int cutOffFrequency;
    public static final int ORDER = 1;

    Butterworth butterworth;

    /**
     * Constructor
     *
     * @param samplingFreq  Sampling frequency in Hz
     * @param cutOffFreq    Cut-off frequency in Hz
     */
    public LowPassFilter(int samplingFreq, int cutOffFreq) {
        butterworth = new Butterworth();
        butterworth.lowPass(ORDER, samplingFreq, cutOffFreq);
    }

    public double applyLowPassFilter(double value) {
        return butterworth.filter(value);
    }
    public void applyLowPassFilter(String inputFilePath, String outputFilePath) {
        List<Double> timeValues = new ArrayList<>();
        List<Double> voltageValues = new ArrayList<>();
        List<Double> filteredVoltages = new ArrayList<>();

        // Read the CSV file and store time and voltage values
        int num = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {

            String line;
            while ((line = br.readLine()) != null) {
                num++;
                if (num < 4) {
                    continue;
                }
                String[] values = line.split(",");
                if (values.length == 2) {
                    try {
                        double time = Double.parseDouble(values[0]);
                        double voltage = Double.parseDouble(values[1]);
                        timeValues.add(time);
                        voltageValues.add(voltage);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid data at line: " + line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        for (double voltage : voltageValues) {
            double filteredValue = butterworth.filter(voltage);
            filteredVoltages.add(filteredValue);
        }

        // Save the filtered data back to a new CSV file
        try (FileWriter writer = new FileWriter(outputFilePath)) {
            writer.write("Time,Voltage\n(ms),(V)\n,\n");  // Write the header
            for (int i = 0; i < timeValues.size(); i++) {
                writer.write(timeValues.get(i) + "," + filteredVoltages.get(i) + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
