package org.lolicode.nekomusiccli.music.player;

import org.lolicode.nekomusiccli.NekoMusicClient;
import org.lwjgl.openal.AL10;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AudioPlayer implements AutoCloseable {
    private final int source;
    private final Decoder decoder;
    private final ByteArrayInputStream stream;
    private final BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
    private volatile boolean playbackRunning = false;
    private volatile boolean cleanupDone = false;

    public AudioPlayer(ByteArrayInputStream stream) throws RuntimeException, IOException {
        this.source = AL10.alGenSources();
        this.stream = stream;

        try {
            this.decoder = Decoder.getDecoder(stream);
        } catch (RuntimeException | IOException e) {
            NekoMusicClient.LOGGER.error("Failed to open the audio stream", e);
            cleanup();
            throw e;
        }
    }

    public synchronized void play() {
        if (playbackRunning) {
            return;  // Prevent multiple playbacks
        }
        playbackRunning = true;
        new Thread(this::decodeLoop, NekoMusicClient.MOD_NAME + " Audio-Decode-Loop").start();
        new Thread(this::playBackLoop, NekoMusicClient.MOD_NAME + " Audio-Playback-Loop").start();
    }

    public synchronized void stop() {
        try {
            this.decoder.close();
        } catch (Exception e) {
            NekoMusicClient.LOGGER.error("Failed to close the decoder", e);
        }
        playbackRunning = false;  // Stop the playback thread
    }

    public synchronized void cleanup() {
        if (cleanupDone) {
            return;  // Prevent multiple cleanups
        }

        this.stop();

        AL10.alSourceStop(this.source);
        AL10.alSourceUnqueueBuffers(this.source);
        AL10.alDeleteSources(this.source);
        queue.forEach(AL10::alDeleteBuffers);
        try {
            this.stream.close();  // Close the stream
        } catch (IOException e) {
            NekoMusicClient.LOGGER.error("Failed to close the audio stream", e);
        }

        cleanupDone = true;  // Mark the cleanup as done
    }

    private void decodeLoop() {
        try {
            while (playbackRunning) {
                ByteBuffer buffer = decoder.decodeFrame();
                if (buffer == null) {
                    break;
                }
                int alBuffer = AL10.alGenBuffers();
                AL10.alBufferData(alBuffer, decoder.getOutputChannels() == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16, buffer, decoder.getOutputFrequency());
                queue.put(alBuffer);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            NekoMusicClient.LOGGER.error("Failed to decode the audio stream", e);
        } finally {
            try {
                decoder.close();
            } catch (Exception e) {
                NekoMusicClient.LOGGER.error("Failed to close the decoder", e);
            }
        }
    }

    private void playBackLoop() {
        try {
            while (playbackRunning) {
                int buffer = queue.take();  // This blocks if the queue is empty
                AL10.alSourceQueueBuffers(source, buffer);
                if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    AL10.alSourcePlay(source);
                }
            }
        } catch (InterruptedException ignored) {
        } finally {
            cleanup();
        }
    }

    public void setGain(float volume) throws IllegalStateException, IllegalArgumentException {
        if (volume < 0.0f || volume > 1.0f) {
            throw new IllegalArgumentException("Volume not valid: " + volume);
        }
        if (this.cleanupDone) {
            throw new IllegalStateException("Cannot set gain after cleanup");
        }
        AL10.alSourcef(this.source, AL10.AL_GAIN, volume);
    }

    @Override
    public void close() throws Exception {
        cleanup();
    }
}

