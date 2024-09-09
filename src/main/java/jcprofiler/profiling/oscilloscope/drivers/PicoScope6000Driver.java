package jcprofiler.profiling.oscilloscope.drivers;

import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import jcprofiler.args.Args;
import jcprofiler.profiling.oscilloscope.AbstractOscilloscope;
import jcprofiler.profiling.oscilloscope.drivers.libraries.PicoScope6000Library;
import jcprofiler.profiling.oscilloscope.drivers.libraries.PicoScopeVoltageDefinitions;

import java.io.IOException;
import java.nio.file.Path;

public class PicoScope6000Driver extends AbstractOscilloscope {

    ShortByReference handle = new ShortByReference();
    private final short channelA = (short) PicoScope6000Library.PicoScope6000Channel.PS6000_CHANNEL_A.ordinal();
    private final short channelB = (short) PicoScope6000Library.PicoScope6000Channel.PS6000_CHANNEL_B.ordinal();
    private short chARange = (short) PicoScope6000Library.PicoScope6000Range.PS6000_10V.ordinal();
    private short chBRange = (short) PicoScope6000Library.PicoScope6000Range.PS6000_1V.ordinal();

    private double mvThreshold = 1000.0;
    short delay = 0; // no data before trigger
    short autoTriggerMs = 0; // wait indefinitely
    short direction = (short) PicoScope6000Library.PicoScope6000ThresholdDirection.PS6000_RISING.ordinal();
    short timebase = 0;
    int timeIntervalNanoseconds = 51;
    static int numberOfSamples = 9748538;
    static short oversample = 1;
    ShortByReference timeUnitsSbr = new ShortByReference((short) 0);

    protected PicoScope6000Driver(Args args) {
        super(args);
    }

    @Override
    public boolean connect() {
        int status = PicoScope6000Library.INSTANCE.ps6000OpenUnit(handle, null);
        return status != PicoScope6000Library.PS6000_OK;
    }

    private void setChannel(short channel, short range) {
        int status = PicoScope6000Library.INSTANCE.ps6000SetChannel(
                handle.getValue(),
                channel,
                (short) 1,
                (short) PicoScope6000Library.PicoScope6000Coupling.PS6000_DC_1M.ordinal(),
                range,
                (float) 0,
                (short) 0);
        if (status != PicoScope6000Library.PS6000_OK) {
            finish();
            throw new RuntimeException("Cannot setup PicoScope6000 channel");
        }
    }

    private void calculateTimebase() {
        short currentTimebase = 0;
        int oldTimeInterval = 0;

        IntByReference timeInterval = new IntByReference();
        IntByReference maxSamples = new IntByReference();

        while (PicoScope6000Library.INSTANCE.ps6000GetTimebase(
                handle.getValue(),
                currentTimebase,
                numberOfSamples,
                timeInterval,
                oversample,
                maxSamples, 0) == PicoScope6000Library.PS6000_OK || timeInterval.getValue() < timeIntervalNanoseconds) {
            currentTimebase++;
            oldTimeInterval = timeInterval.getValue();
        }

        timebase = (short) (currentTimebase - 1);
        timeIntervalNanoseconds = oldTimeInterval;
    }

    @Override
    public void setup() {
        // Set channel A
        setChannel(channelA, chARange);

        // Set channel B
        setChannel(channelB, chBRange);
        // Set trigger
        short threshold = (short) (mvThreshold / PicoScopeVoltageDefinitions.SCOPE_INPUT_RANGES_MV[chARange] * PicoScope6000Library.PS6000_MAX_VALUE);
        int status = PicoScope6000Library.INSTANCE.ps6000SetSimpleTrigger(
                handle.getValue(),
                (short) 1,
                channelA,
                threshold,
                direction,
                delay,
                autoTriggerMs
        );
        if (status == PicoScope6000Library.PS6000_OK) {
            finish();
            throw new RuntimeException("Cannot setup PicoScope2000 channel A");
        }
        // Set timebase
        calculateTimebase();
    }

    @Override
    public void startMeasuring() {

    }

    @Override
    public void stopDevice() {

    }

    @Override
    public void store(Path file) throws IOException {

    }

    @Override
    public void finish() {

    }
}
