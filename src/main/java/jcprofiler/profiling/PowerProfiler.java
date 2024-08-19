// SPDX-FileCopyrightText: 2022 Lukáš Zaoral <x456487@fi.muni.cz>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cz.muni.fi.crocs.rcard.client.CardManager;
import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;
import org.apache.commons.csv.CSVPrinter;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * This class represents the specifics of profiling in custom mode.
 */
public class PowerProfiler extends AbstractProfiler {

    private static final Logger log = LoggerFactory.getLogger(PowerProfiler.class);
    /**
     * Constructs the {@link PowerProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param cardManager applet connection instance
     * @param model       Spoon model
     */
    public PowerProfiler(final Args args, final CardManager cardManager, final CtModel model) {
        super(args, cardManager, JCProfilerUtil.getProfiledExecutable(model, args.entryPoint, args.executable),
              /* customInsField */ null);
    }

    /**
     * Generates the list of inputs for power profiling.
     */
    @Override
    protected void profileImpl() {
        if (!(profiledExecutable instanceof CtConstructor))
            generateInputs(args.repeatCount);
        log.info("No actual profiling is performed.");
    }

    public static long moveDecimalPointRightAndRound(String s, int places, int decimalPlaces) {
        // Split the string at the decimal point
        String[] parts = s.split("\\.");

        // Concatenate the parts and keep track of the decimal point's new position
        String wholeNumberPart = parts[0];
        String decimalPart = parts.length > 1 ? parts[1] : "";
        String combined = wholeNumberPart + decimalPart;

        // Calculate the new position for the decimal point
        int newPosition = wholeNumberPart.length() + places;

        // Ensure the combined string has enough length to accommodate the new decimal position
        while (combined.length() < newPosition) {
            combined += "0";
        }

        // Insert the decimal point at the new position
        String result = combined.substring(0, newPosition) + "." + combined.substring(newPosition);

        // Convert to BigDecimal and round to the specified number of decimal places
        BigDecimal roundedNumber = new BigDecimal(result).setScale(decimalPlaces, RoundingMode.HALF_UP);

        return roundedNumber.longValue();
    }

    /**
     * Stores the measurements template using given {@link CSVPrinter} instance.
     *
     * @param  printer instance of the CSV printer
     *
     * @throws IOException if the printing fails
     */
    @Override
    protected void saveMeasurements(final CSVPrinter printer) throws IOException {
        log.info("Creating output CSV file");
        File directory = new File(args.tracesDirectory.toUri());
        File[] filesList = directory.listFiles();
        printer.printComment("trapName,measurement1,measurement2,...");

        int index = 0;
        for (final String trapName : trapNameMap.values()) {
            if (index == 0) {
                printer.printRecord(trapName, "");
                index++;
                continue;
            }
            File file = filesList[index - 1];
            long value = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String firstMeasurementLine = null;
                String currentLine;
                String lastMeasurementLine = null;
                br.readLine();
                br.readLine();
                br.readLine();
                firstMeasurementLine = br.readLine();
                lastMeasurementLine = firstMeasurementLine;
                while ((currentLine = br.readLine()) != null) {
                    lastMeasurementLine = currentLine;
                }
                long startTime = moveDecimalPointRightAndRound(firstMeasurementLine.split(",")[0], 6, 0);
                long finishTime = moveDecimalPointRightAndRound(lastMeasurementLine.split(",")[0], 6, 0);
                value = finishTime - startTime;

            } catch (IOException e) {
                e.printStackTrace();
            }
            printer.printRecord(trapName, value);
            index++;
        }
        log.info("CSV file created");
    }
}
