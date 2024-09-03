package jcprofiler.profiling.oscilloscope.drivers;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;
import jcprofiler.args.Args;
import jcprofiler.profiling.oscilloscope.AbstractOscilloscope;
import jcprofiler.profiling.oscilloscope.drivers.libraries.PicoScope2000Library;
import jcprofiler.profiling.oscilloscope.drivers.libraries.PicoScopeVoltageDefinitions;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class PicoScope2000Driver extends AbstractOscilloscope {

    short handle;
    private short status;
    private final short channelA = (short) PicoScope2000Library.PicoScope2000Channel.PS2000_CHANNEL_A.ordinal();
    private final short channelB = (short) PicoScope2000Library.PicoScope2000Channel.PS2000_CHANNEL_B.ordinal();
    private short chARange = (short) PicoScope2000Library.PicoScope2000Range.PS2000_10V.ordinal();
    private short chBRange = (short) PicoScope2000Library.PicoScope2000Range.PS2000_1V.ordinal();
    private double mvThreshold = 1000.0;
    float delay = 0; // no data before trigger
    short autoTriggerMs = 0; // wait indefinitely
    short direction = (short) PicoScope2000Library.PicoScope2000TriggerDirection.PS2000_RISING.ordinal();
    static short timebase = 8; // 25 MS/s with PicoScope 2204A
    static int numberOfSamples = 9748538;
    static short oversample = 1;
    ShortByReference timeUnitsSbr = new ShortByReference((short) 0);


    public PicoScope2000Driver(Args args) {
        super(args);
    }

    @Override
    public boolean testConnection() {
        handle = PicoScope2000Library.INSTANCE.ps2000_open_unit();
        return handle != 0;
    }

    private void setChannel(short channel, short range) {
        status = PicoScope2000Library.INSTANCE.ps2000_set_channel(handle,
                channel, /* channel number */
                (short) 1, /* enable channel */
                (short) 1, /* DC coupling */
                range); /* set range */
        if (status != PicoScope2000Library.PicoScope2000Error.PS2000_OK.ordinal()) {
            finish();
            throw new RuntimeException("Cannot setup PicoScope2000 channel A");
        }
    }

    private void calculateTimebase() {
        // Preselect the timebase
        timebase = 0;
        IntByReference timeIntervalIbr = new IntByReference(0);
        IntByReference maxSamplesIbr = new IntByReference(0);
        do {
            status = PicoScope2000Library.INSTANCE.ps2000_get_timebase(handle, timebase, numberOfSamples, timeIntervalIbr, timeUnitsSbr, oversample, maxSamplesIbr);
            // If invalid timebase is used, increment timebase index
            if(status == 0) {
                timebase++;
            }
        } while (status == 0);
        System.out.println("\tTimebase: " + timebase);
        System.out.println("\tTime interval: " + timeIntervalIbr);
        System.out.println("\tTime unit: " + timeUnitsSbr);
        System.out.println("\tMax samples: " + maxSamplesIbr);
    }

    @Override
    public void setup() {
        // Set channel A
        setChannel(channelA, chARange);

        // Set channel B
        setChannel(channelB, chBRange);
        // Set trigger
        short threshold = (short) (mvThreshold / PicoScopeVoltageDefinitions.SCOPE_INPUT_RANGES_MV[chARange] * PicoScope2000Library.PS2000_MAX_VALUE);
        status = PicoScope2000Library.INSTANCE.ps2000_set_trigger2(handle, channelA, threshold, direction, delay, autoTriggerMs);
        if (status == 0) {
            finish();
            throw new RuntimeException("Cannot setup PicoScope2000 channel A");
        }
        // Set timebase
        calculateTimebase();
    }

    @Override
    public void startMeasuring() {
        IntByReference timeIndisposedMsIbr = new IntByReference(0);
        System.out.println("\tStarting data collection (waiting for trigger)...");

        status = PicoScope2000Library.INSTANCE.ps2000_run_block(handle, numberOfSamples, timebase, oversample, timeIndisposedMsIbr);
        if (status == 0) {
            System.err.println("Error in running block measurement");
            finish();
        }
    }

    @Override
    public void stopDevice() {
        PicoScope2000Library.INSTANCE.ps2000_stop(handle);
    }

    private float[] adc2mV(short[] chAData) {
        int vRange = PicoScopeVoltageDefinitions.SCOPE_INPUT_RANGES_MV[chARange];
        float[] result = new float[chAData.length];
        for (int i = 0; i < chAData.length; i++) {
            result[i] =  ((float) chAData[i] * vRange) / PicoScope2000Library.PS2000_MAX_VALUE;
        }
        return result;
    }

    @Override
    public void store(Path resultFile) throws IOException {
        short ready = 0;
        while (ready == 0) {
            ready = PicoScope2000Library.INSTANCE.ps2000_ready(handle);
            System.out.print(".");

            try {
                Thread.sleep(5);
            } catch(InterruptedException ie) {
                ie.printStackTrace();
                finish();
            }
        }

        if (ready > 0){
            System.out.println("Data collection completed");

            // Retrieve data values
            Memory timesPointer = new Memory((long) numberOfSamples * Native.getNativeSize(Integer.TYPE));
            Memory chABufferPointer = new Memory((long) numberOfSamples * Native.getNativeSize(Short.TYPE));
            ShortByReference overflowSbr = new ShortByReference((short) 0);

            int numberOfSamplesCollected = PicoScope2000Library.INSTANCE.ps2000_get_times_and_values(handle, timesPointer, chABufferPointer, null,
                    null, null, overflowSbr, timeUnitsSbr.getValue(), numberOfSamples);

            if (numberOfSamplesCollected > 0) {
                System.out.println("\tCollected " + numberOfSamples + " samples.");
                System.out.println();

                int[] times = timesPointer.getIntArray(0, numberOfSamplesCollected);
                short[] chAData = chABufferPointer.getShortArray(0, numberOfSamplesCollected);
                float[] chADataMiliVolts = adc2mV(chAData);

                System.out.println("Time\t Milivolts");
                CSVPrinter printer = new CSVPrinter(new FileWriter(resultFile.toFile()), super.format);
                for(int i = 0; i < numberOfSamplesCollected; i++) {
                    System.out.println(times[i] + ",\t" + chADataMiliVolts[i]);
                    printer.print(times[i]);
                    printer.printRecord(chADataMiliVolts[i]);
                }
            } else {
                System.err.println("\tps2000_get_times_and_values: No samples collected.");
            }
        }
    }

    @Override
    public void finish() {
        stopDevice();
        PicoScope2000Library.INSTANCE.ps2000_close_unit(handle);
    }
}
