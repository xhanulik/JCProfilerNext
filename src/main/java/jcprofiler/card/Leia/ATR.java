/**
 * Code Copyright (c) 2024, Veronika Hanulikova <xhanulik@gmail.com>
 * Python driver for the LEIA Smart Reader (https://github.com/cw-leia/smartleia) Copyright (c) 2019, The LEIA Team <leia@ssi.gouv.fr>
 */

package jcprofiler.card.Leia;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ATR extends DataStructure {
    private byte ts = 0;
    private byte t0 = 0;
    private final byte[] ta = new byte[4];
    private final byte[] tb = new byte[4];
    private final byte[] tc = new byte[4];
    private final byte[] td = new byte[4];
    private final byte[] h = new byte[16];
    private final byte[] tMask = new byte[4];
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
        // Not needed for now
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
}
