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

import jcprofiler.profiling.similaritysearch.models.Trace;

import java.io.File;
import java.io.IOException;

/**
 * This class contains many utility methods used for data loading and saving.
 * HashMap will in future be replaced with resource file.
 * 
 * @author Martin Podhora
 */
public class DataManager {
    /**
     * Column of time values in .csv file.
     */
    public static final int DEFAULT_TIME_COLUMN = 0;
    
    /**
     * Column of voltage in .csv file.
     */
    public static final int DEFAULT_VOLTAGE_COLUMN = 1;
    public static final int SKIPPING_CONSTANT = 2;

    private static String getFileExtension(File file) {
        String fileName = file.getName();
        if(fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
        return fileName.substring(fileName.lastIndexOf(".")+1);
        else return "";
    }
    
    public static Trace loadTrace(String filePath, boolean notSkipping) throws IOException {
        return DataLoader.importFromCsv(filePath, DEFAULT_TIME_COLUMN, DEFAULT_VOLTAGE_COLUMN, notSkipping);
    }
    /**
     * Returns true when to skip, and false otherwise
     * @param indexInOriginalArray
     * @return 
     */
    public static boolean skipFunction(int indexInOriginalArray) {
        return indexInOriginalArray % SKIPPING_CONSTANT != 0;
    }
    
    /**
     * Method used to save data to file specified in @param dataPath between two indices.
     * 
     * @param filePath
     * @param trace
     * @param firstIndex
     * @param lastIndex
     * @throws IOException 
     */
    public static void saveTrace(String filePath, Trace trace, int firstIndex, int lastIndex) throws IOException {
        DataSaver.exportToCsv(trace, filePath, firstIndex, lastIndex);
    }
}
