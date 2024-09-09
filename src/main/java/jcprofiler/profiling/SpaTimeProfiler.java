// SPDX-FileCopyrightText: 2017-2021 Petr Švenda <petrsgit@gmail.com>
// SPDX-FileCopyrightText: 2022 Lukáš Zaoral <x456487@fi.muni.cz>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import cz.muni.fi.crocs.rcard.client.CardManager;
import cz.muni.fi.crocs.rcard.client.Util;
import jcprofiler.args.Args;
import jcprofiler.profiling.oscilloscope.AbstractOscilloscope;
import jcprofiler.profiling.similaritysearch.SimilaritySearchController;
import jcprofiler.profiling.similaritysearch.dataprocessing.DataManager;
import jcprofiler.profiling.similaritysearch.models.Boundaries;
import jcprofiler.profiling.similaritysearch.models.Trace;
import jcprofiler.profiling.similaritysearch.Similarity;
import jcprofiler.util.JCProfilerUtil;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.CtModel;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * This class represents the specifics of profiling in SPA time mode.
 *
 * @author Veronika Hanulíková
 */
public class SpaTimeProfiler extends AbstractProfiler {
    // use LinkedHashX to preserve insertion order
    private final Map<String, List<Long>> measurements = new LinkedHashMap<>();
    private static Trace delimiterTrace = null;
    String traceFile = "trace";
    Path subtracesDirectory = null;
    int delimiterNum = trapNameMap.size();

    int successfulExtractions = 0;

    private static final Logger log = LoggerFactory.getLogger(SpaTimeProfiler.class);

    AbstractOscilloscope oscilloscope;

    /**
     * Constructs the {@link SpaTimeProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param cardManager applet connection instance
     * @param model       Spoon model
     */
    public SpaTimeProfiler(final Args args, final CardManager cardManager, final CtModel model) {
        super(args, cardManager, JCProfilerUtil.getProfiledMethod(model, args.executable), null);
    }

    /**
     * Run oscillocope.
     *
     * @throws RuntimeException if some measurements are missing
     */
    @Override
    protected void profileImpl() {
        try {
            // reset if possible and erase any previous performance stop
            resetApplet();

            // generate profiling inputs
            generateInputs(args.repeatCount);

            // find and prepare oscilloscope
            oscilloscope = AbstractOscilloscope.create();
            oscilloscope.setup();

            if (args.saveSubtraces) {
                // create director for subtraces
                subtracesDirectory = args.traceDir.resolve("subtracesDirectory");
            }

           // load delimiter trace
            delimiterTrace = DataManager.loadTrace(args.delimiterFile.toAbsolutePath().toString(), true);

            for (int round = 1; round <= args.repeatCount; round++) {
                final CommandAPDU triggerAPDU = getInputAPDU(round);

                final String input = Util.bytesToHex(triggerAPDU.getBytes());
                log.info("Round: {}/{} APDU: {}", round, args.repeatCount, input);

                // create file for storing the final trace
                Path traceFilePath = args.traceDir.resolve(traceFile + "_" + round + ".csv");
                // run operation and oscilloscope
                profileSingleStep(triggerAPDU, traceFilePath);
                // parse trace for times
                if (extractTimes(traceFilePath, round) != 0) {
                    successfulExtractions += 1;
                    // extraction failed, all measurements for given trigger APDU is null
                    for (short trapID : trapNameMap.keySet()) {
                        measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(0L);
                    }
                }
            }
            oscilloscope.finish();

        } catch (CardException | InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Collecting measurements complete.");
    }

    /**
     * Performs a single time profiling step.  Executes the given APDU and stores the elapsed time.
     *
     * @param  triggerAPDU APDU to reach the selected fatal trap
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if setting the next fatal performance trap failed
     */
    private void profileSingleStep(CommandAPDU triggerAPDU, Path tracePath) throws CardException {
        oscilloscope.startMeasuring();

        // send profiled APDu to card
        final ResponseAPDU response = cardManager.transmit(triggerAPDU);

        // stored measured data
        try {
            oscilloscope.store(tracePath);
        } catch (Exception e) {
            throw new RuntimeException("Storage of profiled data unsuccessfull!");
        }

        // test response from card
        final int SW = response.getSW();
        if (SW != JCProfilerUtil.SW_NO_ERROR) {
            throw new RuntimeException("Unexpected SW received when profiling: %s");
        }
        log.debug("Collecting measurement complete.");
        resetApplet();
    }

    private short getTrapID(int index) {
        for (short trapID : trapNameMap.keySet()) {
            if (index == 0) {
                return trapID;
            }
            index--;
        }
        return -1;
    }

    private int extractTimes(Path traceFilePath, int round) throws IOException, InterruptedException {
        // load trace from CSV file into inner representation
        Trace operationTrace = DataManager.loadTrace(traceFilePath.toAbsolutePath().toString(), false);

        // perform similarity search
        log.debug("Starting trace extraction");
        SortedSet<Similarity> similarities = SimilaritySearchController.searchTraceForOperation(operationTrace, delimiterTrace,
                SimilaritySearchController.MANHATTAN_DISTANCE_ALGORITHM, 24);
        // test number of found similarities
        if (similarities.isEmpty() || similarities.size() != delimiterNum * args.delimiterPatternNum) {
            log.error("Unexpected number of delimiters found (expected %d, found %d)", delimiterNum, similarities.size());
            log.error("Skipping trace");
            return 1;
        }

        // convert into boundaries set
        List<Boundaries> similaritiesBoundaries = new ArrayList<>();
        similarities.forEach((similarity) ->
                similaritiesBoundaries.add(
                        new Boundaries(operationTrace.getTimeOnPosition(similarity.getFirstIndex())
                                , operationTrace.getTimeOnPosition(similarity.getLastIndex())
                                , similarity.getFirstIndex()
                                , similarity.getLastIndex())));
        Collections.sort(similaritiesBoundaries);

        // go over triples and extract times between them
        int numberOfSubtrace = 0; // for storing purposes
        short trapID = getTrapID(numberOfSubtrace);
        measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(null);
        for (int delIndex = 0; delIndex < similaritiesBoundaries.size(); delIndex++) {
            if (delIndex % args.delimiterPatternNum == 0 && delIndex != 0) {
                // get time between this and previous triple
                Boundaries startDelimiter = similaritiesBoundaries.get(delIndex - 1);
                Boundaries endDelimiter = similaritiesBoundaries.get(delIndex);
                long elapsedTime = endDelimiter.getLowerBoundNano() - startDelimiter.getUpperBoundNano();
                System.out.printf("Operation length (%d -> %d): %d ns\n", delIndex - 1, delIndex, elapsedTime);
                numberOfSubtrace++;

                // store time for given trapID
                trapID = getTrapID(numberOfSubtrace);
                log.info("Duration: {} ns", elapsedTime);
                measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(elapsedTime);

                // save CSV for subtrace
                if (args.saveSubtraces) {
                    try {
                        Files.createDirectories(subtracesDirectory);
                    } catch (IOException e) {
                        System.out.println("Failed to create the directory: " + e.getMessage());
                    }
                    // adjust main trace file name
                    Path currentSubtracePath = subtracesDirectory.resolve(traceFile + "_" + round + "_" + numberOfSubtrace + ".csv");
                    // save subtrace
                    DataManager.saveTrace(currentSubtracePath.toAbsolutePath().toString(),
                            operationTrace, startDelimiter.getLastIndex(), endDelimiter.getFirstIndex());
                }
            }
            System.out.printf("%f - %f\n", similaritiesBoundaries.get(delIndex).getLowerBound(), similaritiesBoundaries.get(delIndex).getUpperBound());
        }
        return 0;
    }

    /**
     * Stores the time measurements using given {@link CSVPrinter} instance.
     *
     * @param  printer instance of the CSV printer
     *
     * @throws IOException if the printing fails
     */
    @Override
    protected void saveMeasurements(final CSVPrinter printer) throws IOException {
        printer.printComment("trapName,measurement1,measurement2,...");
        for (final Map.Entry<String, List<Long>> e : measurements.entrySet()) {
            printer.print(e.getKey());
            printer.printRecord(e.getValue());
        }
    }
}
