package com.mannequin.building;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 漫剧场景建筑蓝图序列化与管理单元测试。
 */
class BuildingBlueprintHelperTest {

    @Test
    @DisplayName("测试文件名非法字符安全过滤")
    void testSanitizeFileName() {
        assertEquals("house_test", BuildingBlueprintHelper.sanitizeFileName("house:test"));
        assertEquals("modern_villa_01", BuildingBlueprintHelper.sanitizeFileName("modern/villa*01"));
        assertTrue(BuildingBlueprintHelper.sanitizeFileName("").startsWith("building_"));
        assertTrue(BuildingBlueprintHelper.sanitizeFileName(null).startsWith("building_"));
    }

    @Test
    @DisplayName("测试蓝图文件落盘、元数据读取与清理")
    void testSaveAndListBlueprints() throws IOException {
        String testFileName = "junit_test_building.nbt";

        CompoundTag root = new CompoundTag();
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", "测试中式客栈");
        meta.putString("Author", "单元测试置景师");
        meta.putString("Description", "用于自动化测试验证的纯建筑蓝图");
        meta.putInt("SizeX", 16);
        meta.putInt("SizeY", 24);
        meta.putInt("SizeZ", 32);
        meta.putLong("CreatedAt", System.currentTimeMillis());
        root.put("Metadata", meta);
        root.put("Structure", new CompoundTag());

        // 1. 保存
        boolean saved = BuildingBlueprintHelper.saveBlueprintFile(testFileName, root);
        assertTrue(saved, "蓝图文件应该成功落盘保存");

        // 2. 列出并检查元数据
        List<BuildingBlueprintHelper.BlueprintInfo> list = BuildingBlueprintHelper.listBlueprints();
        assertFalse(list.isEmpty(), "列表不应为空");

        BuildingBlueprintHelper.BlueprintInfo found = null;
        for (BuildingBlueprintHelper.BlueprintInfo info : list) {
            if (testFileName.equals(info.fileName())) {
                found = info;
                break;
            }
        }

        assertNotNull(found, "应该能够在列表中扫描到刚刚保存的文件");
        assertEquals("测试中式客栈", found.name());
        assertEquals("单元测试置景师", found.author());
        assertEquals(16, found.sizeX());
        assertEquals(24, found.sizeY());
        assertEquals(32, found.sizeZ());
        assertEquals("16 × 24 × 32", found.getDimensionsText());
        assertEquals(16 * 24 * 32, found.getBlockVolume());

        // 3. 删除清理
        boolean deleted = BuildingBlueprintHelper.deleteBlueprintFile(testFileName);
        assertTrue(deleted, "应该能够安全删除测试文件");
    }
}
