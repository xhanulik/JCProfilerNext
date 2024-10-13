package jcprofiler.card.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ConfigureSmartcardCommand extends DataStructure {
    private byte protocol;
    private int etu;
    private int freq;
    private byte negotiatePts;
    private byte negotiateBaudrate;

    public enum T {
        AUTO((byte)0),
        T0((byte)1),
        T1((byte)2);

        private final byte protocol;

        T(byte protocol) {
            this.protocol = protocol;
        }

        public byte value() {
            return protocol;
        }
    }


    // Constructor
    public ConfigureSmartcardCommand(
            Byte protocol,
            Integer etu,
            Integer freq,
            Boolean negotiatePts,
            Boolean negotiateBaudrate) {
        if (protocol == null || protocol > T.T1.value())
            throw new RuntimeException("Protocol number not supported");
        if (protocol == T.AUTO.value())
            throw new RuntimeException("Auto detection of communication protocol is not supported");
        this.protocol = protocol;
        this.etu = etu != null ? etu : 0;
        this.freq = freq != null ? freq : 0;
        this.negotiatePts = negotiatePts != null && negotiatePts ? (byte) 1 : (byte) 0;
        this.negotiateBaudrate = negotiateBaudrate != null && negotiateBaudrate ? (byte) 1 : (byte) 0;
    }

    @Override
    public byte[] pack() {
        ByteBuffer buffer = ByteBuffer.allocate(11);
        buffer.put(protocol);
        buffer.putInt(etu);
        buffer.putInt(freq);
        buffer.put(negotiatePts);
        buffer.put(negotiateBaudrate);
        System.out.println(buffer);
        return buffer.array();
    }

    @Override
    public void unpack(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.protocol = buffer.get();
        this.etu = buffer.getInt();
        this.freq = buffer.getInt();
        this.negotiatePts = buffer.get();
        this.negotiateBaudrate = buffer.get();
    }
}
