package com.mannequin.client;

import com.mannequin.client.timeline.MotionFrame;
import com.mannequin.client.timeline.TimelineTrack;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 时间轴轨道与动捕帧插值单元测试。
 */
class TimelineTrackTest {

    @org.junit.jupiter.api.BeforeAll
    static void init() {
        try {
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    @Test
    @DisplayName("测试单轨运动帧采样与子帧平滑插值")
    void testTrackSampleInterpolation() {
        TimelineTrack track = new TimelineTrack("test_entity", "红人偶动捕轨");

        track.recordFrame(new MotionFrame(0, new Vec3(0, 0, 0), 0, 0, 0, 70, false));
        track.recordFrame(new MotionFrame(20, new Vec3(10, 0, 0), 10, 90, 0, 70, true));

        assertEquals(2, track.getFrameCount());

        // 采样第 10 Tick（正中间半秒处）
        MotionFrame midFrame = track.sample(10, 0.0F);
        assertNotNull(midFrame);
        assertEquals(5.0, midFrame.position().x, 0.01, "中点X坐标必须为 5.0");
        assertEquals(45.0F, midFrame.yaw(), 0.01F, "中点Yaw必须插值为 45.0度");
        assertEquals(5.0F, midFrame.pitch(), 0.01F, "中点Pitch必须插值为 5.0度");

        // 采样第 0 Tick 的子帧 partialTick 0.5F（相当于第 0.5 Tick 处）
        MotionFrame subTickFrame = track.sample(0, 0.5F);
        assertNotNull(subTickFrame);
        assertEquals(0.25, subTickFrame.position().x, 0.05);
    }

    @Test
    @DisplayName("测试轨道锁定保护与初始帧读取")
    void testTrackLockAndInitialFrame() {
        TimelineTrack track = new TimelineTrack("test_entity", "蓝人偶动捕轨");
        track.recordFrame(new MotionFrame(0, new Vec3(1, 2, 3), 0, 0, 0, 70, false));

        assertEquals(new Vec3(1, 2, 3), track.getInitialFrame().position());

        // 锁定后不可被覆写或清空
        track.setLocked(true);
        track.recordFrame(new MotionFrame(10, new Vec3(5, 5, 5), 0, 0, 0, 70, false));
        assertEquals(1, track.getFrameCount(), "锁定轨道后不可追加新帧");

        track.clear();
        assertEquals(1, track.getFrameCount(), "锁定轨道后不可被清空");
    }

    @Test
    @DisplayName("测试 MasterClockEngine 移除人偶轨道")
    void testTrackRemoval() {
        com.mannequin.client.timeline.MasterClockEngine engine = com.mannequin.client.timeline.MasterClockEngine.INSTANCE;
        String trackId = java.util.UUID.randomUUID().toString();
        engine.getOrCreateTrack(trackId, "待回收人偶轨道");
        assertTrue(engine.getTracks().containsKey(trackId));

        // 模拟实体被清除时联动移除
        boolean removed = engine.removeTrack(trackId);
        assertTrue(removed);
        assertFalse(engine.getTracks().containsKey(trackId));
    }

    @Test
    @DisplayName("测试人偶比例预设与轮转")
    void testScalePresets() {
        float[] presets = com.mannequin.entity.MannequinEntity.SCALE_PRESETS;
        assertEquals(9, presets.length);
        assertEquals(0.25F, presets[0]);
        assertEquals(1.0F, presets[3]);
        assertEquals(10.0F, presets[8]);

        // 测试描述生成
        assertEquals("100% 标准人体基准", com.mannequin.entity.MannequinEntity.getScaleDescription(1.0F));
        assertEquals("25% 手办/微缩模型", com.mannequin.entity.MannequinEntity.getScaleDescription(0.25F));
        assertEquals("1000% 摩天巨像/哥斯拉级巨物", com.mannequin.entity.MannequinEntity.getScaleDescription(10.0F));
    }
}
