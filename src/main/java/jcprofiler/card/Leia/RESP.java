/**
 * Code Copyright (c) 2024, Veronika Hanulikova <xhanulik@gmail.com>
 * Python driver for the LEIA Smart Reader (https://github.com/cw-leia/smartleia) Copyright (c) 2019, The LEIA Team <leia@ssi.gouv.fr>
 */

package jcprofiler.card.Leia;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class RESP extends DataStructure {
    private int le;
    private byte sw1;
    private byte sw2;
    private int deltaT;
    private int deltaTAnswer;
    private byte[] data;
    @Override
    public byte[] pack() {
        ByteBuffer buffer = ByteBuffer.allocate(14 + this.data.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(this.le);
        buffer.put(this.sw1);
        buffer.put(this.sw2);
        buffer.putInt(this.deltaT);
        buffer.putInt(this.deltaTAnswer);
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
        this.deltaTAnswer = buffer.getInt();
        this.data = Arrays.copyOfRange(data, 14, data.length);
    }

    public byte[] toArray() {
        byte[] result = new byte[data.length + 2];
        System.arraycopy(data, 0, result, 0, data.length);
        result[data.length] = sw1;
        result[data.length + 1] = sw2;
        return result;
    }
}
