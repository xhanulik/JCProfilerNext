package jcprofiler.instrumentation.processors;

import jcprofiler.args.Args;
import jcprofiler.util.JCProfilerUtil;

import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtTypeReference;

public class InsertMemoryTrapProcessor extends AbstractInsertTrapProcessor<CtExecutable<?>> {
    public InsertMemoryTrapProcessor(final Args args) {
        super(args);
    }

    @Override
    public boolean isToBeProcessed(final CtExecutable<?> executable) {
        return JCProfilerUtil.getFullSignature(executable).equals(args.method);
    }

    @Override
    public void process(final CtExecutable<?> executable) {
        super.process(executable);
        fixPMArrayLength();
    }

    private void fixPMArrayLength() {
        final CtTypeReference<Short> shortRef = getFactory().createCtTypeReference(Short.TYPE);

        final int arrayLength = trapCount * Short.BYTES;
        final CtLiteral<Integer> arrayLengthLiteral = getFactory().createLiteral(arrayLength);
        arrayLengthLiteral.addTypeCast(shortRef);

        final CtField<?> arrayLengthField = PM.getField("ARRAY_LENGTH");
        if (!arrayLengthField.getType().equals(shortRef))
            throw new RuntimeException(
                    "PM.ARRAY_LENGTH field is of type " + arrayLengthField.getType() + "! Expected short.");

        @SuppressWarnings("unchecked") // the runtime check is above
        final CtField<Short> arrayLengthFieldCasted = (CtField<Short>) arrayLengthField;

        @SuppressWarnings("unchecked")
        // Unfortunately, this is the best solution we have since Spoon does not reflect type casts in type parameters.
        final CtLiteral<Short> arrayLengthLiteralCasted = (CtLiteral<Short>) (Object) arrayLengthLiteral;
        arrayLengthFieldCasted.setAssignment(arrayLengthLiteralCasted);
    }
}