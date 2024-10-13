package jcprofiler.card.driver;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class ATR extends DataStructure {
    private byte ts = 0;
    private byte t0 = 0;
    private byte[] ta = new byte[4];
    private byte[] tb = new byte[4];
    private byte[] tc = new byte[4];
    private byte[] td = new byte[4];
    private byte[] h = new byte[16];
    private byte[] tMask = new byte[4];
    private byte hNum = 0;
    private byte tck = 0;
    private byte tckPresent = 0;
    private int dICurr = 0;
    private int fICurr = 0;
    public int fMaxCurr = 0;
    public byte tProtocolCurr = 0;
    private byte ifsc = 0;
    @Override
    public byte[] pack() {
        // TODO
        return new byte[0];
    }

    @Override
    public void unpack(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        this.ts = buffer.get();
        this.t0 = buffer.get();
        buffer.get(this.ta);
        buffer.get(this.tb);
        buffer.get(this.tc);
        buffer.get(this.td);
        buffer.get(this.h);
        buffer.get(this.tMask);
        this.hNum = buffer.get();
        this.tck = buffer.get();
        this.tckPresent = buffer.get();
        this.dICurr = buffer.getInt();
        this.fICurr = buffer.getInt();
        this.fMaxCurr = buffer.getInt();
        this.tProtocolCurr = buffer.get();
        this.ifsc = buffer.get();
    }

    public String toString() {
        return "ATR {" +
                "\nts = " + Byte.toUnsignedInt(ts) +
                ", \nt0 = " + Byte.toUnsignedInt(t0) +
                ", \nta = " + Arrays.toString(ta) +
                ", \ntb = " + Arrays.toString(tb) +
                ", \ntc = " + Arrays.toString(tc) +
                ", \ntd = " + Arrays.toString(td) +
                ", \nh = " + Arrays.toString(h) +
                ", \ntMask = " + Arrays.toString(tMask) +
                ", \nhNum = " + Byte.toUnsignedInt(hNum) +
                ", \ntck = " + Byte.toUnsignedInt(tck) +
                ", \ntckPresent = " + Byte.toUnsignedInt(tckPresent) +
                ", \ndICurr = " + dICurr +
                ", \nfICurr = " + fICurr +
                ", \nfMaxCurr = " + fMaxCurr +
                ", \ntProtocolCurr = " + Byte.toUnsignedInt(tProtocolCurr) +
                ", \nifsc = " + Byte.toUnsignedInt(ifsc) +
                "\n}";
    }
}
