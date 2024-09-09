package jcprofiler.profiling.oscilloscope.drivers.libraries;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;

public interface PicoScope6000Library extends Library {
    PicoScope6000Library INSTANCE = (PicoScope6000Library) Native.load(
            "ps6000", PicoScope6000Library.class
    );

    int PS6000_MAX_VALUE = 32512;

    int ps6000OpenUnit(ShortByReference handle, String serial);

    int ps6000SetChannel(short handle, short channel, short enabled, short type, short range, float analogueOffset, short bandwidth);

    int ps6000GetTimebase(short handle, int timebase, int noSamples, IntByReference timeIntervalNanoseconds, short oversample, IntByReference maxSamples, int segmentIndex);

    int ps6000SetSimpleTrigger(short handle, short enable, short source, short threshold, short direction, int delay, short autoTrigger_ms);

    int ps6000RunBlock(short handle, int noOfPreTriggerSamples, int noOfPostTriggerSamples, int timebase, short oversample, IntByReference timeIndisposedMs, int segmentIndex, short lpReady, Void pParameter);

    int ps6000IsReady(short handle, ShortByReference ready);

    int ps6000SetDataBuffers(short handle, short channel, ShortByReference bufferMax, ShortByReference bufferMin, int bufferLth, short downSampleRatioMode);

    int ps6000GetValues(short handle, int startIndex, IntByReference noOfSamples, int downSampleRatio, short downSampleRatioMode, int segmentIndex, ShortByReference overflow);

    int ps6000Stop(short handle);

    int ps6000CloseUnit(short handle);

    // Enumerations
    enum PicoScope6000Channel {
        PS6000_CHANNEL_A,
        PS6000_CHANNEL_B,
        PS6000_CHANNEL_C,
        PS6000_CHANNEL_D,
        PS6000_EXTERNAL,
        PS6000_TRIGGER_AUX,
        PS6000_MAX_TRIGGER_SOURCES
    }
    enum PicoScope6000Range {
        PS6000_10MV,
        PS6000_20MV,
        PS6000_50MV,
        PS6000_100MV,
        PS6000_200MV,
        PS6000_500MV,
        PS6000_1V,
        PS6000_2V,
        PS6000_5V,
        PS6000_10V,
        PS6000_20V,
        PS6000_50V,
        PS6000_MAX_RANGES
    }

    enum PicoScope6000TimeUnits {
        PS6000_FS,
        PS6000_PS,
        PS6000_NS,
        PS6000_US,
        PS6000_MS,
        PS6000_S,
        PS6000_MAX_TIME_UNITS,
    }

    final int PS6000_OK = 0x00000000;

    enum PicoScope6000ThresholdDirection {
        PS6000_ABOVE,             //using upper threshold
        PS6000_BELOW,							// using upper threshold
        PS6000_RISING,            // using upper threshold
        PS6000_FALLING,           // using upper threshold
    }

    enum PicoScope6000Coupling {
        PS6000_AC,
        PS6000_DC_1M,
        PS6000_DC_50R
    }
}
