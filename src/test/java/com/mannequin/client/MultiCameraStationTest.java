package com.mannequin.client;

import com.mannequin.client.camera.CameraStation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多机位拍摄机位模型与参数单元测试。
 */
class MultiCameraStationTest {

    @Test
    @DisplayName("测试机位数据记录完整性与镜头焦距字段")
    void testCameraStationDataIntegrity() {
        Vec3 pos = new Vec3(100.5, 65.0, -200.2);
        CameraStation station = new CameraStation(1, "机位 1 (全景广角)", pos, 45.0F, -10.0F, 5.0F, 85.0F);

        assertEquals(1, station.id());
        assertEquals("机位 1 (全景广角)", station.name());
        assertEquals(pos, station.position());
        assertEquals(45.0F, station.yaw(), 0.001F);
        assertEquals(-10.0F, station.pitch(), 0.001F);
        assertEquals(5.0F, station.roll(), 0.001F);
        assertEquals(85.0F, station.fov(), 0.001F);
    }

    @Test
    @DisplayName("测试特写机位焦距变焦参数")
    void testCloseUpCameraStation() {
        Vec3 pos = new Vec3(10.0, 64.0, 10.0);
        CameraStation closeUp = new CameraStation(2, "特写机位", pos, 180.0F, 0.0F, 0.0F, 25.0F);

        assertEquals(2, closeUp.id());
        assertEquals(25.0F, closeUp.fov(), 0.001F);
        assertTrue(closeUp.fov() < 70.0F, "特写机位视场角 FOV 应小于标准视场角");
    }
}
