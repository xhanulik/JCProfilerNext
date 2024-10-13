package jcprofiler.card.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RESP extends DataStructure {
    private int le;
    private byte sw1;
    private byte sw2;
    private int deltaT;
    private int deltaTAsnwer;
    private byte[] data;
    @Override
    public byte[] pack() {
        ByteBuffer buffer = ByteBuffer.allocate(14 + this.data.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(this.le);
        buffer.put(this.sw1);
        buffer.put(this.sw2);
        buffer.putInt(this.deltaT);
        buffer.putInt(this.deltaTAsnwer);
        buffer.put(this.data, 0, this.data.length);
        return buffer.array();
    }

    @Override
    public void unpack(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.le = buffer.getInt();
        this.sw1 = buffer.get();
        this.sw2 = buffer.get();
        this.deltaT = buffer.getInt();
        this.deltaTAsnwer = buffer.getInt();
        this.data = Arrays.copyOfRange(data, 14, data.length);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Convert each byte to a 2-digit hexadecimal value
            String hex = String.format("%02X", b);
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Override
    public String toString() {
        return bytesToHex(data) + String.format("%02X", sw1) + String.format("%02X", sw2);
    }

    public byte[] toArray() {
        byte[] result = new byte[data.length + 2];
        System.arraycopy(data, 0, result, 0, data.length);
        result[data.length] = sw1;
        result[data.length + 1] = sw2;
        return result;
    }
}
