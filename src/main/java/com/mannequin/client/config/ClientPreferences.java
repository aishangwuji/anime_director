package com.mannequin.client.config;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.neoforged.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 客户端全局偏好配置中心（ClientPreferences）。
 *
 * <p>解决多模块（飞控、配乐、多机位、时间轴、工作区）各自并发异步读写同一个 {@code client_prefs.nbt}
 * 导致的数据丢失、字段相互覆盖与 GZIP 文件损坏竞态。
 *
 * <p>核心机制：
 * <ul>
 *   <li><b>内存主缓存与同步保护</b>：内存中常驻一份根 {@link CompoundTag}，所有读取与写入均在内存中即时生效；</li>
 *   <li><b>300ms 写入防抖（Debounce）</b>：高频拖动滑动条时（一秒触发数十次），仅合并在停顿 300ms 后触发一次物理磁盘写入；</li>
 *   <li><b>兼容 GZIP 压缩与原子文件替换</b>：支持原版 NBT 压缩流，落盘时先写入 {@code .tmp} 临时文件，完成后通过
 *       {@link StandardCopyOption#ATOMIC_MOVE} 原子替换原文件，彻底杜绝写坏文件。</li>
 * </ul>
 */
public final class ClientPreferences {

    private static final Logger LOGGER = LogManager.getLogger("Mannequin-ClientPrefs");
    public static final ClientPreferences INSTANCE = new ClientPreferences();

    private final Object lock = new Object();
    private CompoundTag rootTag = new CompoundTag();
    private boolean loaded = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Mannequin-PrefsFlusher");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingFlush = null;

    private ClientPreferences() {
    }

    private Path getStoragePath() {
        try {
            if (FMLPaths.CONFIGDIR != null && FMLPaths.CONFIGDIR.get() != null) {
                return FMLPaths.CONFIGDIR.get().resolve("mannequin/client_prefs.nbt");
            }
        } catch (Throwable ignored) {
        }
        return Paths.get("config/mannequin/client_prefs.nbt");
    }

    private void ensureLoadedLocked() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = getStoragePath();
        File file = path.toFile();
        if (file.exists() && file.isFile() && file.length() > 0) {
            try {
                this.rootTag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
                return;
            } catch (Exception eCompressed) {
                try {
                    CompoundTag tag = NbtIo.read(path);
                    if (tag != null) {
                        this.rootTag = tag;
                        return;
                    }
                } catch (Exception ePlain) {
                    LOGGER.warn("Failed to read client_prefs.nbt, resetting to empty config", eCompressed);
                }
            }
        }
        this.rootTag = new CompoundTag();
    }

    /**
     * 获取全局配置根标签副本（深拷贝，读操作安全无竞态）。
     */
    public CompoundTag getRoot() {
        synchronized (lock) {
            ensureLoadedLocked();
            return rootTag.copy();
        }
    }

    /**
     * 原子更新配置根标签中的内容，并触发 300ms 磁盘防抖写入。
     */
    public void updateRoot(Consumer<CompoundTag> updater) {
        synchronized (lock) {
            ensureLoadedLocked();
            updater.accept(rootTag);
            scheduleFlushLocked();
        }
    }

    /**
     * 获取指定模块配置区段的副本（深拷贝）。
     */
    public CompoundTag getSection(String sectionKey) {
        synchronized (lock) {
            ensureLoadedLocked();
            if (rootTag.contains(sectionKey, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                return rootTag.getCompound(sectionKey).copy();
            }
            return new CompoundTag();
        }
    }

    /**
     * 更新指定模块配置区段。
     */
    public void updateSection(String sectionKey, Consumer<CompoundTag> updater) {
        synchronized (lock) {
            ensureLoadedLocked();
            CompoundTag section = rootTag.contains(sectionKey, net.minecraft.nbt.Tag.TAG_COMPOUND)
                    ? rootTag.getCompound(sectionKey)
                    : new CompoundTag();
            updater.accept(section);
            rootTag.put(sectionKey, section);
            scheduleFlushLocked();
        }
    }

    private void scheduleFlushLocked() {
        if (pendingFlush != null && !pendingFlush.isDone()) {
            pendingFlush.cancel(false);
        }
        pendingFlush = scheduler.schedule(this::flushToDisk, 300, TimeUnit.MILLISECONDS);
    }

    /**
     * 立即将内存中的配置同步刷盘（支持退出游戏或测试时强制持久化）。
     */
    public void flushImmediately() {
        synchronized (lock) {
            if (pendingFlush != null) {
                pendingFlush.cancel(false);
                pendingFlush = null;
            }
        }
        flushToDisk();
    }

    private void flushToDisk() {
        CompoundTag snapshot;
        synchronized (lock) {
            snapshot = rootTag.copy();
        }

        Path targetPath = getStoragePath();
        try {
            Path parent = targetPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            Path tempPath = targetPath.resolveSibling(targetPath.getFileName().toString() + ".tmp");
            NbtIo.writeCompressed(snapshot, tempPath);

            try {
                Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException atomicEx) {
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to safely write client_prefs.nbt to disk", e);
        }
    }
}
