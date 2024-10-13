package jcprofiler.profiling.oscilloscope.drivers.libraries;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.ShortByReference;

public interface PicoScope4000Library extends Library {
    PicoScope4000Library INSTANCE = Native.load(
            "ps4000", PicoScope4000Library.class
    );
    int PS4000_OK = 0x00000000;
    int ps4000OpenUnit(ShortByReference handle);
    int ps4000GetUnitInfo(short handle, byte[] string, short stringLength, ShortByReference requiredSize, int info);
    int ps4000SetChannel(short handle, short  channel, short enabled, short dc, short range);
    int ps4000SetSimpleTrigger(short handle, short enable, short source, short threshold, short direction, int delay, short autoTrigger_ms);
    int ps4000GetTimebase(short handle, int timebase, int noSamples, IntByReference timeIntervalNanoseconds, short oversample, IntByReference maxSamples, short segmentIndex);
    int ps4000RunBlock(short handle, int noOfPreTriggerSamples, int noOfPostTriggerSamples,int timebase, short oversample, IntByReference timeIndisposedMs, short segmentIndex, Pointer lpReady, Pointer pParameter);
    int ps4000IsReady(short handle, ShortByReference ready);
    int ps4000SetDataBuffer(short handle, short channel,Pointer buffer, int bufferLth);
    int ps4000GetValues(short handle, int startIndex, IntByReference noOfSamples, int downSampleRatio, short downSampleRatioMode, short segmentIndex, ShortByReference overflow);
    int ps4000Stop(short handle);
    int ps4000CloseUnit(short handle);

    // Enumerations
    public enum PicoScope4000Channel {
        PS4000_CHANNEL_A(0),
        PS4000_CHANNEL_B(1),
        PS4000_CHANNEL_C(2),
        PS4000_CHANNEL_D(3),
        PS4000_EXTERNAL(4),
        PS4000_MAX_CHANNELS(4),
        PS4000_TRIGGER_AUX(5),
        PS4000_MAX_TRIGGER_SOURCES(6);
        private final int channel;
        PicoScope4000Channel(int channel){
            this.channel = channel;
        }
    }

    public enum PicoScope4000Range {
        PS4000_10MV,
        PS4000_20MV,
        PS4000_50MV,
        PS4000_100MV,
        PS4000_200MV,
        PS4000_500MV,
        PS4000_1V,
        PS4000_2V,
        PS4000_5V,
        PS4000_10V,
        PS4000_20V,
        PS4000_50V,
        PS4000_100V,
        PS4000_MAX_RANGES
    }

    enum PicoScope4000ThresholdDirection
    {
        ABOVE, //using upper threshold
        BELOW,
        RISING, // using upper threshold
        FALLING, // using upper threshold
        RISING_OR_FALLING, // using both threshold
        ABOVE_LOWER, // using lower threshold
        BELOW_LOWER, // using lower threshold
        RISING_LOWER,       // using lower threshold
        FALLING_LOWER,     // using lower threshold
    }
}
