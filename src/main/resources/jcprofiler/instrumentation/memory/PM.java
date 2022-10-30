package @PACKAGE@;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

public class PM {
    private static final short ARRAY_LENGTH = 0;
    private static final short MAX_APDU_LENGTH = 256;

    // Arrays storing the memory consumption for given trap
    public static final byte[] memoryUsageTransientDeselect = new byte[ARRAY_LENGTH];
    public static final byte[] memoryUsageTransientReset = new byte[ARRAY_LENGTH];
    public static final byte[] memoryUsagePersistent = new byte[ARRAY_LENGTH];

    // Store usage info for given trap
    public static void check(short stopCondition) {
        short trapID = (short) ((stopCondition - /* PERF_START */ 2) * Short.BYTES);

        Util.setShort(memoryUsageTransientDeselect, trapID, JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT));
        Util.setShort(memoryUsageTransientReset, trapID, JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_TRANSIENT_RESET));
        Util.setShort(memoryUsagePersistent, trapID, JCSystem.getAvailableMemory(JCSystem.MEMORY_TYPE_PERSISTENT));
    }

    // Prepare and send the given part of data
    private static void sendArray(byte[] arr, APDU apdu) {
        short part = (short) (apdu.getBuffer()[ISO7816.OFFSET_P2] & 0x00FF);
        short beginOffset = (short) (part * MAX_APDU_LENGTH);

        if (beginOffset > ARRAY_LENGTH)
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);

        short remainingLength = (short) (ARRAY_LENGTH - beginOffset);
        short length = remainingLength > MAX_APDU_LENGTH ? MAX_APDU_LENGTH : remainingLength;

        Util.arrayCopyNonAtomic(arr, beginOffset, apdu.getBuffer(), (short) 0, length);
        apdu.setOutgoingAndSend((short) 0, length);
    }

    // Send usage info back to the profiler
    public static void send(APDU apdu) {
        switch (apdu.getBuffer()[ISO7816.OFFSET_P1]) {
            case JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT:
                sendArray(memoryUsageTransientDeselect, apdu);
                break;
            case JCSystem.MEMORY_TYPE_TRANSIENT_RESET:
                sendArray(memoryUsageTransientReset, apdu);
                break;
            case JCSystem.MEMORY_TYPE_PERSISTENT:
                sendArray(memoryUsagePersistent, apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }
}