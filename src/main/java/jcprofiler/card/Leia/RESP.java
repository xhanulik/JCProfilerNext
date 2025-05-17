// SPDX-FileCopyrightText: 2019 The LEIA Team <leia@ssi.gouv.fr>
// SPDX-FileCopyrightText: 2025 Veronika Hanulíková <xhanulik@gmail.com>
// SPDX-License-Identifier: BSD-3-Clause

/**
 * This file is a derivative work based on code from the SmartLEIA project,
 * originally developed by the LEIA Team (https://github.com/cw-leia/smartleia),
 * and licensed under the BSD 3-Clause License.
 *
 * Original code licensed under the BSD 3-Clause License:
 * Copyright (c) 2019, The LEIA Team <leia@ssi.gouv.fr>
 *
 * Modifications and translation:
 * Copyright (c) 2025 Veronika Hanulíková
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted under the terms of the BSD 3-Clause License.
 * See LICENSES/BSD-3-Clause.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
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
