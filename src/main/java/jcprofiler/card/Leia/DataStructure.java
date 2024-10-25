/**
 * Code Copyright (c) 2024, Veronika Hanulikova <xhanulik@gmail.com>
 * Python driver for the LEIA Smart Reader (https://github.com/cw-leia/smartleia) Copyright (c) 2019, The LEIA Team <leia@ssi.gouv.fr>
 */

package jcprofiler.card.Leia;

abstract class DataStructure {
    public abstract byte[] pack();
    public abstract void unpack(byte[] buffer);
}
