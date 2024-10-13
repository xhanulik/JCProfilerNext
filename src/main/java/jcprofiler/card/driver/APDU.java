package jcprofiler.card.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class APDU extends DataStructure {

    private byte cla;
    private byte ins;
    private byte p1;
    private byte p2;
    private short lc;
    private int le;
    private byte sendLe;
    private byte[] data;

    private final int MAX_APDU_PAYLOAD_SIZE = 16384;

    public APDU(byte cla, byte ins, byte p1, byte p2, int le, byte sendLe, byte[] data) {
        this.cla = cla;
        this.ins = ins;
        this.p1 = p1;
        this.p2 = p2;
        this.le = le;
        this.sendLe = sendLe;

        if (data == null) {
            this.data = new byte[0];
            this.lc = 0;
        } else {
            // copy only maximal portion of data
            this.data = Arrays.copyOf(data, Math.min(data.length, MAX_APDU_PAYLOAD_SIZE));
            this.lc = (short) data.length;
        }
    }

    public APDU(byte cla, byte ins, byte p1, byte p2, byte[] data) {
        this.cla = cla;
        this.ins = ins;
        this.p1 = p1;
        this.p2 = p2;
        this.le = 0;
        this.sendLe = 0;

        if (data == null) {
            this.data = new byte[0];
            this.lc = 0;
        } else {
            // copy only maximal portion of data
            this.data = Arrays.copyOf(data, Math.min(data.length, MAX_APDU_PAYLOAD_SIZE));
            this.lc = (short) data.length;
        }
    }

    public static byte[] hexStringToByteArray(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }

    /**
     * Only short APDU supported for now
     * @param apdu APDU represented as hey string
     */
    public APDU(String apdu) {
        byte[] bytes = hexStringToByteArray(apdu);
        if (bytes.length < 5) {
            throw new IllegalArgumentException("Error in decoding APDU buffer: too small");
        }
        this.cla = (byte) (bytes[0] & 0xFF);
        this.ins = (byte) (bytes[1] & 0xFF);
        this.p1 = (byte) (bytes[2] & 0xFF);
        this.p2 = (byte) (bytes[3] & 0xFF);
        this.sendLe = 0;

        if (bytes.length == 5) {
            this.lc = 0;
            this.le = bytes[4] & 0xFF;
            this.sendLe = 1;
            this.data = new byte[0];
        } else {
            this.lc = (short) (bytes[4] & 0xFF);
            this.le = 0;
            // for now, only short APDu is supported
            if (this.lc == 0) {
                this.le = bytes.length == 6 ? (byte) (bytes[4] & 0xFF) : 0;
                this.sendLe = 1;
                this.data = new byte[0];
            } else {
                this.data = Arrays.copyOfRange(bytes, 5, 5 + this.lc);
            }
        }
    }

    @Override
    public byte[] pack() {
        ByteBuffer buffer = ByteBuffer.allocate(11 + this.lc).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(this.cla);
        buffer.put(this.ins);
        buffer.put(this.p1);
        buffer.put(this.p2);
        buffer.putShort(this.lc);
        buffer.putInt(this.le);
        buffer.put(this.sendLe);
        buffer.put(this.data, 0, this.lc);
        return buffer.array();
    }

    @Override
    public void unpack(byte[] buffer) {
        // TODO
    }
}
