package com.mannequin.client;

import com.mannequin.client.camera.DampedValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 物理指数阻尼插值单元测试。
 */
class DampedValueTest {

    @Test
    @DisplayName("测试标量阻尼随时间单调收敛至目标值")
    void testScalarConvergence() {
        double current = 0.0;
        double target = 100.0;
        double lambda = 8.0;
        double dt = 0.016; // 约 60 FPS

        for (int i = 0; i < 60; i++) {
            double next = DampedValue.update(current, target, lambda, dt);
            assertTrue(next > current, "数值应单调递增逼近目标");
            current = next;
        }

        assertEquals(target, current, 1.0, "经过1秒后应收敛至目标值附近");
    }

    @Test
    @DisplayName("测试跨越 180 度边界的最短路径角度插值")
    void testAngleShortestPathWrapAround() {
        float current = 175.0F;
        float target = -175.0F; // 实际跨度仅有 10 度，而不是反向转 350 度
        double lambda = 10.0;
        double dt = 0.05;

        float result = DampedValue.updateAngle(current, target, lambda, dt);
        // 圆弧角距离断言
        float diff = Math.abs((result - current) % 360.0F);
        if (diff > 180.0F) {
            diff = 360.0F - diff;
        }
        assertTrue(diff < 20.0F, "角度插值必须走最短路径，不得反向自转一大圈");
    }
}
