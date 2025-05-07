// SPDX-FileCopyrightText: 2022 Lukáš Zaoral <x456487@fi.muni.cz>
// SPDX-FileCopyrightText: 2023 Antonín Dufka <xhanulik@gmail.com>
// SPDX-FileCopyrightText: 2025 Veronika Hanulíková <xhanulik@gmail.com>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import cz.muni.fi.crocs.rcard.client.Util;
import jcprofiler.args.Args;
import jcprofiler.card.Leia.TargetController;
import jcprofiler.profiling.oscilloscope.AbstractOscilloscope;
import jcprofiler.profiling.similaritysearch.SimilaritySearchController;
import jcprofiler.profiling.similaritysearch.dataprocessing.DataManager;
import jcprofiler.profiling.similaritysearch.models.Boundaries;
import jcprofiler.profiling.similaritysearch.models.Trace;
import jcprofiler.profiling.similaritysearch.Similarity;
import jcprofiler.util.JCProfilerUtil;
import org.apache.commons.csv.CSVPrinter;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.CtModel;

import javax.smartcardio.CardException;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
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
    Path subtracesDirectory = null;
    int delimiterNum = trapNameMap.size();

    public int CARD = 1;
    private static int STAGE = 2;
    private static int parties = 2;
    private static int threshold = 2;

    private static final Logger log = LoggerFactory.getLogger(SpaTimeProfiler.class);

    AbstractOscilloscope oscilloscope;

    TargetController target;

    /**
     * Constructs the {@link SpaTimeProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param model       Spoon model
     */
    public SpaTimeProfiler(final Args args, TargetController targetController, final CtModel model) {
        super(args, null, targetController, JCProfilerUtil.getProfiledMethod(model, args.executable), null);
    }


    /**
     * @author: Antonín Dufka
     */
    public byte[] recodePoint(byte[] point) {
        Security.addProvider(new BouncyCastleProvider());
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        return spec.getCurve().decodePoint(point).getEncoded(false); // TODO change if should use compressed points
    }

    /**
     * @author: Antonín Dufka
     */
    public static byte[] randECPoint() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        ECParameterSpec ecSpec_named = ECNamedCurveTable.getParameterSpec("secp256k1");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", "BC");
        kpg.initialize(ecSpec_named);
        KeyPair pair = kpg.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        return pub.getQ().getEncoded(true);
    }

    /**
     * @author: Antonín Dufka
     */
    public static byte[] randSecret() {
        Random rng = new Random();
        byte[] buffer = new byte[32];
        rng.nextBytes(buffer);
        return buffer;
    }

    /**
     * @author: Antonín Dufka
     */
    public static int[] randParticipants(int include, int size, int max) {
        ArrayList<Integer> all = new ArrayList<Integer>();
        for(int i = 0; i < max; ++i) {
            if (i + 1 == include) {
                continue;
            }
            all.add(i + 1);
        }
        Collections.shuffle(all);
        ArrayList<Integer> result = new ArrayList<Integer>();
        result.add(include);
        for(int i = 1; i < size; ++i) {
            result.add(all.get(i - 1));
        }
        Collections.sort(result);

        int[] output = new int[size];
        for(int i = 0; i < size; ++i) {
            output[i] = result.get(i);
        }
        return output;
    }


    /**
     * Run oscillocope.
     *
     * @throws RuntimeException if some measurements are missing
     */
    @Override
    protected void profileImpl() {
        int unsuccessfulMeasurements = 0;
        try {
            // prepare target LEIA controller
            targetController.resetTriggerStrategy();

            // find and prepare oscilloscope
            oscilloscope = AbstractOscilloscope.create(args);
            oscilloscope.setup();
            if (args.traceDir != null) {
                // create director for subtraces
                subtracesDirectory = args.traceDir.resolve("subtracesDirectory");
            }

            resetApplet();

            // generate profiling inputs
            generateInputs(args.repeatCount);
            generateAuxiliaryInputs();

           // load delimiter trace
            delimiterTrace = DataManager.loadTrace(args.delimiterFile.toAbsolutePath().toString(), true);

            for (int round = 1; round <= args.repeatCount; round++) {
                // run multiple APDU before measuring, if specified
                targetController.resetTriggerStrategy();
                sendAuxiliaryInputs(round);

                CommandAPDU initAPDU = new CommandAPDU(0, 0, 0, 0);
                log.info("APDU:{} ", Hex.encode(initAPDU.getBytes()));
                ResponseAPDU response = targetController.sendAPDU(initAPDU);
                log.info("RESP: {}", Hex.toHexString(response.getData()));

                // choose arbitrary index
                CARD = 1;
                System.out.printf("Card index %d\n", CARD);

                // setup random secret either from arguments or generate random when not supplied
                byte[] secret = randSecret();
                byte[] randPoint = randECPoint();

                System.out.printf("Secret share for card %d: %s\n", CARD, new String(Hex.encode(secret)));
                System.out.printf("Public point %s\n", new String(Hex.encode(randPoint)));
                byte[] point = recodePoint(randPoint);
                System.out.printf("(Public group key encoded %s\n", new String(Hex.encode(point)));

                // Send secret and point to the card
                CommandAPDU setupAPDU = new CommandAPDU(0, 1, threshold, parties, Util.concat(new byte[]{(byte) CARD}, secret, point));
                log.info("APDU:{} ", Hex.encode(setupAPDU.getBytes()));
                response = targetController.sendAPDU(setupAPDU);
                log.info("RESP: {}", Hex.toHexString(response.getData()));

                switch (STAGE) {
                    case 1:
                        // profiling COMMIT: INS = 0x02
                        break;
                    case 2:
                        prepareSign();
                        // profiling COMMIT: INS = 0x07
                        break;
                }

                // get APDU which will be measured
                final CommandAPDU triggerAPDU = getInputAPDU(round);
                final String input = Util.bytesToHex(triggerAPDU.getBytes());
                log.info("Round: {}/{} APDU: {}", round, args.repeatCount, input);

                // run operation and oscilloscope measuring
                Trace trace = profileSingleStep(triggerAPDU);

                // trace is stored for now in CSV parse trace for times
                if (extractTimes(trace, round) != 0) {
                    // extraction failed, creating bogus 0
                    for (short trapID : trapNameMap.keySet()) {
                        measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(0L);
                    }
                    log.info("Measurements not saved");
                    unsuccessfulMeasurements++;
                }

                CommandAPDU resetAPDU = new CommandAPDU(0, 5, 0, 0);
                log.info("APDU:{} ", Hex.encode(resetAPDU.getBytes()));
                response = targetController.sendAPDU(resetAPDU);
                log.info("RESP: {}", Hex.toHexString(response.getData()));
            }
            // close connection to oscilloscope
            oscilloscope.finish();

        } catch (CardException | InterruptedException | IOException e) {
            if (oscilloscope != null)
                oscilloscope.finish();
            if (target != null)
                targetController.close();
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        log.info("Final number of measurements: {}", args.repeatCount - unsuccessfulMeasurements);
        log.info("Collecting measurements complete.");
    }

    private void prepareSign() throws Exception {
        // 1. Commit
        byte[] cardData;
        // randomness generation in here in JCProfilerNext and print out - only when DEBUG = true in JCFROST.java
        // when no DEBUG, nonces are generated on the card and cannot be printed out
        byte[] hidingNonceRandomness = randSecret();
        byte[] bindingNonceRandomness = randSecret();
        assert(hidingNonceRandomness.length == 32 && bindingNonceRandomness.length == 32);
        System.out.printf("Card %d hiding randomness %s\n", CARD, new String(Hex.encode(hidingNonceRandomness)));
        System.out.printf("Card %d binding randomness %s\n", CARD, new String(Hex.encode(bindingNonceRandomness)));
        CommandAPDU rndSetAPDU = new CommandAPDU(0, 2, 64, 0, Util.concat(hidingNonceRandomness, bindingNonceRandomness));
        System.out.println(new String(Hex.encode(rndSetAPDU.getBytes())));
        ResponseAPDU response = targetController.sendAPDU(rndSetAPDU);
        if (response.getSW() != 0x9000) {
            System.out.printf("Card %d commit APDU failed %s\n", CARD, Util.bytesToHex(response.getBytes()));
        }
        cardData = response.getData();
        System.out.printf("Card %d commitments %s\n", CARD, new String(Hex.encode(cardData)));

        // 2. Commitments
        int[] participants = randParticipants(CARD, threshold, parties);
        for (int identifier : participants) {
            // setup commitments of this card
            byte[] hiding = Arrays.copyOfRange(cardData, 0, 33);
            byte[] binding = Arrays.copyOfRange(cardData, 33, 66);
            if (identifier != CARD) { // setup commitments send from other parties
                hiding = randECPoint();
                binding = randECPoint();
            }
            // print commitment in hex
            System.out.printf("Card %d hiding commitment (public) %s\n", identifier, new String(Hex.encode(hiding)));
            System.out.printf("Card %d binding commitment (public) %s\n", identifier, new String(Hex.encode(binding)));
            System.out.printf("Card %d sends public commitments to %d: %s\n", identifier, CARD,
                    new String(Hex.encode(Util.concat(recodePoint(hiding), recodePoint(binding)))));
            CommandAPDU commitmentAPDU = new CommandAPDU(0, 3, identifier, 0, Util.concat(recodePoint(hiding), recodePoint(binding)));
            System.out.println(new String(Hex.encode(commitmentAPDU.getBytes())));
            response = targetController.sendAPDU (commitmentAPDU);
            if (response.getSW() != 0x9000) {
                System.out.printf("Card %d commitment APDU failed %s\n", CARD, Util.bytesToHex(response.getBytes()));
            }
        }
    }

    /**
     * Performs a single time profiling step.  Executes the given APDU and stores the elapsed time.
     *
     * @param triggerAPDU APDU to reach the selected fatal trap
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if setting the next fatal performance trap failed
     */
    private Trace profileSingleStep(CommandAPDU triggerAPDU) throws CardException {
        int SW;
        if (STAGE == 2) {
            // send one more APDU with message for JCFROST profiling version
            CommandAPDU preSign = new CommandAPDU(triggerAPDU.getCLA(), (byte) 0x04, triggerAPDU.getP1(), triggerAPDU.getP2(), triggerAPDU.getBytes());
            log.info("APDU: {}", preSign);
            ResponseAPDU preResponse = targetController.sendAPDU(preSign);
            SW = preResponse.getSW();
            log.info("RESP: SW={}", String.format("%02X", preResponse.getSW1()) + String.format("%02X", preResponse.getSW2()));
            if (SW != JCProfilerUtil.SW_NO_ERROR) {
                throw new RuntimeException("Unexpected SW received when profiling: " + SW);
            }
        }

        // set pres-send APDU trigger strategy
        targetController.setPreSendAPDUTriggerStrategy();

        // start measuring on oscilloscope
        oscilloscope.startMeasuring();

        // send profiled APDU to card
        ResponseAPDU response = targetController.sendAPDU(triggerAPDU);
        log.info("RESPONSE: {}", Hex.toHexString(response.getData()));
        // stored measured data into CSV
        Trace trace;
        try {
            trace = oscilloscope.getTrace(args.cutOffFrequency);
        } catch (Exception e) {
            throw new RuntimeException("Storage of profiled data unsuccessful!");
        }

        // test response from card
        SW = response.getSW();
        if (SW != JCProfilerUtil.SW_NO_ERROR) {
            throw new RuntimeException("Unexpected SW received when profiling: " + SW);
        }
        log.debug("Collecting measurement complete.");
        resetApplet();

        return trace;
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

    private int extractTimes(Trace operationTrace , int round) throws IOException, InterruptedException {
        // Save trace
        if (args.traceDir != null) {
            // adjust main trace file name
            Path currentTracePath = args.traceDir.resolve("trace_" + round + ".csv");
            DataManager.saveTrace(currentTracePath.toAbsolutePath().toString(),
                    operationTrace, 0, operationTrace.getDataCount() - 1);
            log.debug("Trace {} saved.", currentTracePath.getFileName());
        }

        // perform similarity search
        log.debug("Starting trace extraction");
        int totalNumSum = delimiterNum * args.delimiterPatternNum;
        log.debug("Searching for {} similarities", totalNumSum);
        SortedSet<Similarity> similarities = SimilaritySearchController.searchTraceForOperation(operationTrace, delimiterTrace,
                SimilaritySearchController.MANHATTAN_DISTANCE_ALGORITHM, totalNumSum);
        // test number of found similarities
        if (similarities.isEmpty() || similarities.size() != totalNumSum) {
            log.error("Unexpected number of delimiters found (expected {}, found {})", totalNumSum, similarities.size());
            log.error("Skipping trace");
            return 1;
        }
        log.debug("{} similarities extracted successfuly", totalNumSum);

        // convert into boundaries set
        List<Boundaries> similaritiesBoundaries = new ArrayList<>();
        similarities.forEach((similarity) ->
                similaritiesBoundaries.add(
                        new Boundaries(operationTrace.getTimeOnPosition(similarity.getFirstIndex())
                                , operationTrace.getTimeOnPosition(similarity.getLastIndex())
                                , similarity.getFirstIndex()
                                , similarity.getLastIndex())));
        Collections.sort(similaritiesBoundaries);

        // create subtrace directory
        if (args.traceDir != null) {
            try {
                Files.createDirectories(subtracesDirectory);
            } catch (IOException e) {
                log.error("Failed to create the directory for subtraces: " + e.getMessage());
                return 1;
            }
        }

        // go over triples and extract times between them
        int numberOfSubtrace = 0; // for storing purposes
        measurements.computeIfAbsent(getTrapName(getTrapID(0)), k -> new ArrayList<>()).add(0L);
        log.debug("Computing times");
        for (int delIndex = 1; delIndex < similaritiesBoundaries.size(); delIndex++) {
            // get time between this and previous delimiter
            Boundaries startDelimiter = similaritiesBoundaries.get(delIndex - 1);
            Boundaries endDelimiter = similaritiesBoundaries.get(delIndex);
            long elapsedTime = (long) (endDelimiter.getLowerBound() - startDelimiter.getUpperBound());

            // after full delimiter
            if (delIndex % args.delimiterPatternNum == 0) {
                numberOfSubtrace++;

                // store time for given trapID
                short trapID = getTrapID(numberOfSubtrace);
                log.debug("Trap ID {} duration: {} ns", trapID, elapsedTime);
                measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(elapsedTime);

                // save CSV for subtrace
                if (args.traceDir != null) {
                    // adjust main trace file name
                    Path currentSubtracePath = subtracesDirectory.resolve("trace_" + round + "_" + numberOfSubtrace + ".csv");
                    // save subtrace
                    DataManager.saveTrace(currentSubtracePath.toAbsolutePath().toString(),
                            operationTrace, startDelimiter.getLastIndex(), endDelimiter.getFirstIndex());
                    log.debug("Subtrace {} saved.", currentSubtracePath.getFileName());
                }
            } else {
                log.debug("Time in-between delimiter patterns: {} ns", elapsedTime);
                if (args.patternDistance >= 0 && elapsedTime > args.patternDistance) {
                    log.error("Unexpected time between delimiter patterns (expected max {}, found {})", args.patternDistance, elapsedTime);
                    log.error("Skipping trace");
                    return 1;
                }
            }
        }
        log.info("Trace extraction finished successfully");
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
