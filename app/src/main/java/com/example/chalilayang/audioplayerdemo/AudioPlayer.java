package com.example.chalilayang.audioplayerdemo;

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.example.chalilayang.audioplayerdemo.utils.FileUtils;
import com.example.chalilayang.audioplayerdemo.utils.SoundTouch;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by chalilayang on 2016/12/9.
 */
@TargetApi(16)
public class AudioPlayer {
    private static final String TAG = "AudioPlayer";
    private ByteBuffer[] inputBuffersAudio = null;
    private ByteBuffer[] outputBuffersAudio = null;
    private MediaCodec.BufferInfo infoAudio = null;
    private MediaExtractor audioExtractor;
    private MediaCodec audioDecoder;
    private AudioTrack audioTrack;
    private int audioTrackIndex = -1;
    private int sampleRate = -1;
    private int channelCount = -1;
    private long curPosition = 0;
    private long duration = 0;
    private String musicPath;
    private MediaFormat audioFormat = null;
    private long startTime;
    private String mime = null;
    private Object audioTrackLock = new Object();
    private Object pauseLock = new Object();
    private Object soundLock = new Object();
    private boolean isPausing = true;
    private long seekPos = -1;
    private Thread deMuxThread;
    private volatile boolean stop = false;
    private float mSpeed = 1f;
    private SoundTouch soundTouch;

    private short[] chunk;

    public AudioPlayer() {
        this.deMuxThread = new Thread(decodeRunnable);
        chunk = new short[1024 * 1024];
    }

    public boolean openAudio(String musicFile, long start) {
        return this.openAudio(musicFile, start, 1f);
    }

    public boolean openAudio(String musicFile, long start, float speed) {
        Log.i(TAG, "openAudio: " + musicFile);
        boolean result = true;
        if (!FileUtils.isValid(musicFile)) {
            result = false;
            return result;
        }
        if (speed <= 3 && speed >= 0.33) {
            mSpeed = speed;
        } else {
            mSpeed = 1;
        }
        startTime = start;
        isPausing = true;
        musicPath = musicFile;
        if (audioExtractor != null) {
            audioExtractor.release();
        }
        audioExtractor = new MediaExtractor();
        try {
            File file = new File(musicFile);
            FileInputStream fis = new FileInputStream(file);
            audioExtractor.setDataSource(fis.getFD());
            final int trackCount = audioExtractor.getTrackCount();
            for (int index = 0; index < trackCount; index++) {
                MediaFormat format = audioExtractor.getTrackFormat(index);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(index);
                    audioTrackIndex = index;
                    audioFormat = format;
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        duration = format.getLong(MediaFormat.KEY_DURATION);
                    } else {
                        return false;
                    }
                    this.mime = mime;
                    break;
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) {
                return false;
            }
            audioExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } catch (IOException e) {
            Log.i(TAG, "openAudio: " + e.getMessage());
            result = false;
            e.printStackTrace();
        }
        return result;
    }

    public boolean prepare() {
        Log.i(TAG, "prepare: ");
        if (audioTrack == null && !createAudioTrack()) {
            return false;
        }
        if (audioDecoder == null && !createMediaCodec()) {
            return false;
        }
        if (soundTouch == null && !createSoundTouch()) {
            return false;
        }
        if (deMuxThread != null) {
            deMuxThread.start();
            return true;
        }
        return false;
    }

    private void setSpeed(float speed) {
        synchronized (soundLock) {
            if (soundTouch != null) {
                soundTouch.release();
            }
            mSpeed = speed;
            soundTouch = new SoundTouch();
            soundTouch.setChannels(1);
            soundTouch.setPitch(1);
            soundTouch.setSampleRate(sampleRate);
            soundTouch.setRate(1);
            soundTouch.setTempo(mSpeed);
        }
    }

    public long getStartTime() {
        return startTime;
    }

    public long getCurrentPosition() {
        return curPosition;
    }

    public long getDuration() {
        return duration;
    }

    public String getMusicPath() {
        return musicPath;
    }

    private boolean createSoundTouch() {
        boolean result = true;
        soundTouch = new SoundTouch();
        soundTouch.setChannels(1);
        soundTouch.setPitch(1);
        soundTouch.setSampleRate(sampleRate);
        soundTouch.setRate(1);
        soundTouch.setTempo(mSpeed);
        return result;
    }

    private boolean createMediaCodec() {
        boolean result = true;
        try {
            audioExtractor.selectTrack(this.audioTrackIndex);
            audioDecoder = MediaCodec.createDecoderByType(mime);
            audioDecoder.configure(audioFormat, null, null, 0);
            audioDecoder.start();
            inputBuffersAudio = audioDecoder.getInputBuffers();
            outputBuffersAudio = audioDecoder.getOutputBuffers();
            infoAudio = new MediaCodec.BufferInfo();
        } catch (IOException e) {
            result = false;
            e.printStackTrace();
        }
        return result;
    }

    private boolean createAudioTrack() {
        boolean result = true;
        if (audioTrack != null
                && audioTrack.getState() != AudioTrack.STATE_UNINITIALIZED) {
            audioTrack.release();
            audioTrack = null;
        }
        int minBufferSize = AudioTrack.getMinBufferSize(this.sampleRate,
                AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = 4 * minBufferSize;
        int channelConfig = AudioFormat.CHANNEL_OUT_MONO;
        if (channelCount >= 2) {
            channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        }
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                this.sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);
        return result;
    }

    private void extractAudioData(ByteBuffer buffer, int inIndex) {
        if (seekPos != -1) {
            audioExtractor.seekTo(seekPos, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            seekPos = -1;
        }
        long sampleTime = audioExtractor.getSampleTime();
        int sampleSize = audioExtractor.readSampleData(buffer, 0);
        while (sampleSize <= 0) {
            audioExtractor.seekTo(startTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            sampleTime = audioExtractor.getSampleTime();
            sampleSize = audioExtractor.readSampleData(buffer, 0);
        }
        audioDecoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0);
        audioExtractor.advance();
        buffer.clear();
    }

    public void pause() {
        Log.i(TAG, "pause: " + isPausing);
        if (isPausing) {
            return;
        }
        synchronized (audioTrackLock) {
            if (audioTrack != null
                    && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.pause();
            }
        }
        isPausing = true;
    }

    public void play(float mSpeed) {
        if (!isPausing) {
            pause();
        }
        setSpeed(mSpeed);
        play();
    }

    private void play() {
        if (!isPausing) {
            return;
        }
        synchronized (audioTrackLock) {
            if (audioTrack != null
                    && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.play();
            }
        }
        synchronized (pauseLock) {
            isPausing = false;
            pauseLock.notifyAll();
        }
    }

    public void resume(float mSpeed) {
        if (isPausing) {
            setSpeed(mSpeed);
            resume();
        }
    }

    public void seek(long time) {
        Log.i(TAG, "seek: " + time);
        if (time < 0 || time > duration) {
            Log.i(TAG, "seek value not valid ");
            return;
        }
        if (!isPausing) {
            pause();
        }
        synchronized (audioTrackLock) {
            if (audioTrack != null
                    && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.flush();
            }
        }
        curPosition = seekPos = time;
    }

    private void resume() {
        if (!isPausing) {
            return;
        }
        synchronized (audioTrackLock) {
            if (audioTrack != null
                    && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                audioTrack.play();
            }
        }
        synchronized (pauseLock) {
            isPausing = false;
            pauseLock.notifyAll();
        }
    }

    public void releaseResource() {
        Log.i(TAG, "releaseResource: ");
        stop = true;
        isPausing = false;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        if (deMuxThread != null) {
            deMuxThread.interrupt();
            deMuxThread = null;
        }
        synchronized (soundLock) {
            if (soundTouch != null) {
                soundTouch.release();
                soundTouch = null;
            }
        }
        synchronized (audioTrackLock) {
            if (audioTrack != null
                    && audioTrack.getState() != AudioTrack.STATE_UNINITIALIZED) {
                audioTrack.release();
                audioTrack = null;
            }
        }
        if (audioDecoder != null) {
            audioDecoder.release();
            audioDecoder = null;
        }
        if (audioExtractor != null) {
            audioExtractor.release();
            audioExtractor = null;
        }
    }

    private void drainAudioData() {
        Log.i(TAG, "drainAudioData");
        while(!stop && audioDecoder != null && audioTrack != null) {
            Log.i(TAG, "drainAudioData: while");
            if (isPausing) {
                synchronized (pauseLock) {
                    try {
                        pauseLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (stop || audioDecoder == null || audioTrack == null) {
                return;
            }
            int inIndex = -1;
            try {
                inIndex = audioDecoder.dequeueInputBuffer(10000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (stop || audioDecoder == null || audioTrack == null) {
                return;
            }
            Log.i(TAG, "drainAudioData: inIndex " + inIndex);
            if (inIndex >= 0) {
                ByteBuffer buffer = inputBuffersAudio[inIndex];
                extractAudioData(buffer, inIndex);
            }
            if (stop || audioDecoder == null || audioTrack == null) {
                return;
            }
            int outIndex = -1;
            try {
                outIndex = audioDecoder.dequeueOutputBuffer(infoAudio, 10000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (stop || audioDecoder == null || audioTrack == null) {
                return;
            }
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    outputBuffersAudio = audioDecoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    break;
                default:
                    Log.i(TAG, "outIndex " + outIndex
                            + " infoAudio.presentationTimeUs " + infoAudio.presentationTimeUs
                            + " infoAudio.size " + infoAudio.size);
                    if (outIndex >= 0) {
                        curPosition = infoAudio.presentationTimeUs;
                        ByteBuffer buffer = outputBuffersAudio[outIndex];
                        if (chunk.length < infoAudio.size/2) {
                            chunk = new short[infoAudio.size/2];
                        }
                        buffer.asShortBuffer().get(chunk, 0, infoAudio.size/2);
                        buffer.clear();
                        if (mSpeed == 1f || soundTouch == null) {
                            writeAudioTrack(chunk, infoAudio.size/2);
                        } else {
                            synchronized (soundLock) {
                                drainSound(chunk, infoAudio.size/2);
                                short[] data = pullSound();
                                while(data != null && data.length > 0) {
                                    Log.i(TAG, "drainAudioData: data.length " + data.length);
                                    writeAudioTrack(data, data.length);
                                    data = pullSound();
                                }
                            }
                        }
                        Log.i(TAG, "drainAudioData: releaseOutputBuffer " + outIndex);
                        audioDecoder.releaseOutputBuffer(outIndex, false);
                    }
                    break;
            }
        }
    }


    private void drainSound(short[] sound, int size) {
        if (soundTouch != null) {
            Log.i(TAG, "drainSound: " + size);
            soundTouch.putSamples(sound, size);
        }
    }

    private short[] pullSound() {
        Log.i(TAG, "pullSound: ");
        short[] result = null;
        if (soundTouch != null) {
            result = soundTouch.receiveSamples();
        }
        return result;
    }

    private void writeAudioTrack(short[] chunk, int length) {
        synchronized (audioTrackLock) {
            if (audioTrack != null
                    && audioTrack.getState() != AudioTrack.STATE_UNINITIALIZED
                    && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
                Log.i(TAG, "audioTrack.write " + infoAudio.size);
                audioTrack.write(chunk, 0, length);
            }
        }
    }

    private Runnable decodeRunnable = new Runnable() {
        @Override
        public void run() {
            drainAudioData();
            Log.i(TAG, "run: decodeRunnable end");
        }
    };
}
