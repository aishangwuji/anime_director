package com.mannequin.building;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 漫剧场景建筑蓝图序列化与文件管理助手（Building Blueprint Helper）。
 *
 * <p>本类严格专注于纯建筑结构的打包、导出、导入与一键生成：
 * <ul>
 *   <li><b>只存建筑</b>：仅打包方块、BlockState 状态（楼梯/门窗/红石等）与方块实体（箱子/告示牌等），彻底排除人偶与机位；</li>
 *   <li><b>原生兼容</b>：基于原版 {@link StructureTemplate} NBT 序列化，免疫游戏版本更新损坏；</li>
 *   <li><b>便携单文件</b>：以 GZIP 压缩存储于 {@code config/mannequin/blueprints/<名称>.nbt}，支持一键在操作系统中打开目录并直接分享给好友。</li>
 * </ul>
 */
public final class BuildingBlueprintHelper {

    public record BlueprintInfo(
            String fileName,
            String name,
            String author,
            String description,
            int sizeX,
            int sizeY,
            int sizeZ,
            long createdAt,
            long fileSizeBytes
    ) {
        public String getDimensionsText() {
            return sizeX + " × " + sizeY + " × " + sizeZ;
        }

        public long getBlockVolume() {
            return (long) sizeX * sizeY * sizeZ;
        }
    }

    private BuildingBlueprintHelper() {
    }

    /**
     * 获取或自动创建本地建筑蓝图存储目录。
     */
    public static Path getBlueprintsDir() {
        Path dir;
        try {
            if (FMLPaths.CONFIGDIR != null && FMLPaths.CONFIGDIR.get() != null) {
                dir = FMLPaths.CONFIGDIR.get().resolve("mannequin/blueprints");
            } else {
                dir = Path.of("config/mannequin/blueprints");
            }
        } catch (Throwable ignored) {
            dir = Path.of("config/mannequin/blueprints");
        }
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException ignored) {
        }
        return dir;
    }

    /**
     * 在操作系统文件管理器（Windows 资源管理器）中一键打开蓝图文件夹，方便用户复制粘贴分享文件。
     */
    public static void openBlueprintsFolder() {
        Path dir = getBlueprintsDir();
        File file = dir.toFile();
        if (file.exists()) {
            Util.getPlatform().openFile(file);
        }
    }

    /**
     * 快速扫描并列出本地目录中的所有可用建筑蓝图元数据。
     */
    public static List<BlueprintInfo> listBlueprints() {
        List<BlueprintInfo> list = new ArrayList<>();
        Path dir = getBlueprintsDir();
        if (!Files.exists(dir)) {
            return list;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".nbt"))
                    .forEach(p -> {
                        try {
                            CompoundTag root = NbtIo.readCompressed(p, NbtAccounter.unlimitedHeap());
                            CompoundTag meta = root.contains("Metadata") ? root.getCompound("Metadata") : new CompoundTag();
                            String name = meta.getString("Name");
                            if (name.isEmpty()) {
                                name = p.getFileName().toString().replace(".nbt", "");
                            }
                            String author = meta.contains("Author") ? meta.getString("Author") : "未知导演";
                            String desc = meta.contains("Description") ? meta.getString("Description") : "";
                            int sx = meta.contains("SizeX") ? meta.getInt("SizeX") : 0;
                            int sy = meta.contains("SizeY") ? meta.getInt("SizeY") : 0;
                            int sz = meta.contains("SizeZ") ? meta.getInt("SizeZ") : 0;
                            long createdAt = meta.contains("CreatedAt") ? meta.getLong("CreatedAt") : Files.getLastModifiedTime(p).toMillis();
                            long fileSize = Files.size(p);

                            list.add(new BlueprintInfo(p.getFileName().toString(), name, author, desc, sx, sy, sz, createdAt, fileSize));
                        } catch (Throwable ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }

        // 按创建时间倒序排列（最新导出的排在最前）
        list.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
        return list;
    }

    /**
     * 将两点框选的纯建筑结构序列化打包为 NBT 根标签。
     *
     * @param level       世界
     * @param posA        角点 A
     * @param posB        角点 B
     * @param name        建筑名称
     * @param author      作者
     * @param description 描述
     * @return 完整的复合 NBT 数据
     */
    public static CompoundTag exportBuilding(Level level, BlockPos posA, BlockPos posB, String name, String author, String description) {
        int minX = Math.min(posA.getX(), posB.getX());
        int minY = Math.min(posA.getY(), posB.getY());
        int minZ = Math.min(posA.getZ(), posB.getZ());
        int maxX = Math.max(posA.getX(), posB.getX());
        int maxY = Math.max(posA.getY(), posB.getY());
        int maxZ = Math.max(posA.getZ(), posB.getZ());

        BlockPos origin = new BlockPos(minX, minY, minZ);
        Vec3i size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);

        // 仅抓取方块与容器方块实体，includeEntities 强制为 false，杜绝人偶与生物被打包！
        StructureTemplate template = new StructureTemplate();
        template.fillFromWorld(level, origin, size, false, null);

        CompoundTag root = new CompoundTag();

        // 1. 元数据
        CompoundTag meta = new CompoundTag();
        meta.putString("Name", (name == null || name.isBlank()) ? "未命名建筑" : name.trim());
        meta.putString("Author", (author == null || author.isBlank()) ? "片场置景师" : author.trim());
        meta.putString("Description", description != null ? description.trim() : "");
        meta.putInt("SizeX", size.getX());
        meta.putInt("SizeY", size.getY());
        meta.putInt("SizeZ", size.getZ());
        meta.putLong("CreatedAt", System.currentTimeMillis());
        root.put("Metadata", meta);

        // 2. 纯建筑方块与方块实体数据
        CompoundTag structureTag = new CompoundTag();
        template.save(structureTag);
        root.put("Structure", structureTag);

        return root;
    }

    /**
     * 将 NBT 数据保存为本地 .nbt 蓝图文件。
     */
    public static boolean saveBlueprintFile(String fileName, CompoundTag root) {
        String cleanName = sanitizeFileName(fileName);
        if (!cleanName.endsWith(".nbt")) {
            cleanName += ".nbt";
        }

        try {
            Path targetFile = getBlueprintsDir().resolve(cleanName);
            NbtIo.writeCompressed(root, targetFile);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 删除指定建筑蓝图文件。
     */
    public static boolean deleteBlueprintFile(String fileName) {
        try {
            Path targetFile = getBlueprintsDir().resolve(fileName);
            return Files.deleteIfExists(targetFile);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 在服务端将指定蓝图建筑一键生成并放置在世界中。
     *
     * @param level           服务端世界
     * @param targetOrigin    放置基准原点（底面角点）
     * @param fileName        蓝图文件名
     * @param rotationDegrees 旋转角度（0, 90, 180, 270）
     * @return 是否成功放置
     */
    public static boolean placeBuilding(ServerLevel level, BlockPos targetOrigin, String fileName, int rotationDegrees) {
        Path file = getBlueprintsDir().resolve(fileName);
        if (!Files.exists(file)) {
            return false;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (!root.contains("Structure")) {
                return false;
            }

            CompoundTag structureTag = root.getCompound("Structure");
            StructureTemplate template = new StructureTemplate();
            template.load(level.holderLookup(Registries.BLOCK), structureTag);

            Rotation rotation = switch ((rotationDegrees % 360 + 360) % 360) {
                case 90 -> Rotation.CLOCKWISE_90;
                case 180 -> Rotation.CLOCKWISE_180;
                case 270 -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setIgnoreEntities(true);

            // 放置方块结构，带有方块更新与渲染刷新标志
            template.placeInWorld(level, targetOrigin, targetOrigin, settings, level.random, 2 | 16);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 安全过滤文件名中的特殊非法字符。
     */
    public static String sanitizeFileName(String input) {
        if (input == null || input.isBlank()) {
            return "building_" + System.currentTimeMillis();
        }
        return input.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
