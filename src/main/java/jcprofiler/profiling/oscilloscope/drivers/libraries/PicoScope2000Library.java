package jcprofiler.profiling.oscilloscope.drivers.libraries;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;

public interface PicoScope2000Library extends Library {
    public final static int PS2000_MAX_VALUE = 32767;

    PicoScope2000Library INSTANCE = (PicoScope2000Library) Native.load(
            "ps2000", PicoScope2000Library.class
    );


    short ps2000_open_unit();

    short ps2000_close_unit(short handle);

    short ps2000_set_channel(short handle, short channel, short enabled, short dc, short range);

    short ps2000_get_timebase(short handle, short timebase, int no_of_samples, IntByReference time_interval, ShortByReference time_units, short oversample, IntByReference max_samples);

    short ps2000_set_trigger(short handle, short source, short threshold, short direction, short delay, short auto_trigger_ms);

    short ps2000_run_block(short handle, int no_of_values, short timebase, short oversample, IntByReference time_indisposed_ms);

    int ps2000_get_times_and_values(short handle, Memory times, Memory buffer_a, Memory buffer_b, Memory buffer_c, Memory buffer_d, ShortByReference overflow, short time_units, int no_of_values);


    short ps2000_ready(short handle);

    short ps2000_stop(short handle);

    // Enumerations

    public enum PicoScope2000Channel {
        PS2000_CHANNEL_A(0),
        PS2000_CHANNEL_B(1),
        PS2000_CHANNEL_C(2),
        PS2000_CHANNEL_D(3),
        PS2000_EXTERNAL(4),
        PS2000_MAX_CHANNELS(4),
        PS2000_NONE(5);

        private final int channel;

        PicoScope2000Channel(int channel) {
            this.channel = channel;
        }
    }

    enum PicoScope2000Range {
        PS2000_10MV,
        PS2000_20MV,
        PS2000_50MV,
        PS2000_100MV,
        PS2000_200MV,
        PS2000_500MV,
        PS2000_1V,
        PS2000_2V,
        PS2000_5V,
        PS2000_10V,
        PS2000_20V,
        PS2000_50V,
        PS2000_MAX_RANGES;
    }

    enum PicoScope2000TimeUnits {
        PS2000_FS,
        PS2000_PS,
        PS2000_NS,
        PS2000_US,
        PS2000_MS,
        PS2000_S,
        PS2000_MAX_TIME_UNITS;
    }

    enum PicoScope2000Error {
        PS2000_OK,
        PS2000_MAX_UNITS_OPENED,  // More than PS2000_MAX_UNITS
        PS2000_MEM_FAIL,          // Not enough RAM on host machine
        PS2000_NOT_FOUND,         // Cannot find device
        PS2000_FW_FAIL,           // Unable to download firmware
        PS2000_NOT_RESPONDING,
        PS2000_CONFIG_FAIL,       // Missing or corrupted configuration settings
        PS2000_OS_NOT_SUPPORTED,  // Need to use win98SE (or later) or win2k (or later)
        PS2000_PICOPP_TOO_OLD;
    }

    enum PicoScope2000TriggerDirection {
        PS2000_RISING,
        PS2000_FALLING,
        PS2000_MAX_DIRS;
    }
}
