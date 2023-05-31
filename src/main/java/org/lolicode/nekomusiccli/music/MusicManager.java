package org.lolicode.nekomusiccli.music;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import org.lolicode.nekomusiccli.NekoMusicClient;
import org.lolicode.nekomusiccli.hud.HudUtils;
import org.lolicode.nekomusiccli.music.player.AudioPlayer;

import java.io.ByteArrayInputStream;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class MusicManager {
    private final AtomicReference<AudioPlayer> playerRef = new AtomicReference<>();
    private volatile boolean isPlaying = false;
    public volatile MusicObj currentMusic = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(NekoMusicClient.MOD_NAME + " Music-Manager").build());
    private final BlockingDeque<Future<?>> futures = new LinkedBlockingDeque<>();
    private volatile boolean isDisposed = false;

    public synchronized void play(MusicObj music) {
        if (this.isDisposed) {
            NekoMusicClient.LOGGER.error("MusicManager is disposed");
            return;
        }
        if (this.isPlaying) {
            this.stop();
        }
        if (music == null) {
            NekoMusicClient.LOGGER.error("Music is null");
            return;
        }
        stopVanillaMusic();
        this.isPlaying = true;
        this.currentMusic = music;

        this.futures.add(this.executor.submit(() -> {
            try (ByteArrayInputStream stream = NekoMusicClient.netUtils.getMusicStream(music)) {
                if (stream == null) {
                    NekoMusicClient.LOGGER.error("Failed to get music stream");
                    return;
                }
                AudioPlayer player = new AudioPlayer(stream);
                playerRef.set(player);
                // Start hud right before play to sync lyric and music
                if (NekoMusicClient.config.enableHud) {
                    HudUtils hudUtils = NekoMusicClient.hudUtilsRef.get();
                    if (hudUtils == null) {
                        hudUtils = new HudUtils();
                        NekoMusicClient.hudUtilsRef.set(hudUtils);
                    }
                    hudUtils.setMusic(music);
                }
                player.play();
                player.setGain(getVolume());
            } catch (Exception e) {
                NekoMusicClient.LOGGER.error("Failed to play music", e);
                stop();
            }
        }));
    }

    public synchronized void stop() {
        AudioPlayer player = this.playerRef.getAndSet(null);
        this.futures.forEach(f -> {
            if (!f.isDone()) {
                f.cancel(true);
            }
        });
        this.futures.clear();
        if (player != null) {
            player.stop();
        }
        this.isPlaying = false;
        if (this.currentMusic != null) {
            this.currentMusic = null;
        }
        if (NekoMusicClient.hudUtilsRef.get() != null) {
            NekoMusicClient.hudUtilsRef.get().close();
        }
    }

    public synchronized void dispose() {
        this.stop();
        this.executor.shutdownNow();
        this.isDisposed = true;
    }

    public static float getVolume() {
        return MinecraftClient.getInstance().options.getSoundVolume(SoundCategory.RECORDS);
    }

    public void setVolume(float volume) {
        AudioPlayer player = this.playerRef.get();
        if (player != null) {
            try {
                player.setGain(volume);
            } catch (Exception e) {
                NekoMusicClient.LOGGER.error("Failed to set volume", e);
            }
        }
    }

    public static void stopVanillaMusic() {
        MinecraftClient.getInstance().getSoundManager().stopSounds(null, SoundCategory.MUSIC);
        MinecraftClient.getInstance().getSoundManager().stopSounds(null, SoundCategory.RECORDS);
    }
}
