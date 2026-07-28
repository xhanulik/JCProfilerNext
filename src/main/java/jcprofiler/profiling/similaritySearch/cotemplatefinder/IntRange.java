// SPDX-FileCopyrightText: 2019-2026 Martin Podhora (martinftlsx)
// SPDX-License-Identifier: MIT

/**
 * This file is copied from the SPA-Cryptographic-Operations-Extractor,
 * originally developed by Martin Podhora (https://github.com/crocs-muni/SPA-Cryptographic-Operations-Extractor),
 * and licensed under MIT license.
 *
 * Original code licensed under the MIT License:
 * Copyright (c) 2019 martinftlsx
 *
 * Licensed under the MIT License.
 * See LICENSES/MIT.txt and THIRD_PARTY_NOTICES.txt for details.
 *
 * This file is distributed as part of a larger project (JCProfilerNext),
 * which is licensed under the GNU General Public License v3.0.
 * See LICENSE.txt for full licensing information.
 */

package jcprofiler.profiling.similaritySearch.cotemplatefinder;

/**
 * Simple (firstIndex, lastIndex) interval pair, standing in for
 * {@code org.apache.commons.lang3.tuple.Pair<Integer, Integer>} used by the
 * original CO Template Finder module, since this project does not depend on commons-lang3.
 *
 * @author Martin Podhora
 */
public class IntRange {
    private final int firstIndex;
    private final int lastIndex;

    public IntRange(int firstIndex, int lastIndex) {
        this.firstIndex = firstIndex;
        this.lastIndex = lastIndex;
    }

    public int getFirstIndex() {
        return firstIndex;
    }

    public int getLastIndex() {
        return lastIndex;
    }
}
