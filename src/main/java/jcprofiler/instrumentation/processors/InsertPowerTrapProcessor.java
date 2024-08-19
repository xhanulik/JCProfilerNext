// SPDX-FileCopyrightText: 2022 Lukáš Zaoral <x456487@fi.muni.cz>
// SPDX-License-Identifier: GPL-3.0-only

package jcprofiler.instrumentation.processors;

import jcprofiler.args.Args;
import spoon.reflect.declaration.CtMethod;

/**
 * Class for performance trap insertion in power mode
 * <br>
 * Applicable to instances of {@link CtMethod}.
 *
 */
public class InsertPowerTrapProcessor extends AbstractInsertTrapProcessor<CtMethod<?>> {
    /**
     * Constructs the {@link InsertPowerTrapProcessor} class.
     *
     * @param args object with commandline arguments
     */
    public InsertPowerTrapProcessor(final Args args) {
        super(args);
    }
}
