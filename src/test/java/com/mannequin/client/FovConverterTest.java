package com.mannequin.client;

import com.mannequin.client.camera.FovConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 镜头焦距与视角换算单元测试。
 */
class FovConverterTest {

    @Test
    @DisplayName("测试经典电影焦段双向换算的可逆性")
    void testFovFocalLengthReversibility() {
        double[] testFocals = { 18.0, 24.0, 35.0, 50.0, 85.0, 135.0 };

        for (double focal : testFocals) {
            double fov = FovConverter.focalLengthToFov(focal);
            double backFocal = FovConverter.fovToFocalLength(fov);
            assertEquals(focal, backFocal, 1e-4, "焦距换算往返误差必须在 0.0001mm 以内");
        }
    }

    @Test
    @DisplayName("测试焦段语义化分类描述")
    void testFocalDescription() {
        assertTrue(FovConverter.getFocalLengthDescription(16.0).contains("超广角"));
        assertTrue(FovConverter.getFocalLengthDescription(24.0).contains("广角"));
        assertTrue(FovConverter.getFocalLengthDescription(35.0).contains("人文纪实"));
        assertTrue(FovConverter.getFocalLengthDescription(50.0).contains("标准人像"));
        assertTrue(FovConverter.getFocalLengthDescription(85.0).contains("黄金人像"));
    }
}
