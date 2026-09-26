package com.mannequin.client;

import com.mannequin.client.camera.CameraKeyframe;
import com.mannequin.client.camera.CatmullRomSpline;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 样条机械滑轨插值单元测试。
 */
class CatmullRomSplineTest {

    @Test
    @DisplayName("测试样条曲线严格经过所有关键帧机位")
    void testSplinePassesThroughKeyframes() {
        CameraKeyframe k0 = new CameraKeyframe(new Vec3(0, 0, 0), 0, 0, 0, 70);
        CameraKeyframe k1 = new CameraKeyframe(new Vec3(10, 5, 2), 10, 45, 5, 50);
        CameraKeyframe k2 = new CameraKeyframe(new Vec3(20, 2, 10), -5, 90, 0, 35);

        CatmullRomSpline spline = new CatmullRomSpline(List.of(k0, k1, k2));

        CameraKeyframe start = spline.evaluate(0.0);
        assertEquals(0.0, start.position().x, 0.05);
        assertEquals(0.0, start.position().y, 0.05);
        assertEquals(0.0, start.position().z, 0.05);

        CameraKeyframe end = spline.evaluate(1.0);
        assertEquals(20.0, end.position().x, 0.05);
        assertEquals(2.0, end.position().y, 0.05);
        assertEquals(10.0, end.position().z, 0.05);

        assertTrue(spline.getTotalLength() > 20.0, "滑轨空间总长度必须大于两端欧氏距离");
    }
}
