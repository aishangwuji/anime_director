package com.mannequin.client.camera;

import com.mannequin.client.timeline.MasterClockEngine;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 多机位分镜全自动化批量渲染录像总调度（Multi-Camera Batch Runner）。
 *
 * <p>核心运作机制：
 * <ul>
 *   <li>顺序遍历机位库中的所有拍摄机位；</li>
 *   <li>对每个机位瞬间切入对应坐标、朝向与焦距，时间轴自动精准倒带至第 0 秒；</li>
 *   <li>全自动开启 MP4 管道直通录制，并触发主时钟演播；</li>
 *   <li>主时钟满档收杆后，无缝停止录制并封包当前机位 MP4，自动切入下一机位；</li>
 *   <li>全部机位跑完后，向聊天栏推送包含系统资源管理器一键超链接的完工报告。</li>
 * </ul>
 */
public final class MultiCameraBatchRunner {

    private static final Logger LOGGER = LogManager.getLogger("Mannequin-BatchRunner");
    public static final MultiCameraBatchRunner INSTANCE = new MultiCameraBatchRunner();

    public enum Status {
        IDLE,
        RECORDING_WAIT,
        RECORDING,
        FINISHED
    }

    private Status status = Status.IDLE;
    private final List<CameraStation> queue = new ArrayList<>();
    private int currentStationIndex = 0;
    private int lastRecordedTick = -1;
    private boolean canCaptureFrame = false;
    private String sessionTimestamp = "";
    private int totalStationsRecorded = 0;
    private int waitTicks = 0;
    private boolean savedHideGui = false;

    // 主视角实时自由运镜录制状态与用户偏好配置
    private boolean isLivePov = false;
    private File currentLivePovFile = null;
    private boolean cleanFeedEnabled = true; // 是否在录像时隐去所有准星/HUD/辅助线
    private int recordingFps = 20;           // 视频录制帧率 (默认 20 FPS 基准)

    private MultiCameraBatchRunner() {
        loadPreferences();
    }

    public void loadPreferences() {
        try {
            net.minecraft.nbt.CompoundTag tag = com.mannequin.client.config.ClientPreferences.INSTANCE.getRoot();
            if (tag.contains("cleanFeedEnabled")) {
                this.cleanFeedEnabled = tag.getBoolean("cleanFeedEnabled");
            }
            if (tag.contains("recordingFps")) {
                this.recordingFps = tag.getInt("recordingFps");
            }
        } catch (Exception ignored) {
        }
    }

    public void savePreferences() {
        com.mannequin.client.config.ClientPreferences.INSTANCE.updateRoot(tag -> {
            tag.putBoolean("cleanFeedEnabled", cleanFeedEnabled);
            tag.putInt("recordingFps", recordingFps);
        });
    }

    public boolean isCleanFeedEnabled() {
        return cleanFeedEnabled;
    }

    public void setCleanFeedEnabled(boolean cleanFeedEnabled) {
        this.cleanFeedEnabled = cleanFeedEnabled;
        savePreferences();
    }

    public void toggleCleanFeed() {
        setCleanFeedEnabled(!this.cleanFeedEnabled);
    }

    public int getRecordingFps() {
        return (recordingFps > 0) ? recordingFps : 20;
    }

    public void setRecordingFps(int fps) {
        this.recordingFps = Math.max(10, Math.min(120, fps));
        savePreferences();
    }

    public boolean isLivePov() {
        return isLivePov;
    }

    public boolean isLivePovRecording() {
        return isLivePov && isRunning();
    }

    public boolean isRunning() {
        return status != Status.IDLE && status != Status.FINISHED;
    }

    public Status getStatus() {
        return status;
    }

    public int getCurrentStationIndex() {
        return currentStationIndex;
    }

    public int getTotalStations() {
        return queue.size();
    }

    /**
     * 启动全自动化多机位批量录制流水线。
     */
    public boolean startBatchRecording() {
        if (isRunning()) {
            cancel();
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!Mp4VideoRecorder.isFfmpegAvailable()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("§c[分镜录制] 未在系统中检测到 FFmpeg！请先安装 FFmpeg 并配置 PATH 环境变量后再启动录像！"),
                        false
                );
            }
            return false;
        }

        queue.clear();
        queue.addAll(MultiCameraManager.INSTANCE.getStations());

        // 若用户尚未手动打任何机位点，自动截取当前视口作为默认 1 号机位
        if (queue.isEmpty()) {
            CameraStation autoStation = MultiCameraManager.INSTANCE.addStationAtCurrent("机位 1 (主控视角)");
            if (autoStation != null) {
                queue.add(autoStation);
            }
        }

        if (queue.isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c[分镜录制] 错误：无法创建有效拍摄机位！"), false);
            }
            return false;
        }

        currentStationIndex = 0;
        totalStationsRecorded = 0;
        sessionTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        this.savedHideGui = mc.options.hideGui;
        if (cleanFeedEnabled) {
            mc.options.hideGui = true; // 自动隐去原版十字准星、快捷栏与聊天框，开启纯净 Clean Feed 录像模式
        }

        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§6[分镜录制] 启动全机位 MP4 自动化流水线！共计 " + queue.size() + " 个机位..."),
                    false
            );
        }

        prepareAndStartStation(currentStationIndex);
        return true;
    }

    /**
     * 单机位独立排演录像：自动倒带、切入视角并录制该机位动作排演，生成 MP4 参考视频。
     *
     * @param station 目标机位（若为 null，则采用当前视角或活跃试看机位）
     */
    public boolean startSingleStationRecording(CameraStation station) {
        if (isRunning()) {
            cancel();
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!Mp4VideoRecorder.isFfmpegAvailable()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("§c[分镜录制] 未在系统中检测到 FFmpeg！请先安装 FFmpeg 并配置 PATH 环境变量后再启动录像！"),
                        false
                );
            }
            return false;
        }

        queue.clear();
        if (station != null) {
            queue.add(station);
        } else {
            CameraStation preview = MultiCameraManager.INSTANCE.getActivePreviewStation();
            if (preview != null) {
                queue.add(preview);
            } else if (!MultiCameraManager.INSTANCE.getStations().isEmpty()) {
                queue.add(MultiCameraManager.INSTANCE.getStations().get(0));
            } else {
                CameraStation autoStation = MultiCameraManager.INSTANCE.addStationAtCurrent("机位_实拍");
                if (autoStation != null) {
                    queue.add(autoStation);
                }
            }
        }

        if (queue.isEmpty()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("§c[分镜录制] 错误：无法确定拍摄机位！"), false);
            }
            return false;
        }

        currentStationIndex = 0;
        totalStationsRecorded = 0;
        sessionTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        this.savedHideGui = mc.options.hideGui;
        if (cleanFeedEnabled) {
            mc.options.hideGui = true; // 自动隐去原版十字准星、快捷栏与聊天框，开启纯净 Clean Feed 录像模式
        }

        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§6[分镜录制] 开始录制【" + queue.get(0).name() + "】MP4 分镜参考视频..."),
                    false
            );
        }

        prepareAndStartStation(currentStationIndex);
        return true;
    }

    /**
     * 为当前索引机位配置视角、倒带并开启录制。
     */
    private void prepareAndStartStation(int index) {
        if (index >= queue.size()) {
            finishBatch();
            return;
        }

        CameraStation station = queue.get(index);
        MultiCameraManager.INSTANCE.switchToStation(station.id());

        // 时间轴强制归零倒带
        MasterClockEngine.INSTANCE.rewindToStart();

        // 规范化安全文件名
        String safeName = station.name().replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        String filename = String.format("take_%s_cam%d_%s.mp4", sessionTimestamp, station.id(), safeName);
        File targetFile = new File(MultiCameraManager.INSTANCE.getExportDirectory(), filename);

        boolean started = Mp4VideoRecorder.INSTANCE.start(targetFile, getRecordingFps());
        if (!started) {
            LOGGER.error("Failed to start recorder for station {}", station.name());
            // 跳过该机位继续下一个
            currentStationIndex++;
            prepareAndStartStation(currentStationIndex);
            return;
        }

        this.lastRecordedTick = -1;
        this.status = Status.RECORDING_WAIT;
        this.waitTicks = 6; // 等待 6 帧让渲染缓冲区与实体姿态完全归位
        this.canCaptureFrame = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§e[机位录制中 (" + (index + 1) + "/" + queue.size() + ")] " + station.name() + " -> " + filename),
                    true
            );
        }
    }

    /**
     * 启动主视角自由运镜实时录制 (Live POV Recording)。
     *
     * <p>在此模式下，导演拥有自由相机的 100% 实时飞行运镜权，
     * 同时场景演员与时间轴自动同步开演，捕获的纯净视口帧将实时喂入 FFmpeg 编码器。
     */
    public boolean startLivePovRecording() {
        if (isRunning()) {
            if (isLivePov) {
                stopLivePovRecording();
                return true;
            } else {
                cancel();
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (!Mp4VideoRecorder.isFfmpegAvailable()) {
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.literal("§c[主视角录制] 未在系统中检测到 FFmpeg！请先安装 FFmpeg 并配置 PATH 环境变量后再启动录像！"),
                        false
                );
            }
            return false;
        }

        // 自动激活自由相机（若尚未开启）
        if (!DirectorCameraController.INSTANCE.isCameraActive()) {
            DirectorCameraController.INSTANCE.toggleCamera();
        }

        this.isLivePov = true;
        this.sessionTimestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = String.format("take_%s_live_pov.mp4", sessionTimestamp);
        this.currentLivePovFile = new File(MultiCameraManager.INSTANCE.getExportDirectory(), filename);

        boolean started = Mp4VideoRecorder.INSTANCE.start(currentLivePovFile, getRecordingFps());
        if (!started) {
            LOGGER.error("Failed to start Live POV recorder to {}", currentLivePovFile.getAbsolutePath());
            this.isLivePov = false;
            return false;
        }

        this.savedHideGui = mc.options.hideGui;
        if (cleanFeedEnabled) {
            mc.options.hideGui = true;
        }

        this.lastRecordedTick = -1;
        this.status = Status.RECORDING_WAIT;
        this.waitTicks = 4;
        this.canCaptureFrame = false;

        // 倒带时间轴并准备开演
        MasterClockEngine.INSTANCE.rewindToStart();

        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("§c[🔴 主视角录像中] 已开启自由运镜实时录制！按 [F10] 或快捷菜单随时停止，时间轴结束后将自动封包。"),
                    true
            );
        }
        return true;
    }

    public void stopLivePovRecording() {
        if (isLivePov && isRunning()) {
            finishLivePov();
        }
    }

    private void finishLivePov() {
        Mp4VideoRecorder.INSTANCE.stop();
        this.status = Status.FINISHED;
        this.isLivePov = false;

        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = savedHideGui; // 恢复原有界面显隐

        if (mc.player != null && currentLivePovFile != null) {
            File exportDir = MultiCameraManager.INSTANCE.getExportDirectory();

            mc.player.displayClientMessage(Component.literal("§a========================================"), false);
            mc.player.displayClientMessage(
                    Component.literal("§a[主视角录制完毕] 成功直出导演自由运镜 MP4 视频！"),
                    false
            );

            MutableComponent openFileLink = Component.literal("§b§n[点击打开主视角录制视频: " + currentLivePovFile.getName() + "]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, currentLivePovFile.getAbsolutePath()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击在播放器中播放此视频")))
                    );
            mc.player.displayClientMessage(openFileLink, false);

            MutableComponent openDirLink = Component.literal("§7[打开保存目录: " + exportDir.getAbsolutePath() + "]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, exportDir.getAbsolutePath()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("在 Windows 资源管理器中打开此文件夹")))
                    );
            mc.player.displayClientMessage(openDirLink, false);
            mc.player.displayClientMessage(Component.literal("§a========================================"), false);
        }

        LOGGER.info("[MultiCameraBatchRunner] Live POV recording finished: {}", currentLivePovFile != null ? currentLivePovFile.getName() : "null");
    }

    /**
     * 逻辑 Tick 推进钩子：标记当前渲染帧需要捕获并推送至 FFmpeg 管道。
     */
    public void onClientTick() {
        if (status == Status.RECORDING && MasterClockEngine.INSTANCE.getState() == MasterClockEngine.State.PLAYING) {
            canCaptureFrame = true;
        }
    }

    /**
     * 逐帧捕获监听器（在渲染完成后抓帧并喂入 FFmpeg 管道）。
     *
     * @param event NeoForge 渲染帧后置事件
     */
    public void onRenderFramePost(RenderFrameEvent.Post event) {
        if (status == Status.IDLE || status == Status.FINISHED) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            cancel();
            return;
        }

        if (status == Status.RECORDING_WAIT) {
            waitTicks--;
            if (waitTicks <= 0) {
                status = Status.RECORDING;
                canCaptureFrame = true;
                MasterClockEngine.INSTANCE.play();
            }
            return;
        }

        if (status == Status.RECORDING) {
            MasterClockEngine.State engineState = MasterClockEngine.INSTANCE.getState();

            // 当主时钟正在播放时，按客户端逻辑 Tick (20 FPS 标准基准) 抓取每一帧动作状态与子帧插值姿态
            if (engineState == MasterClockEngine.State.PLAYING) {
                if (canCaptureFrame) {
                    canCaptureFrame = false;
                    NativeImage frame = Screenshot.takeScreenshot(mc.getMainRenderTarget());
                    Mp4VideoRecorder.INSTANCE.recordFrame(frame); // 异步提交至后台队列，由后台写线程关闭释放
                }
            } else {
                if (isLivePov) {
                    // 主视角模式下，主时钟演播到达总时长，自动收尾并封包视频
                    finishLivePov();
                } else {
                    // 主时钟演播到达总时长，自动停止当前机位录像
                    Mp4VideoRecorder.INSTANCE.stop();
                    totalStationsRecorded++;

                    currentStationIndex++;
                    if (currentStationIndex < queue.size()) {
                        prepareAndStartStation(currentStationIndex);
                    } else {
                        finishBatch();
                    }
                }
            }
        }
    }

    /**
     * 全部机位录制完毕，生成完工汇报与可点击打开文件夹的超链接。
     */
    private void finishBatch() {
        Mp4VideoRecorder.INSTANCE.stop();
        status = Status.FINISHED;

        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = savedHideGui; // 恢复玩家原有的 GUI 显隐设置

        if (mc.player != null) {
            File exportDir = MultiCameraManager.INSTANCE.getExportDirectory();

            mc.player.displayClientMessage(Component.literal("§a========================================"), false);
            mc.player.displayClientMessage(
                    Component.literal("§a[分镜录制完毕] 成功直出 " + totalStationsRecorded + " 个机位的 MP4 参考视频！"),
                    false
            );

            MutableComponent openDirLink = Component.literal("§b§n[点击在此打开 MP4 视频保存目录: " + exportDir.getAbsolutePath() + "]")
                    .withStyle(style -> style
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, exportDir.getAbsolutePath()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("在 Windows 资源管理器中打开此文件夹")))
                    );
            mc.player.displayClientMessage(openDirLink, false);
            mc.player.displayClientMessage(
                    Component.literal("§7提示：视频采用 H.264 编码，已自动隐去所有辅助框与准星，直出纯净无暇参考视频。"),
                    false
            );
            mc.player.displayClientMessage(Component.literal("§a========================================"), false);
        }

        LOGGER.info("[MultiCameraBatchRunner] Batch recording finished with {} videos.", totalStationsRecorded);
    }

    /**
     * 强制中断当前批量录制或主视角录像。
     */
    public void cancel() {
        Mp4VideoRecorder.INSTANCE.stop();
        boolean wasLivePov = isLivePov;
        status = Status.IDLE;
        isLivePov = false;
        queue.clear();
        currentStationIndex = 0;

        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = savedHideGui; // 恢复玩家原有的 GUI 显隐设置

        if (mc.player != null) {
            if (wasLivePov) {
                mc.player.displayClientMessage(Component.literal("§c[主视角录制] 已手动中断主视角运镜录像！"), true);
            } else {
                mc.player.displayClientMessage(Component.literal("§c[分镜录制] 已手动中断批量机位录制！"), true);
            }
        }
    }
}
