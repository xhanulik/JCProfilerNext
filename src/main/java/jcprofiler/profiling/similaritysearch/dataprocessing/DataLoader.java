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

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import jcprofiler.profiling.similaritysearch.models.Trace;

import java.io.*;

/**
 * Class contains static helper methods for data loading from .csv files.
 * 
 * @author Martin Podhora
 */
class DataLoader {
    /**
     * Last header line of .csv file.
     */
    private static final int DATA_STARTING_LINE = 3;   
    
    /**
     * Helper static method used for counting all lines in .csv file.
     * 
     * @param filePath path to the CSV file
     * @return number of lines
     * @throws IOException file error
     */
    private static int countLinesInCsv(String filePath) throws IOException {
        try (InputStream is = new BufferedInputStream(new FileInputStream(filePath))) {
            byte[] c = new byte[1024];
            int count = 0;
            int readChars;
            boolean empty = true;
            while ((readChars = is.read(c)) != -1) {
                empty = false;
                for (int i = 0; i < readChars; ++i) {
                    if (c[i] == '\n') {
                        ++count;
                    }
                }
            }
            return (count == 0 && !empty) ? 1 : count;
        }
    }
    
    /**
     * Imports data from csv file to class trace
     * 
     * @param filePath file to be imported
     * @param timeColumn number of column with time
     * @param voltageColumn number of column with voltage
     * @return created trace object
     * @throws FileNotFoundException
     * @throws IOException 
     */
    public static Trace importFromCsv(String filePath, int timeColumn, int voltageColumn, boolean notSkipping) throws FileNotFoundException, IOException {
        String[] csvRow;
        int linesInCsv = countLinesInCsv(filePath) - DATA_STARTING_LINE;                                // need to substract non-data lines
        int sizeNeededForArray = notSkipping ? linesInCsv : linesInCsv / DataManager.SKIPPING_CONSTANT; // decide whether skipping or not and if yes then divide number of lines by skipping constant
        int addDataPositionIndex = 0;
        int allCyclesCounter = 0;
        String timeUnit;
        String voltageUnit;
        double timeValue;
        double voltageValue;
        
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath))) {
            try (CSVReader csvReader = new CSVReader(bufferedReader)) {
                csvRow = csvReader.readNext();
                if (csvRow.length < timeColumn && csvRow.length < voltageColumn) 
                    throw new IOException("Invalid format of CSV file.");
                csvRow = csvReader.readNext();
                timeUnit = csvRow[timeColumn];
                voltageUnit = csvRow[voltageColumn];
                csvReader.skip(1);

                Trace trace = new Trace(sizeNeededForArray, voltageUnit, timeUnit);

                while ((csvRow = csvReader.readNext()) != null && addDataPositionIndex < sizeNeededForArray) {
                    if (notSkipping || !DataManager.skipFunction(allCyclesCounter)) {
                        timeValue = Double.parseDouble(csvRow[timeColumn]);
                        voltageValue = Double.parseDouble(csvRow[voltageColumn]);
                        trace.addData(voltageValue, timeValue, addDataPositionIndex);
                        addDataPositionIndex++;
                    }
                    allCyclesCounter++;
                }
                return trace;    
            } catch (CsvValidationException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
