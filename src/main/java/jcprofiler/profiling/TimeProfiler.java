// SPDX-FileCopyrightText: 2017-2021 Petr Švenda <petrsgit@gmail.com>
// SPDX-FileCopyrightText: 2022 Lukáš Zaoral <x456487@fi.muni.cz>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.profiling;

import cz.muni.fi.crocs.rcard.client.CardManager;
import cz.muni.fi.crocs.rcard.client.Util;
import jcprofiler.args.Args;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.*;

/**
 * This class represents the specifics of profiling in time mode.
 *
 * @author Lukáš Zaoral and Petr Švenda
 */
public class TimeProfiler extends AbstractProfiler {
    // use LinkedHashX to preserve insertion order
    private final Map<String, List<Long>> measurements = new LinkedHashMap<>();

    private static final Logger log = LoggerFactory.getLogger(TimeProfiler.class);

    /**
     * Constructs the {@link TimeProfiler} class.
     *
     * @param args        object with commandline arguments
     * @param cardManager applet connection instance
     * @param model       Spoon model
     */
    public TimeProfiler(final Args args, final CardManager cardManager, final CtModel model) {
        super(args, cardManager, JCProfilerUtil.getProfiledMethod(model, args.executable),
              /* customInsField */ "INS_PERF_SETSTOP");
    }

    public byte[] recodePoint(byte[] point) {
        Security.addProvider(new BouncyCastleProvider());
        ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("secp256k1");
        return spec.getCurve().decodePoint(point).getEncoded(false); // TODO change if should use compressed points
    }

    public int CARD = 1;

    /**
     * Utility function which will generate random valid ECPoint
     *
     * @return ECPoint
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

    public static byte[] randSecret() {
        Random rng = new Random();
        byte[] buffer = new byte[32];
        rng.nextBytes(buffer);
        return buffer;
    }

    public static int randIndex(int max) {
        Random rng = new Random();
        return Math.abs((rng.nextInt() % max)) + 1;
    }

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
     * Measures the elapsed time.
     *
     * @throws RuntimeException if some measurements are missing
     */
    @Override
    protected void profileImpl() {
        try {
            // reset if possible and erase any previous performance stop
            resetApplet();

            // initialize with INS instruction
            cardManager.transmit(new CommandAPDU(0, 0, 0, 0));

            // main profiling loop
            setTrap(PERF_START);
            generateInputs(args.repeatCount);
            for (int round = 1; round <= args.repeatCount; round++) {
                // choose either random index or given from arguments
                CARD = args.cardIndex == -1 ? randIndex(args.parties) : args.cardIndex;
                System.out.printf("Card index %d\n", CARD);

                // setup secret either from arguments or generate random when not supplied
                byte[] secret = args.secret != null ? args.secret : randSecret();
                byte[] randPoint = args.point != null ? args.point : randECPoint();

                System.out.printf("Secret share for card %d: %s\n", CARD, new String(Hex.encode(secret)));
                System.out.printf("Public point %s\n", new String(Hex.encode(randPoint)));
                byte[] point = recodePoint(randPoint);
                System.out.printf("(Public group key encoded %s\n", new String(Hex.encode(point)));

                // Send secret and point to the card
                CommandAPDU setupAPDU = new CommandAPDU(0, 1, args.threshold, args.parties, Util.concat(new byte[]{(byte) CARD}, secret, point));
                System.out.println(new String(Hex.encode(setupAPDU.getBytes())));
                cardManager.transmit(setupAPDU);

                switch (args.stage) {
                    case 1: // commit
                        // a) profiling data should be 'commit' INS and APDU
                        // b) artificial sign (INS: 06)
                        // c) computing lambda and challenge (INS: 07)
                        break;
                    case 2: // sign
                        doSign();
                        break;
                }

                final CommandAPDU triggerAPDU = getInputAPDU(round);
                CommandAPDU preTriggerAPDU = new CommandAPDU(triggerAPDU.getCLA(), 0x04, 0x40, 0x00, triggerAPDU.getData());
                System.out.println(new String(Hex.encode(preTriggerAPDU.getBytes())));
                cardManager.transmit(preTriggerAPDU);
                final String input = Util.bytesToHex(triggerAPDU.getBytes());
                log.info("Round: {}/{} APDU: {}", round, args.repeatCount, input);
                profileSingleStep(triggerAPDU);
            }

            // sanity check
            log.debug("Checking that no measurements are missing.");
            measurements.forEach((k, v) -> {
                if (v.size() != args.repeatCount)
                    throw new RuntimeException(k + ".size() != " + args.repeatCount);
            });
            if (inputs.size() != args.repeatCount)
                throw new RuntimeException("inputs.size() != " + args.repeatCount);


        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        log.info("Collecting measurements complete.");
    }

    private void doSign() throws Exception {
        // commit
        byte[] cardData;
        if (args.hidingNonce != null && args.bindingNonce != null) {
            // set DEBUG randomness from command line arguments - only when DEBUG = true in JCFROST.java
            byte[] hidingNonceRandomness = args.hidingNonce;
            byte[] bindingNonceRandomness = args.bindingNonce;
            assert(hidingNonceRandomness.length == 32 && bindingNonceRandomness.length == 32);
            CommandAPDU rndSetAPDU = new CommandAPDU(0, 2, 64, 0, Util.concat(hidingNonceRandomness, bindingNonceRandomness));
            System.out.println(new String(Hex.encode(rndSetAPDU.getBytes())));
            ResponseAPDU response = cardManager.transmit(rndSetAPDU);
            if (response.getSW() != 0x9000) {
                System.out.printf("Card %d commit APDU failed %s\n", CARD, Util.bytesToHex(response.getBytes()));
            }
            cardData = response.getData();
        } else {
            // randomness generation in here in JCProfilerNext and print out - only when DEBUG = true in JCFROST.java
            // when no DEBUG, nonces are generated on the card and cannot be printed out
            byte[] hidingNonceRandomness = randSecret();
            byte[] bindingNonceRandomness = randSecret();
            assert(hidingNonceRandomness.length == 32 && bindingNonceRandomness.length == 32);
            System.out.printf("Card %d hiding randomness %s\n", CARD, new String(Hex.encode(hidingNonceRandomness)));
            System.out.printf("Card %d binding randomness %s\n", CARD, new String(Hex.encode(bindingNonceRandomness)));
            CommandAPDU rndSetAPDU = new CommandAPDU(0, 2, 64, 0, Util.concat(hidingNonceRandomness, bindingNonceRandomness));
            System.out.println(new String(Hex.encode(rndSetAPDU.getBytes())));
            ResponseAPDU response = cardManager.transmit(rndSetAPDU);
            if (response.getSW() != 0x9000) {
                System.out.printf("Card %d commit APDU failed %s\n", CARD, Util.bytesToHex(response.getBytes()));
            }
            cardData = response.getData();
        }
        System.out.printf("Card %d commitments %s\n", CARD, new String(Hex.encode(cardData)));

        // commitments
        int commitmentHidingIndex = 0;
        int commitmentBindingIndex = 0;
        int[] participants = args.participants != null ? args.participants : randParticipants(CARD, args.threshold, args.parties);
        for (int identifier : participants) {
            byte[] hiding = Arrays.copyOfRange(cardData, 0, 33);
            byte[] binding = Arrays.copyOfRange(cardData, 33, 66);
            if (identifier != CARD) { // setup commitments send from other parties
                if (args.hidingCommitments != null) { // if supplied as command line argument, setup directly
                    hiding = args.hidingCommitments[commitmentHidingIndex];
                    commitmentHidingIndex++;
                } else { // otherwise generate random
                    hiding = randECPoint();
                }

                if (args.bindingCommitments != null) {
                    binding = args.bindingCommitments[commitmentBindingIndex];
                    commitmentBindingIndex++;
                } else {
                    binding = randECPoint();
                }
            }
            // print commitment in hex
            System.out.printf("Card %d hiding commitment (public) %s\n", identifier, new String(Hex.encode(hiding)));
            System.out.printf("Card %d binding commitment (public) %s\n", identifier, new String(Hex.encode(binding)));
            System.out.printf("Card %d sends public commitments to %d: %s\n", identifier, CARD,
                    new String(Hex.encode(Util.concat(recodePoint(hiding), recodePoint(binding)))));
            CommandAPDU commitmentAPDU = new CommandAPDU(0, 3, identifier, 0, Util.concat(recodePoint(hiding), recodePoint(binding)));
            System.out.println(new String(Hex.encode(commitmentAPDU.getBytes())));
            ResponseAPDU response = cardManager.transmit(commitmentAPDU);
            if (response.getSW() != 0x9000) {
                System.out.printf("Card %d commitment APDU failed %s\n", CARD, Util.bytesToHex(response.getBytes()));
            }
        }
    }

    /**
     * Sets {@code jcprofiler.PM#nextPerfStop} to given performance trap ID.
     *
     * @param  trapID performance trap ID to be set
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if setting the next fatal performance trap failed
     */
    private void setTrap(short trapID) throws CardException {
        log.debug("Setting next trap to {}.", getTrapName(trapID));

        CommandAPDU setTrap = new CommandAPDU(args.cla, JCProfilerUtil.INS_PERF_HANDLER, 0, 0,
                                              Util.shortToByteArray(trapID));
        ResponseAPDU response = cardManager.transmit(setTrap);
        if (response.getSW() != JCProfilerUtil.SW_NO_ERROR)
            throw new RuntimeException(String.format(
                    "Setting \"%s\" trap failed with SW %s",
                    getTrapName(trapID), Integer.toHexString(response.getSW())));
    }

    /**
     * Performs a single time profiling step.  Executes the given APDU and stores the elapsed time.
     *
     * @param  triggerAPDU APDU to reach the selected fatal trap
     *
     * @throws CardException    if the card connection failed
     * @throws RuntimeException if setting the next fatal performance trap failed
     */
    private void profileSingleStep(CommandAPDU triggerAPDU) throws CardException {
        long prevTransmitDuration = 0;
        long currentTransmitDuration;

        for (short trapID : trapNameMap.keySet()) {
            // set performance trap
            setTrap(trapID);

            // execute target operation
            final String trapName = getTrapName(trapID);
            log.debug("Measuring {}.", trapName);
            final ResponseAPDU response = cardManager.transmit(triggerAPDU);
            if (args.output)
                System.out.printf("Response data %d: %s\n", response.getData().length, new String(Hex.encode(response.getData())));

            // SW should be equal to the trap ID
            final int SW = response.getSW();
            if (SW != Short.toUnsignedInt(trapID)) {
                // unknown SW returned
                if (SW != JCProfilerUtil.SW_NO_ERROR)
                    throw new RuntimeException(String.format(
                            "Unexpected SW received when profiling trap %s: %s", trapName, Integer.toHexString(SW)));

                // we have not reached expected performance trap
                unreachedTraps.add(trapName);
                measurements.computeIfAbsent(trapName, k -> new ArrayList<>()).add(null);
                log.debug("Duration: unreachable");
                continue;
            }

            // compute the difference
            currentTransmitDuration = cardManager.getLastTransmitTimeNano();
            final long diff = currentTransmitDuration - prevTransmitDuration;
            prevTransmitDuration = currentTransmitDuration;

            log.debug("Duration: {} ns", diff);

            // store the difference
            measurements.computeIfAbsent(getTrapName(trapID), k -> new ArrayList<>()).add(diff);

            // free memory after command
            resetApplet();
        }
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
