// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.media;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.os.Process;
import android.util.Log;
import java.nio.ByteBuffer;

import org.chromium.base.CalledByNative;
import org.chromium.base.JNINamespace;

// Owned by its native counterpart declared in audio_record_input.h. Refer to
// that class for general comments.
@JNINamespace("media")
class AudioRecordInput {
    private static final String TAG = "AudioRecordInput";
    // We are unable to obtain a precise measurement of the hardware delay on
    // Android. This is a conservative lower-bound based on measurments. It
    // could surely be tightened with further testing.
    private static final int HARDWARE_DELAY_MS = 100;

    private final long mNativeAudioRecordInputStream;
    private final int mSampleRate;
    private final int mChannels;
    private final int mBitsPerSample;
    private final int mHardwareDelayBytes;
    private ByteBuffer mBuffer;
    private AudioRecord mAudioRecord;
    private AudioRecordThread mAudioRecordThread;

    private class AudioRecordThread extends Thread {
        // The "volatile" synchronization technique is discussed here:
        // http://stackoverflow.com/a/106787/299268
        // and more generally in this article:
        // https://www.ibm.com/developerworks/java/library/j-jtp06197/
        private volatile boolean mKeepAlive = true;

        @Override
        public void run() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            } catch (IllegalArgumentException e) {
                Log.wtf(TAG, "setThreadPriority failed", e);
            } catch (SecurityException e) {
                Log.wtf(TAG, "setThreadPriority failed", e);
            }
            try {
                mAudioRecord.startRecording();
            } catch (IllegalStateException e) {
                Log.e(TAG, "startRecording failed", e);
                return;
            }

            while (mKeepAlive) {
                int bytesRead = mAudioRecord.read(mBuffer, mBuffer.capacity());
                if (bytesRead > 0) {
                    nativeOnData(mNativeAudioRecordInputStream, bytesRead,
                                 mHardwareDelayBytes);
                } else {
                    Log.e(TAG, "read failed: " + bytesRead);
                }
            }

            try {
                mAudioRecord.stop();
            } catch(IllegalStateException e) {
                Log.e(TAG, "stop failed", e);
            }
        }

        public void joinRecordThread() {
            mKeepAlive = false;
            while (isAlive()) {
                try {
                    join();
                } catch (InterruptedException e) {
                    // Ignore.
                }
            }
        }
    }

    @CalledByNative
    private static AudioRecordInput createAudioRecordInput(long nativeAudioRecordInputStream,
            int sampleRate, int channels, int bitsPerSample, int bytesPerBuffer) {
        return new AudioRecordInput(nativeAudioRecordInputStream, sampleRate, channels,
                                    bitsPerSample, bytesPerBuffer);
    }

    private AudioRecordInput(long nativeAudioRecordInputStream, int sampleRate, int channels,
                             int bitsPerSample, int bytesPerBuffer) {
        mNativeAudioRecordInputStream = nativeAudioRecordInputStream;
        mSampleRate = sampleRate;
        mChannels = channels;
        mBitsPerSample = bitsPerSample;
        mHardwareDelayBytes = HARDWARE_DELAY_MS * sampleRate / 1000 * bitsPerSample / 8;

        // We use a direct buffer so that the native class can have access to
        // the underlying memory address. This avoids the need to copy from a
        // jbyteArray to native memory. More discussion of this here:
        // http://developer.android.com/training/articles/perf-jni.html
        try {
            mBuffer = ByteBuffer.allocateDirect(bytesPerBuffer);
        } catch (IllegalArgumentException e) {
            Log.wtf(TAG, "allocateDirect failure", e);
        }
        // Rather than passing the ByteBuffer with every OnData call (requiring
        // the potentially expensive GetDirectBufferAddress) we simply have the
        // the native class cache the address to the memory once.
        //
        // Unfortunately, profiling with traceview was unable to either confirm
        // or deny the advantage of this approach, as the values for
        // nativeOnData() were not stable across runs.
        nativeCacheDirectBufferAddress(mNativeAudioRecordInputStream, mBuffer);
    }

    @CalledByNative
    private boolean open() {
        if (mAudioRecord != null) {
           Log.e(TAG, "open() called twice without a close()");
           return false;
        }
        int channelConfig;
        if (mChannels == 1) {
            channelConfig = AudioFormat.CHANNEL_IN_MONO;
        } else if (mChannels == 2) {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        } else {
            Log.e(TAG, "Unsupported number of channels: " + mChannels);
            return false;
        }

        int audioFormat;
        if (mBitsPerSample == 8) {
            audioFormat = AudioFormat.ENCODING_PCM_8BIT;
        } else if (mBitsPerSample == 16) {
            audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        } else {
            Log.e(TAG, "Unsupported bits per sample: " + mBitsPerSample);
            return false;
        }

        // TODO(ajm): Do we need to make this larger to avoid underruns? The
        // Android documentation notes "this size doesn't guarantee a smooth
        // recording under load".
        int minBufferSize = AudioRecord.getMinBufferSize(mSampleRate, channelConfig, audioFormat);
        if (minBufferSize < 0) {
            Log.e(TAG, "getMinBufferSize error: " + minBufferSize);
            return false;
        }

        // We will request mBuffer.capacity() with every read call. The
        // underlying AudioRecord buffer should be at least this large.
        int audioRecordBufferSizeInBytes = Math.max(mBuffer.capacity(), minBufferSize);
        try {
            // TODO(ajm): Allow other AudioSource types to be requested?
            mAudioRecord = new AudioRecord(AudioSource.VOICE_COMMUNICATION,
                                           mSampleRate,
                                           channelConfig,
                                           audioFormat,
                                           audioRecordBufferSizeInBytes);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "AudioRecord failed", e);
            return false;
        }

        return true;
    }

    @CalledByNative
    private void start() {
        if (mAudioRecord == null) {
           Log.e(TAG, "start() called before open().");
           return;
        }
        if (mAudioRecordThread != null) {
            // start() was already called.
            return;
        }
        mAudioRecordThread = new AudioRecordThread();
        mAudioRecordThread.start();
    }

    @CalledByNative
    private void stop() {
        if (mAudioRecordThread == null) {
            // start() was never called, or stop() was already called.
            return;
        }
        mAudioRecordThread.joinRecordThread();
        mAudioRecordThread = null;
    }

    @CalledByNative
    private void close() {
        if (mAudioRecordThread != null) {
           Log.e(TAG, "close() called before stop().");
           return;
        }
        if (mAudioRecord == null) {
            // open() was not called.
            return;
        }
        mAudioRecord.release();
        mAudioRecord = null;
    }

    private native void nativeCacheDirectBufferAddress(long nativeAudioRecordInputStream,
                                                       ByteBuffer buffer);
    private native void nativeOnData(long nativeAudioRecordInputStream, int size,
                                     int hardwareDelayBytes);
}
