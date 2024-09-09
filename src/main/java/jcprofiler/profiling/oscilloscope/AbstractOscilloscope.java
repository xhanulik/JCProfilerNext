package jcprofiler.profiling.oscilloscope;


import jcprofiler.args.Args;
import jcprofiler.profiling.oscilloscope.drivers.PicoScope2000Driver;
import jcprofiler.profiling.oscilloscope.drivers.PicoScope6000Driver;
import org.apache.commons.csv.CSVFormat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public abstract class AbstractOscilloscope {

    /**
     * Array of implemented oscilloscope drivers
     */
    static Class<?>[] oscilloscopeDrivers = {
            PicoScope2000Driver.class,
            PicoScope6000Driver.class
    };
    /**
     * Commandline arguments
     */
    protected static Args args = null;
    protected Path csv = FileSystems.getDefault().getPath("./measurements.csv");
    protected CSVFormat format = CSVFormat.Builder.create(CSVFormat.DEFAULT).setCommentMarker('#')
            .setRecordSeparator(System.lineSeparator())
            .build();


    protected AbstractOscilloscope(Args args) {
        AbstractOscilloscope.args = args;
    }

    /** Factory method
     *
     * @return constructed {@link AbstractOscilloscope} object
     */
    public static AbstractOscilloscope create() {
        for (Class<?> driver : oscilloscopeDrivers) {
            try {
                Constructor<?> constructor = driver.getConstructor(Args.class);
                AbstractOscilloscope device = (AbstractOscilloscope) constructor.newInstance(args);
                if (device.connect()) {
                    return device;
                }
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        throw new RuntimeException("No oscilloscope connected!");
    }

    public abstract boolean connect();
    public abstract void setup();
    public abstract void startMeasuring();
    public abstract void stopDevice();
    public abstract void store(Path file) throws IOException;
    public abstract void finish();
}
