package com.mannequin.client.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 漫剧片场背景音乐与曲库引擎（Studio Music & BGM Engine）。
 *
 * <p>让创作者自由指定本地音乐文件夹作为片场曲库，随时在游戏内作为拍摄配乐播放：
 * <ul>
 *   <li><b>多格式原生解码</b>：支持 MP3（JLayer纯Java硬解）、WAV（PCM立体声）、OGG（STBVorbis）；</li>
 *   <li><b>独立线程播放</b>：完全在后台线程池异步推流，绝不占用游戏主线程与渲染帧率；</li>
 *   <li><b>专业播放控制</b>：支持循环播放、单曲循环、随机乱序、实时无级音量调节与上一首/下一首切换；</li>
 *   <li><b>白纸化自适应记忆</b>：自动记忆创作者指定的本地音乐文件夹与音量偏好。</li>
 * </ul>
 */
public final class StudioMusicEngine {

    public static final StudioMusicEngine INSTANCE = new StudioMusicEngine();

    public enum LoopMode {
        REPEAT_ALL("列表循环", "🔁"),
        REPEAT_ONE("单曲循环", "🔂"),
        SHUFFLE("随机播放", "🔀"),
        ORDER("顺序播放", "📋");

        private final String displayName;
        private final String icon;

        LoopMode(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }

        public LoopMode next() {
            LoopMode[] vals = values();
            return vals[(ordinal() + 1) % vals.length];
        }
    }

    public record Track(
            String fileName,
            String title,
            Path path,
            String format,
            long fileSizeBytes
    ) {
    }

    private Path musicDirectory;
    private final List<Track> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;

    private volatile boolean isPlaying = false;
    private volatile boolean isPaused = false;
    private volatile boolean shouldStop = false;
    private volatile float volume = 0.70F; // 默认 70% 音量
    private LoopMode loopMode = LoopMode.REPEAT_ALL;

    private final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StudioMusicPlaybackThread");
        t.setDaemon(true);
        return t;
    });

    private final Random random = new Random();

    private StudioMusicEngine() {
        this.musicDirectory = getDefaultMusicDir();
        loadPreferences();
        rescanMusicDirectory();
    }

    /**
     * 获取默认曲库目录：config/mannequin/music
     */
    public static Path getDefaultMusicDir() {
        try {
            if (FMLPaths.CONFIGDIR != null && FMLPaths.CONFIGDIR.get() != null) {
                return FMLPaths.CONFIGDIR.get().resolve("mannequin/music");
            }
        } catch (Throwable ignored) {
        }
        return Paths.get("config/mannequin/music");
    }

    public Path getMusicDirectory() {
        return musicDirectory;
    }

    /**
     * 设定自定义曲库文件夹并自动扫描可用音频。
     */
    public void setMusicDirectory(Path path) {
        if (path != null) {
            this.musicDirectory = path;
            try {
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                }
            } catch (IOException ignored) {
            }
            rescanMusicDirectory();
            savePreferences();
        }
    }

    public void resetToDefaultMusicDirectory() {
        setMusicDirectory(getDefaultMusicDir());
    }

    /**
     * 在操作系统的资源管理器中直接打开当前曲库文件夹。
     */
    public void openMusicFolder() {
        try {
            if (!Files.exists(musicDirectory)) {
                Files.createDirectories(musicDirectory);
            }
            File file = musicDirectory.toFile();
            if (file.exists()) {
                Util.getPlatform().openFile(file);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 重新扫描当前曲库目录中的全部音频文件 (.mp3, .wav, .ogg)。
     */
    public synchronized void rescanMusicDirectory() {
        playlist.clear();
        if (!Files.exists(musicDirectory)) {
            try {
                Files.createDirectories(musicDirectory);
            } catch (IOException ignored) {
            }
            return;
        }

        try (Stream<Path> stream = Files.list(musicDirectory)) {
            stream.filter(p -> {
                String name = p.getFileName().toString().toLowerCase();
                return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg");
            }).forEach(p -> {
                String fileName = p.getFileName().toString();
                String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase();
                String title = fileName.substring(0, fileName.lastIndexOf('.'));
                long size = 0;
                try {
                    size = Files.size(p);
                } catch (IOException ignored) {
                }
                playlist.add(new Track(fileName, title, p, ext, size));
            });
        } catch (IOException ignored) {
        }

        playlist.sort((a, b) -> a.title().compareToIgnoreCase(b.title()));

        if (currentTrackIndex >= playlist.size()) {
            currentTrackIndex = playlist.isEmpty() ? -1 : 0;
        }
    }

    public List<Track> getPlaylist() {
        return Collections.unmodifiableList(playlist);
    }

    public int getCurrentTrackIndex() {
        return currentTrackIndex;
    }

    public Track getCurrentTrack() {
        if (currentTrackIndex >= 0 && currentTrackIndex < playlist.size()) {
            return playlist.get(currentTrackIndex);
        }
        return null;
    }

    public boolean isPlaying() {
        return isPlaying && !isPaused;
    }

    public boolean isPaused() {
        return isPlaying && isPaused;
    }

    public float getVolume() {
        return volume;
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0F, Math.min(1.0F, volume));
        savePreferences();
    }

    public LoopMode getLoopMode() {
        return loopMode;
    }

    public void setLoopMode(LoopMode loopMode) {
        this.loopMode = loopMode != null ? loopMode : LoopMode.REPEAT_ALL;
        savePreferences();
    }

    public void cycleLoopMode() {
        setLoopMode(this.loopMode.next());
    }

    /**
     * 播放指定索引的曲目。
     */
    public synchronized void playTrack(int index) {
        if (index < 0 || index >= playlist.size()) {
            return;
        }

        this.currentTrackIndex = index;
        this.shouldStop = true; // 打断正在播放的曲目
        this.isPaused = false;
        this.isPlaying = true;

        Track track = playlist.get(index);
        playbackExecutor.submit(() -> runPlayback(track));

        sendNowPlayingNotification(track);
    }

    public synchronized void togglePlayPause() {
        if (!isPlaying) {
            if (!playlist.isEmpty()) {
                playTrack(currentTrackIndex >= 0 ? currentTrackIndex : 0);
            }
        } else {
            this.isPaused = !this.isPaused;
        }
    }

    public synchronized void pause() {
        if (isPlaying) {
            this.isPaused = true;
        }
    }

    public synchronized void resume() {
        if (isPlaying && isPaused) {
            this.isPaused = false;
        }
    }

    public synchronized void stop() {
        this.shouldStop = true;
        this.isPlaying = false;
        this.isPaused = false;
    }

    public synchronized void next() {
        if (playlist.isEmpty()) {
            return;
        }
        if (loopMode == LoopMode.SHUFFLE) {
            int nextIndex = random.nextInt(playlist.size());
            playTrack(nextIndex);
        } else {
            int nextIndex = (currentTrackIndex + 1) % playlist.size();
            playTrack(nextIndex);
        }
    }

    public synchronized void previous() {
        if (playlist.isEmpty()) {
            return;
        }
        if (loopMode == LoopMode.SHUFFLE) {
            int prevIndex = random.nextInt(playlist.size());
            playTrack(prevIndex);
        } else {
            int prevIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
            playTrack(prevIndex);
        }
    }

    private void sendNowPlayingNotification(Track track) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.literal("§d[片场配乐] 🎵 正在播放: §f" + track.title() + " §7[" + track.format() + "]"),
                            true
                    );
                }
            });
        }
    }

    /**
     * 后台音频推流解码主循环。
     */
    private void runPlayback(Track track) {
        this.shouldStop = false;
        String format = track.format().toUpperCase();

        try {
            switch (format) {
                case "MP3" -> playMp3(track.path());
                case "WAV" -> playWav(track.path());
                case "OGG" -> playOgg(track.path());
            }
        } catch (Throwable e) {
            // 忽略正常打断，其它异常记录
        }

        // 自然播放完毕时的下一首流转逻辑
        if (!shouldStop && isPlaying) {
            handleTrackFinished();
        }
    }

    private synchronized void handleTrackFinished() {
        if (playlist.isEmpty()) {
            this.isPlaying = false;
            return;
        }

        switch (loopMode) {
            case REPEAT_ONE -> playTrack(currentTrackIndex);
            case REPEAT_ALL -> playTrack((currentTrackIndex + 1) % playlist.size());
            case SHUFFLE -> playTrack(random.nextInt(playlist.size()));
            case ORDER -> {
                if (currentTrackIndex + 1 < playlist.size()) {
                    playTrack(currentTrackIndex + 1);
                } else {
                    this.isPlaying = false;
                }
            }
        }
    }

    /**
     * 解码并播放 MP3 音频（基于 JLayer Bitstream 逐帧解码与实时音量缩放）。
     */
    private void playMp3(Path path) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();

            Header header = bitstream.readFrame();
            if (header == null) {
                return;
            }

            int sampleRate = header.frequency();
            int channels = (header.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format, 16384);
                line.start();

                while (!shouldStop && header != null) {
                    while (isPaused && !shouldStop) {
                        Thread.sleep(40);
                    }
                    if (shouldStop) {
                        break;
                    }

                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                    short[] buffer = output.getBuffer();
                    int len = output.getBufferLength();

                    byte[] pcmBytes = new byte[len * 2];
                    float currentVol = this.volume;
                    for (int i = 0; i < len; i++) {
                        short sample = (short) Math.max(-32768, Math.min(32767, Math.round(buffer[i] * currentVol)));
                        pcmBytes[i * 2] = (byte) (sample & 0xFF);
                        pcmBytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
                    }

                    line.write(pcmBytes, 0, pcmBytes.length);
                    bitstream.closeFrame();
                    header = bitstream.readFrame();
                }

                line.drain();
            }
        }
    }

    /**
     * 解码并播放标准 WAV/PCM 音频。
     */
    private void playWav(Path path) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path));
             AudioInputStream rawAis = AudioSystem.getAudioInputStream(in)) {

            AudioFormat baseFormat = rawAis.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2,
                    baseFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(decodedFormat, rawAis)) {
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
                try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                    line.open(decodedFormat, 16384);
                    line.start();

                    byte[] buffer = new byte[4096];
                    int read;

                    while (!shouldStop && (read = pcmStream.read(buffer, 0, buffer.length)) != -1) {
                        while (isPaused && !shouldStop) {
                            Thread.sleep(40);
                        }
                        if (shouldStop) {
                            break;
                        }

                        // 16位小端音频实时动态音量衰减
                        float currentVol = this.volume;
                        for (int i = 0; i < read - 1; i += 2) {
                            short sample = (short) ((buffer[i] & 0xFF) | (buffer[i + 1] << 8));
                            sample = (short) Math.max(-32768, Math.min(32767, Math.round(sample * currentVol)));
                            buffer[i] = (byte) (sample & 0xFF);
                            buffer[i + 1] = (byte) ((sample >> 8) & 0xFF);
                        }

                        line.write(buffer, 0, read);
                    }

                    line.drain();
                }
            }
        }
    }

    /**
     * 解码并播放 OGG Vorbis 音频（基于 LWJGL STBVorbis 原生解码）。
     */
    private void playOgg(Path path) throws Exception {
        IntBuffer channelsBuf = BufferUtils.createIntBuffer(1);
        IntBuffer sampleRateBuf = BufferUtils.createIntBuffer(1);

        ShortBuffer rawPcm = STBVorbis.stb_vorbis_decode_filename(path.toAbsolutePath().toString(), channelsBuf, sampleRateBuf);
        if (rawPcm == null) {
            return;
        }

        int channels = channelsBuf.get(0);
        int sampleRate = sampleRateBuf.get(0);
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
            line.open(format, 16384);
            line.start();

            int totalSamples = rawPcm.remaining();
            int chunkSize = 2048;
            byte[] pcmBytes = new byte[chunkSize * 2];

            while (!shouldStop && rawPcm.hasRemaining()) {
                while (isPaused && !shouldStop) {
                    Thread.sleep(40);
                }
                if (shouldStop) {
                    break;
                }

                int toRead = Math.min(chunkSize, rawPcm.remaining());
                float currentVol = this.volume;
                for (int i = 0; i < toRead; i++) {
                    short sample = rawPcm.get();
                    sample = (short) Math.max(-32768, Math.min(32767, Math.round(sample * currentVol)));
                    pcmBytes[i * 2] = (byte) (sample & 0xFF);
                    pcmBytes[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
                }

                line.write(pcmBytes, 0, toRead * 2);
            }

            line.drain();
        }
    }

    // ==================== 偏好持久化 ====================

    private static Path getPrefsPath() {
        try {
            if (FMLPaths.CONFIGDIR != null && FMLPaths.CONFIGDIR.get() != null) {
                return FMLPaths.CONFIGDIR.get().resolve("mannequin/client_prefs.nbt");
            }
        } catch (Throwable ignored) {
        }
        return Paths.get("config/mannequin/client_prefs.nbt");
    }

    public void loadPreferences() {
        try {
            Path path = getPrefsPath();
            if (path != null && Files.exists(path)) {
                CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                if (tag.contains("musicDirectory")) {
                    String customDir = tag.getString("musicDirectory");
                    if (!customDir.isBlank()) {
                        this.musicDirectory = Paths.get(customDir);
                    }
                }
                if (tag.contains("musicVolume")) {
                    this.volume = Math.max(0.0F, Math.min(1.0F, tag.getFloat("musicVolume")));
                }
                if (tag.contains("musicLoopMode")) {
                    try {
                        this.loopMode = LoopMode.valueOf(tag.getString("musicLoopMode"));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void savePreferences() {
        CompletableFuture.runAsync(() -> {
            try {
                Path path = getPrefsPath();
                if (path != null) {
                    if (path.getParent() != null) {
                        Files.createDirectories(path.getParent());
                    }
                    CompoundTag tag = new CompoundTag();
                    if (Files.exists(path)) {
                        try {
                            tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                        } catch (Exception ignored) {
                            tag = new CompoundTag();
                        }
                    }
                    tag.putString("musicDirectory", musicDirectory.toAbsolutePath().toString());
                    tag.putFloat("musicVolume", volume);
                    tag.putString("musicLoopMode", loopMode.name());
                    NbtIo.writeCompressed(tag, path);
                }
            } catch (Throwable ignored) {
            }
        });
    }
}
