package com.mannequin.client.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 漫剧片场背景音乐与曲库引擎单元测试。
 */
class StudioMusicEngineTest {

    @Test
    @DisplayName("测试循环模式循环流转与显示属性")
    void testLoopModeCycle() {
        StudioMusicEngine.LoopMode mode = StudioMusicEngine.LoopMode.REPEAT_ALL;
        assertEquals("列表循环", mode.getDisplayName());
        assertEquals("🔁", mode.getIcon());

        mode = mode.next();
        assertEquals(StudioMusicEngine.LoopMode.REPEAT_ONE, mode);
        assertEquals("单曲循环", mode.getDisplayName());
        assertEquals("🔂", mode.getIcon());

        mode = mode.next();
        assertEquals(StudioMusicEngine.LoopMode.SHUFFLE, mode);
        assertEquals("随机播放", mode.getDisplayName());
        assertEquals("🔀", mode.getIcon());

        mode = mode.next();
        assertEquals(StudioMusicEngine.LoopMode.ORDER, mode);
        assertEquals("顺序播放", mode.getDisplayName());
        assertEquals("📋", mode.getIcon());

        mode = mode.next();
        assertEquals(StudioMusicEngine.LoopMode.REPEAT_ALL, mode);
    }

    @Test
    @DisplayName("测试音量范围限制与安全钳制")
    void testVolumeClamping() {
        StudioMusicEngine engine = StudioMusicEngine.INSTANCE;

        engine.setVolume(0.50F);
        assertEquals(0.50F, engine.getVolume(), 0.001F);

        engine.setVolume(-0.25F);
        assertEquals(0.0F, engine.getVolume(), 0.001F);

        engine.setVolume(1.80F);
        assertEquals(1.0F, engine.getVolume(), 0.001F);

        engine.setVolume(0.70F);
        assertEquals(0.70F, engine.getVolume(), 0.001F);
    }

    @Test
    @DisplayName("测试自定义曲库目录扫描与多格式过滤")
    void testDirectoryScanningAndFormatFiltering(@TempDir Path tempDir) throws IOException {
        // 创建测试音频与无关文件
        Path mp3File = tempDir.resolve("Epic_Battle_Theme.mp3");
        Path wavFile = tempDir.resolve("Calm_Dialogue_Ambience.wav");
        Path oggFile = tempDir.resolve("Suspense_Trailer.ogg");
        Path textFile = tempDir.resolve("readme.txt");
        Path imageFile = tempDir.resolve("cover.png");

        Files.write(mp3File, new byte[]{1, 2, 3, 4, 5});
        Files.write(wavFile, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        Files.write(oggFile, new byte[]{1, 2, 3});
        Files.write(textFile, "some notes".getBytes());
        Files.write(imageFile, new byte[]{0, 0, 0});

        StudioMusicEngine engine = StudioMusicEngine.INSTANCE;
        Path originalDir = engine.getMusicDirectory();

        try {
            // 切换至测试目录
            engine.setMusicDirectory(tempDir);

            List<StudioMusicEngine.Track> playlist = engine.getPlaylist();
            assertEquals(3, playlist.size(), "曲库应只收录 .mp3, .wav, .ogg 文件");

            // 验证曲目信息解析
            boolean foundMp3 = false;
            boolean foundWav = false;
            boolean foundOgg = false;

            for (StudioMusicEngine.Track track : playlist) {
                if ("Epic_Battle_Theme".equals(track.title())) {
                    foundMp3 = true;
                    assertEquals("MP3", track.format());
                    assertEquals(5, track.fileSizeBytes());
                } else if ("Calm_Dialogue_Ambience".equals(track.title())) {
                    foundWav = true;
                    assertEquals("WAV", track.format());
                    assertEquals(8, track.fileSizeBytes());
                } else if ("Suspense_Trailer".equals(track.title())) {
                    foundOgg = true;
                    assertEquals("OGG", track.format());
                    assertEquals(3, track.fileSizeBytes());
                }
            }

            assertTrue(foundMp3, "应正确扫描并解析 MP3 曲目");
            assertTrue(foundWav, "应正确扫描并解析 WAV 曲目");
            assertTrue(foundOgg, "应正确扫描并解析 OGG 曲目");
        } finally {
            // 恢复原曲库目录
            engine.setMusicDirectory(originalDir);
        }
    }
}
