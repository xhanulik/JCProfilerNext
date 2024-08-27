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


package jcprofiler.profiling.similaritysearch.dataprocessing;

import com.opencsv.CSVWriter;
import jcprofiler.profiling.similaritysearch.models.Trace;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Class contains static helper methods for data saving to .csv files.
 * 
 * @author Martin Podhora
 */
class DataSaver {
    /**
     * Last header line of .csv file.
     */
    private static final int DATA_STARTING_LINE = 3;
    

    /**
     * Exports specified interval of data from trace to .csv file.
     * 
     * @param trace
     * @param dataPath
     * @param firstIndex
     * @param lastIndex
     * @throws IOException 
     */
    public static void exportToCsv(Trace trace, String dataPath, int firstIndex, int lastIndex) throws IOException {
        privateExportToCsv(trace, dataPath, firstIndex, lastIndex);
    }
    
    /**
     * More general helper method.
     * 
     * @param trace
     * @param dataPath
     * @param firstIndex
     * @param lastIndex
     * @throws IOException 
     */
    private static void privateExportToCsv(Trace trace, String dataPath, int firstIndex, int lastIndex) throws IOException {
        String[] csvRow = new String[2];
        
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(dataPath))) {
            try (CSVWriter csvWriter = new CSVWriter(bw)) {
                csvRow[0] = "Time";
                csvRow[1] = "Voltage";
                csvWriter.writeNext(csvRow);
                csvRow[0] = trace.getTimeUnit();
                csvRow[1] = trace.getVoltageUnit();
                csvWriter.writeNext(csvRow);
                csvWriter.writeNext(new String[2]);
                for (int i = firstIndex; i < lastIndex; i++) {
                    csvRow[0] = String.valueOf(trace.getTimeOnPosition(i));
                    csvRow[1] = String.valueOf(trace.getVoltageOnPosition(i));
                    csvWriter.writeNext(csvRow);
                }
            }
        }
    }
}
