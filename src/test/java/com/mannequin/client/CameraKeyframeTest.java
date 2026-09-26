package com.mannequin.client;

import com.mannequin.client.camera.CameraKeyframe;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 导演相机关键帧与插值单元测试。
 */
class CameraKeyframeTest {

    @Test
    @DisplayName("测试相机关键帧线性插值与最短偏航角环绕")
    void testCameraKeyframeLerpShortestYaw() {
        // 从 350° 插值到 10°，跨越 360°/0° 边界，最短角位移应为 +20°
        CameraKeyframe f0 = new CameraKeyframe(new Vec3(0, 0, 0), 0, 350.0F, 0, 70.0F);
        CameraKeyframe f1 = new CameraKeyframe(new Vec3(10, 20, 30), 10.0F, 10.0F, 15.0F, 90.0F);

        // 中点插值 (t = 0.5)
        CameraKeyframe mid = CameraKeyframe.lerp(f0, f1, 0.5);

        assertEquals(5.0, mid.position().x, 0.01);
        assertEquals(10.0, mid.position().y, 0.01);
        assertEquals(15.0, mid.position().z, 0.01);
        assertEquals(5.0F, mid.pitch(), 0.01F);
        assertEquals(7.5F, mid.roll(), 0.01F);
        assertEquals(80.0F, mid.fov(), 0.01F);

        // 350° + 10° = 360° ≡ 0°，并确保在 [-180, 180] 范围内
        assertEquals(0.0F, mid.yaw(), 0.05F, "跨界中点偏航角应为 0 度");
        assertTrue(mid.yaw() >= -180.0F && mid.yaw() <= 180.0F, "偏航角必须严格规范在 [-180, 180] 范围内");
    }

    @Test
    @DisplayName("测试偏航角环绕无界漂移抑制")
    void testYawNoDriftBeyond180() {
        // 从 170° 到 -170°（位移为 +20°），在 t=0.75 处，rawYaw = 170 + 15 = 185° -> 规范后应为 -175°
        CameraKeyframe f0 = new CameraKeyframe(Vec3.ZERO, 0, 170.0F, 0, 70.0F);
        CameraKeyframe f1 = new CameraKeyframe(Vec3.ZERO, 0, -170.0F, 0, 70.0F);

        CameraKeyframe result = CameraKeyframe.lerp(f0, f1, 0.75);
        assertEquals(-175.0F, result.yaw(), 0.1F, "超过 180° 应自动翻转至负角 [-180, 180]");
        assertTrue(result.yaw() >= -180.0F && result.yaw() <= 180.0F);
    }

    @Test
    @DisplayName("测试基于原生贴图通过 FFmpeg 管道生成 MP4 视频的完整性")
    void testFfmpegVideoPiping() {
        File outMp4 = new File("build/test_ffmpeg.mp4");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-framerate", "20",
                    "-f", "rawvideo",
                    "-pix_fmt", "rgba",
                    "-s", "64x64",
                    "-i", "-",
                    "-c:v", "libx264",
                    "-pix_fmt", "yuv420p",
                    "-preset", "ultrafast",
                    "-crf", "18",
                    outMp4.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            Thread reader = new Thread(() -> {
                try (var is = p.getInputStream()) {
                    is.transferTo(System.out);
                } catch (Exception ignored) {}
            });
            reader.start();

            var pixelsField = com.mojang.blaze3d.platform.NativeImage.class.getDeclaredField("pixels");
            pixelsField.setAccessible(true);

            try (var stdin = p.getOutputStream()) {
                var channel = java.nio.channels.Channels.newChannel(stdin);
                for (int i = 0; i < 20; i++) {
                    try (com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(64, 64, false)) {
                        img.fillRect(0, 0, 64, 64, 0xFF00FF00 | (i * 10));
                        long ptr = pixelsField.getLong(img);
                        java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memByteBuffer(ptr, 64 * 64 * 4);
                        channel.write(buf);
                    }
                }
            }

            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            reader.join(2000);
            System.out.println("OUTPUT_MP4_SIZE: " + outMp4.length());
            assertTrue(outMp4.exists() && outMp4.length() > 0, "MP4 file should be created and non-empty");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            outMp4.delete();
        }
    }

    @Test
    @DisplayName("测试相机关键帧 NBT 序列化与反序列化完整性")
    void testCameraKeyframeNbtRoundtrip() {
        CameraKeyframe kf = new CameraKeyframe(new Vec3(12.5, 64.0, -108.3), -15.5F, 90.0F, 12.0F, 45.0F);
        net.minecraft.nbt.CompoundTag tag = kf.toNbt();
        CameraKeyframe restored = CameraKeyframe.fromNbt(tag);

        assertEquals(kf.position().x, restored.position().x, 0.001);
        assertEquals(kf.position().y, restored.position().y, 0.001);
        assertEquals(kf.position().z, restored.position().z, 0.001);
        assertEquals(kf.pitch(), restored.pitch(), 0.001F);
        assertEquals(kf.yaw(), restored.yaw(), 0.001F);
        assertEquals(kf.roll(), restored.roll(), 0.001F);
        assertEquals(kf.fov(), restored.fov(), 0.001F);
    }

    @Test
    @DisplayName("测试机位分镜站台 NBT 序列化与反序列化完整性")
    void testCameraStationNbtRoundtrip() {
        com.mannequin.client.camera.CameraStation station = new com.mannequin.client.camera.CameraStation(
                3, "特写机位A", new Vec3(100.5, 70.0, 200.25), 45.0F, -20.0F, 5.0F, 35.0F
        );
        net.minecraft.nbt.CompoundTag tag = station.toNbt();
        com.mannequin.client.camera.CameraStation restored = com.mannequin.client.camera.CameraStation.fromNbt(tag);

        assertEquals(station.id(), restored.id());
        assertEquals(station.name(), restored.name());
        assertEquals(station.position().x, restored.position().x, 0.001);
        assertEquals(station.position().y, restored.position().y, 0.001);
        assertEquals(station.position().z, restored.position().z, 0.001);
        assertEquals(station.yaw(), restored.yaw(), 0.001F);
        assertEquals(station.pitch(), restored.pitch(), 0.001F);
        assertEquals(station.roll(), restored.roll(), 0.001F);
        assertEquals(station.fov(), restored.fov(), 0.001F);
    }

    @Test
    @DisplayName("检查 NativeImage 的原生内存和像素访问方法")
    void testInspectNativeImage() {
        try (com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(16, 16, false)) {
            var pixelsField = img.getClass().getDeclaredField("pixels");
            pixelsField.setAccessible(true);
            long ptr = pixelsField.getLong(img);
            System.out.println("Pixels pointer: " + ptr);

            var sizeField = img.getClass().getDeclaredField("size");
            sizeField.setAccessible(true);
            long size = sizeField.getLong(img);
            System.out.println("Size in bytes: " + size);

            java.nio.ByteBuffer buf = org.lwjgl.system.MemoryUtil.memByteBuffer(ptr, (int) size);
            System.out.println("Direct ByteBuffer capacity: " + buf.capacity());
            assertEquals(16 * 16 * 4, size);
            assertEquals(16 * 16 * 4, buf.capacity());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
