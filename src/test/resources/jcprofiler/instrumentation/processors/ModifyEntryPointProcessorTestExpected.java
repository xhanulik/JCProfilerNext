import javacard.framework.*;

public class Example extends Applet {
    public static final byte INS_PERF_SETSTOP = (byte) 0xF5;
    public static final byte INS_EXAMPLE = (byte) 0xEE;

    Example() {}

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new Example().register();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet())
            return;

        switch (apdu.getBuffer()[ISO7816.OFFSET_INS]) {
            case INS_PERF_SETSTOP:
                PM.m_perfStop = Util.getShort(apdu.getBuffer(), ISO7816.OFFSET_CDATA);
                break;
            case INS_EXAMPLE:
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}