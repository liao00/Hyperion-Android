package com.willowtreeapps.hyperion.geigercounter;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.SoundPool;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Choreographer;
import android.view.Display;

import static android.media.AudioManager.STREAM_SYSTEM;
import static com.willowtreeapps.hyperion.geigercounter.GeigerCounterPlugin.LOG_TAG;

@RequiresApi(DroppedFrameObserverFactory.REQUIRED_API_VERSION)
class DroppedFrameObserverImpl implements DroppedFrameObserver, Choreographer.FrameCallback {

    // Player for the Geiger counter tick sound
    private final SoundPool soundPool;
    private final int tickSoundID;
    // Sentinel value indicating we failed to load the tick sound
    private static final int NOT_LOADED = -1;

    // Whether we are watching for dropped frames
    private boolean isEnabled;

    // e.g. 0.01666 for a 60 Hz screen
    private final double hardwareFrameIntervalSeconds;

    // Last timestamp received from a frame callback, used to measure the next frame interval
    private long lastTimestampNanoseconds = NEVER;
    // Sentinel value indicating we have not yet received a frame callback since the observer was enabled
    private static final long NEVER = -1;

    DroppedFrameObserverImpl(AssetManager assetManager, Display display) {
        soundPool = new SoundPool(1, STREAM_SYSTEM, 0);
        int tickSoundID;
        try {
            AssetFileDescriptor tickSoundFileDescriptor = assetManager.openFd("sounds/GeigerCounterTick.wav");
            tickSoundID = soundPool.load(tickSoundFileDescriptor, 1);
        } catch (Exception exception) {
            Log.e(LOG_TAG, exception.toString());
            tickSoundID = NOT_LOADED;
        }
        this.tickSoundID = tickSoundID;

        double hardwareFramesPerSecond = display.getRefreshRate();
        hardwareFrameIntervalSeconds = 1.0 / hardwareFramesPerSecond;
    }

    // Helpers

    private void playTickSound() {
        if (tickSoundID != NOT_LOADED) {
            soundPool.play(tickSoundID, 1, 1, 1, 0, 1);
        }
    }

    // DroppedFrameObserver

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;

        Choreographer choreographer = Choreographer.getInstance();

        if (isEnabled) {
            choreographer.removeFrameCallback(this);
            choreographer.postFrameCallback(this);
        } else {
            choreographer.removeFrameCallback(this);
            lastTimestampNanoseconds = NEVER;
        }
    }

    // Choreographer.FrameCallback

    @Override
    public void doFrame(long timestampNanoseconds) {
        long frameIntervalNanoseconds = timestampNanoseconds - lastTimestampNanoseconds;

        // To detect a dropped frame, we need to know the interval between two frame callbacks.
        // If this is the first, wait for the second.
        if (lastTimestampNanoseconds != NEVER) {
            // With no dropped frames, frame intervals will roughly equal the hardware interval.
            // 2x the hardware interval means we definitely dropped one frame.
            // So our measuring stick is 1.5x.
            double droppedFrameIntervalSeconds = hardwareFrameIntervalSeconds * 1.5;

            double frameIntervalSeconds = frameIntervalNanoseconds / 1_000_000_000.0;

            if (droppedFrameIntervalSeconds < frameIntervalSeconds) {
                playTickSound();
            }
        }

        lastTimestampNanoseconds = timestampNanoseconds;
        Choreographer.getInstance().postFrameCallback(this);
    }

}
