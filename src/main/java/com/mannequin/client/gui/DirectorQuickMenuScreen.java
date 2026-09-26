package com.mannequin.client.gui;

import com.mannequin.client.camera.CameraFlightStyle;
import com.mannequin.client.camera.DirectorCameraController;
import com.mannequin.client.camera.FpvFlightController;
import com.mannequin.client.camera.MultiCameraBatchRunner;
import com.mannequin.client.camera.MultiCameraManager;
import com.mannequin.client.gui.tutorial.DirectorTutorialScreen;
import com.mannequin.client.input.ModKeyMappings;
import com.mannequin.client.persistence.StudioPersistenceManager;
import com.mannequin.client.studio.PureStudioManager;
import com.mannequin.client.timeline.MasterClockEngine;
import com.mannequin.client.timeline.PuppeteerController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;

/**
 * 漫剧导演快捷操作中心（Director Control Hub / Quick Menu）。
 *
 * <p>按默认快捷键 [C]（可自由在原版按键设置中自定义修改）一键呼出，
 * 将全模组所有核心运镜、动捕、排演、隐身、画幅、多机位分镜及 MP4 参考视频直出统一聚合于一个控制台，
 * 彻底免除创作者记忆繁杂按键的负担。
 */
public class DirectorQuickMenuScreen extends Screen {

    public DirectorQuickMenuScreen() {
        super(Component.literal("漫剧导演快捷操作中心"));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(450, width - 16);
        int panelHeight = Math.min(235, height - 12);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        DirectorCameraController cam = DirectorCameraController.INSTANCE;
        PuppeteerController puppeteer = PuppeteerController.INSTANCE;
        MasterClockEngine clock = MasterClockEngine.INSTANCE;
        TimelineHudOverlay hud = TimelineHudOverlay.INSTANCE;
        MultiCameraManager multiCam = MultiCameraManager.INSTANCE;
        MultiCameraBatchRunner batchRunner = MultiCameraBatchRunner.INSTANCE;

        // 顶部右上角 Clean Feed 纯净无UI录制开关
        boolean cleanFeed = batchRunner.isCleanFeedEnabled();
        addRenderableWidget(Button.builder(
                Component.literal("🎬 纯净 [" + (cleanFeed ? "§a开启" : "§7关闭") + "§r]"),
                btn -> {
                    batchRunner.toggleCleanFeed();
                    rebuildWidgets();
                })
                .bounds(startX + panelWidth - 138, startY + 4, 112, 18)
                .tooltip(Tooltip.create(Component.literal("§6★ 纯净录像模式 (Clean Feed)\n§7开启时：录制时自动隐藏准星、HUD、快捷栏与黄色机位线，直出无暇纯净视频\n关闭时：保留界面元素录像")))
                .build());

        // 顶部右上角关闭按钮 (✕)
        addRenderableWidget(Button.builder(Component.literal("✕"), btn -> onClose())
                .bounds(startX + panelWidth - 22, startY + 4, 18, 18)
                .tooltip(Tooltip.create(Component.literal("§c关闭快捷控制台 (C / ESC)")))
                .build());

        // 3 列 × 7 行紧凑动作网格布局
        int cols = 3;
        int gridStartX = startX + 12;
        int gridStartY = startY + 34;
        int btnWidth = (panelWidth - 24 - 10) / cols;
        int btnHeight = 20;
        int gapY = 4;
        int gapX = 5;

        // === 第 1 行：基础镜头与飞控航速 ===
        int r0Y = gridStartY;
        // 1. 上帝视角自由相机 (F6)
        boolean camActive = cam.isCameraActive();
        addRenderableWidget(Button.builder(
                Component.literal("🎬 上帝相机 [" + (camActive ? "§a开启" : "§7退出") + "§r]"),
                btn -> {
                    cam.toggleCamera();
                    rebuildWidgets();
                })
                .bounds(gridStartX, r0Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[F6] 切换上帝视角自由相机\n§7脱离角色本体，进入 3D 自由悬浮飞行调度与无级变焦")))
                .build());

        // 2. 运镜风格切换：防抖平稳视角 vs 穿越机视角 (F9)
        CameraFlightStyle style = FpvFlightController.INSTANCE.getFlightStyle();
        addRenderableWidget(Button.builder(
                Component.literal("🎥 风格 [" + style.getDisplayName() + "§r]"),
                btn -> {
                    FpvFlightController.INSTANCE.toggleFlightStyle();
                    rebuildWidgets();
                })
                .bounds(gridStartX + btnWidth + gapX, r0Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[F9] 切换自由相机运镜操控风格\n" +
                        "§a[🛡 防抖平稳]：三轴水平防抖，锁定零侧倾，匀速平稳推进，即走即停\n" +
                        "§6[🚁 穿越机]：气动转弯侧倾，推力加速与惯性滑翔漂移，高动态特技运镜")))
                .build());

        // 3. 自由相机航速档位与微移预设 (J)
        com.mannequin.client.camera.SpeedGear gear = FpvFlightController.INSTANCE.getSpeedGear();
        addRenderableWidget(Button.builder(
                Component.literal("🚀 航速 [" + gear.getDisplayName() + "]"),
                btn -> {
                    FpvFlightController.INSTANCE.cycleSpeedGear();
                    rebuildWidgets();
                })
                .bounds(gridStartX + (btnWidth + gapX) * 2, r0Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[J] 切换自由相机飞行航速档位\n" +
                        "§a[0.5m/s 超微移]：毫米/厘米级极微位移，轻按 A/D 不跳格\n" +
                        "§b[2.0m/s 慢推拉]：电影级慢速推轨与子弹时间运镜\n" +
                        "§6[6.0m/s 标准]：标准航速巡航拍摄\n" +
                        "§d[16.0m/s 高速]：大场景全景飞行调度\n" +
                        "§e★ 技巧：按住 [Alt] 键可随时切入 0.25x 极端微移爬行！")))
                .build());

        // === 第 2 行：附身操纵与动作捕捉 ===
        int r1Y = gridStartY + (btnHeight + gapY);
        // 4. 准星附身受控人偶 (G)
        boolean possessing = puppeteer.isPossessing();
        addRenderableWidget(Button.builder(
                Component.literal("🎭 附身人偶 [" + (possessing ? "§a附身中" : "§7未附身") + "§r]"),
                btn -> {
                    onClose();
                    puppeteer.togglePossession();
                })
                .bounds(gridStartX, r1Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[G] 附身准星所指人偶\n§7接管目标人偶走位与动作，退出菜单后直接操控")))
                .build());

        // 5. 动捕录制开关 (K)
        boolean recording = puppeteer.isRecording();
        addRenderableWidget(Button.builder(
                Component.literal("🔴 动捕录制 [" + (recording ? "§c录制中" : "§7待机") + "§r]"),
                btn -> {
                    if (puppeteer.isPossessing()) {
                        if (recording) {
                            puppeteer.stopRecordingMoCap();
                        } else {
                            puppeteer.startRecordingMoCap();
                        }
                        rebuildWidgets();
                    } else if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.literal("§c[提示] 必须先按 [G] 附身人偶才能开始动捕录制！"), true);
                    }
                })
                .bounds(gridStartX + btnWidth + gapX, r1Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[K] 开启/停止受控实体动捕\n§7对当前附身人偶实时捕捉位移与朝向动作")))
                .build());

        // 6. 全场多轨同步排演 (P)
        boolean playing = clock.getState() == MasterClockEngine.State.PLAYING;
        addRenderableWidget(Button.builder(
                Component.literal("▶ 全场排演 [" + (playing ? "§a播放" : "§7暂停") + "§r]"),
                btn -> {
                    if (playing) {
                        clock.pause();
                    } else {
                        clock.play();
                    }
                    rebuildWidgets();
                })
                .bounds(gridStartX + (btnWidth + gapX) * 2, r1Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[P] 启动/暂停全场协同排演\n§7所有人偶与运镜机位按照时间轴完全同步启动开演")))
                .build());

        // === 第 3 行：时间流速与总时长调度 ===
        int r2Y = gridStartY + (btnHeight + gapY) * 2;
        // 7. 一键倒带复位 (R)
        addRenderableWidget(Button.builder(
                Component.literal("⏪ 一键倒带 [§e00:00§r]"),
                btn -> {
                    clock.rewindToStart();
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.literal("§a[导演系统] 已倒带复位回第 0 秒起始站位！"), true);
                    }
                    rebuildWidgets();
                })
                .bounds(gridStartX, r2Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[R] 一键倒带复位回起点\n§7瞬间将所有人偶、载具与机位吸回第 0 帧")))
                .build());

        // 8. 演播速度 (子弹时间 / 慢动作)
        addRenderableWidget(Button.builder(
                Component.literal("⚡ 速度 [" + clock.getTimeScale().getDisplayName() + "]"),
                btn -> {
                    clock.cycleTimeScale();
                    rebuildWidgets();
                })
                .bounds(gridStartX + btnWidth + gapX, r2Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[点击切换演播速度]\n§71.0x原速 ➔ 0.5x慢动作 ➔ 0.25x子弹时间 ➔ 2.0x快进\n慢动作/子弹时间下可精细化微调机位构图，\n录制时直接输出超丝滑高帧率慢镜头 MP4！")))
                .build());

        // 9. 场景时长
        addRenderableWidget(Button.builder(
                Component.literal("⏱ 时长 [" + String.format("%.0fs", clock.getTotalDurationSeconds()) + "]"),
                btn -> {
                    clock.cycleDuration();
                    rebuildWidgets();
                })
                .bounds(gridStartX + (btnWidth + gapX) * 2, r2Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[点击切换场景时长]\n§76秒 ➔ 10秒 ➔ 15秒 ➔ 30秒 ➔ 60秒\n设置排演与视频录制的总时长")))
                .build());

        // === 第 4 行：主视角与全机位 MP4 直出 ===
        int r3Y = gridStartY + (btnHeight + gapY) * 3;
        // 10. 打下分镜拍摄机位 (B)
        int stationCount = multiCam.getStations().size();
        addRenderableWidget(Button.builder(
                Component.literal("🎥 打下机位 [§6" + stationCount + "个§r]"),
                btn -> {
                    multiCam.addStationAtCurrent(null);
                    rebuildWidgets();
                })
                .bounds(gridStartX, r3Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[B] 在当前视角打下一个固定分镜机位\n§7记录空间位置、角度与当前镜头焦距")))
                .build());

        // 11. 录制主视角实时运镜 (F10)
        boolean isLivePov = batchRunner.isLivePovRecording();
        addRenderableWidget(Button.builder(
                Component.literal(isLivePov ? "⏹ 停止主视" : "🔴 录主视角 (POV)"),
                btn -> {
                    if (isLivePov) {
                        batchRunner.stopLivePovRecording();
                        rebuildWidgets();
                    } else {
                        onClose();
                        batchRunner.startLivePovRecording();
                    }
                })
                .bounds(gridStartX + btnWidth + gapX, r3Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§c[F10] 导演主视角实时录制 (直出 MP4)\n" +
                        "§7以当前自由相机视角实时自由飞行手持运镜，\n" +
                        "全场演员与时间轴同步开演，录制 Clean Feed 纯净 MP4，\n" +
                        "随时按 [F10] 停止，时间轴演播完成也将自动封包！")))
                .build());

        // 12. 批量录制全机位 MP4 参考视频 (Previs)
        boolean isBatching = batchRunner.isRunning() && !batchRunner.isLivePov();
        addRenderableWidget(Button.builder(
                Component.literal(isBatching ? "⏹ 停止批录" : "📼 批量录机位"),
                btn -> {
                    if (isBatching) {
                        batchRunner.cancel();
                    } else {
                        onClose();
                        batchRunner.startBatchRecording();
                    }
                })
                .bounds(gridStartX + (btnWidth + gapX) * 2, r3Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§6★ 直出全机位 MP4 参考视频\n§7自动遍历所有分镜机位并顺序录制完整演播片段，\n生成标准 H.264 视频直接作为 AI (Kling/可灵) 参考素材！")))
                .build());

        // === 第 5 行：导播监视、单机位录像与画幅构图 ===
        int r4Y = gridStartY + (btnHeight + gapY) * 4;
        // 13. 导播多机位监视大厅与分镜试看
        addRenderableWidget(Button.builder(
                Component.literal("📺 监视大厅 [" + (stationCount > 0 ? "§6" + stationCount + "机位§r" : "§7未设§r") + "]"),
                btn -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new CameraMonitorScreen());
                    }
                })
                .bounds(gridStartX, r4Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e导播多机位监视大厅\n§7总览所有分镜机位，一键切入沉浸试看与动作排演联动，\n支持机位重命名、覆盖更新与批量录制")))
                .build());

        // 14. 录制当前机位/单机位排演
        addRenderableWidget(Button.builder(
                Component.literal("🎬 录当前机位"),
                btn -> {
                    onClose();
                    batchRunner.startSingleStationRecording(null);
                })
                .bounds(gridStartX + btnWidth + gapX, r4Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§6★ 单机位排演录制 (直出 MP4)\n§7针对当前所选机位或试看构图视角，自动倒带并开启录制演播，\n录制完成后自动生成真实标准的 H.264 MP4 视频！")))
                .build());

        // 15. 构图画幅遮罩 (V)
        AspectRatioMode ratio = hud.getAspectRatioMode();
        addRenderableWidget(Button.builder(
                Component.literal("📐 画幅 [" + ratio.getDisplayName() + "]"),
                btn -> {
                    hud.toggleAspectRatio();
                    rebuildWidgets();
                })
                .bounds(gridStartX + (btnWidth + gapX) * 2, r4Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[V] 循环切换拍摄画幅遮罩\n§7全屏 ➔ 9:16竖屏短剧 ➔ 16:9宽屏 ➔ 21:9电影宽银幕")))
                .build());

        // === 第 6 行：隐身、纯净片场与视频目录 ===
        int r5Y = gridStartY + (btnHeight + gapY) * 5;
        // 16. 导演本体隐身切换 (F8)
        boolean hideModel = cam.isHidePlayerModel();
        addRenderableWidget(Button.builder(
                Component.literal("👤 导演隐身 [" + (hideModel ? "§a隐身" : "§e显现") + "§r]"),
                btn -> {
                    cam.toggleHidePlayerModel();
                    rebuildWidgets();
                })
                .bounds(gridStartX, r5Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[F8] 显隐导演玩家本体模型\n§7上帝视角下彻底隐身，避免地面替身破坏构图")))
                .build());

        // 17. 纯净片场史莱姆力场开关
        boolean shieldActive = PureStudioManager.INSTANCE.isSlimeShieldEnabled();
        addRenderableWidget(Button.builder(
                Component.literal("🛡 史莱姆力场 [" + (shieldActive ? "§a开启" : "§7关闭") + "§r]"),
                btn -> {
                    PureStudioManager.INSTANCE.toggleSlimeShield();
                    rebuildWidgets();
                })
                .bounds(gridStartX + btnWidth + gapX, r5Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e纯净片场史莱姆力场\n§7专为超平坦世界设计！开启时从根源阻止史莱姆和岩浆怪生成，\n杜绝蹦跳撞翻演员、挤占镜头和噪声干扰")))
                .build());

        // 18. 打开 MP4 视频保存目录
        addRenderableWidget(Button.builder(
                Component.literal("📁 视频目录"),
                btn -> multiCam.openExportFolder())
                .bounds(gridStartX + (btnWidth + gapX) * 2, r5Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e打开 MP4 视频存储文件夹\n§7在 Windows 资源管理器中直达视频保存路径")))
                .build());

        // === 第 7 行：工程持久化、手册与清空 ===
        int r6Y = gridStartY + (btnHeight + gapY) * 6;
        // 19. 保存片场工程
        addRenderableWidget(Button.builder(
                Component.literal("💾 保存片场"),
                btn -> {
                    StudioPersistenceManager.INSTANCE.saveStudioScene(true);
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.literal("§a[导演系统] 本存档片场工程数据已保存成功！"), false);
                    }
                })
                .bounds(gridStartX, r6Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e保存当前存档片场数据\n§7持久化保存全场演员动作轨迹、分镜机位与滑轨，\n下次进入该世界存档时自动无缝恢复！")))
                .build());

        // 20. 导演实战手册 (H)
        addRenderableWidget(Button.builder(
                Component.literal("📖 导演手册"),
                btn -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new DirectorTutorialScreen());
                    }
                })
                .bounds(gridStartX + btnWidth + gapX, r6Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[H] 翻看漫剧导演全操手册\n§7涵盖角色染色、动捕录制、运镜、构图与 AI 提示词指南")))
                .build());

        // 21. 清空全轨 (Del)
        addRenderableWidget(Button.builder(
                Component.literal("🗑 清空全场"),
                btn -> {
                    clock.clearAllTracks();
                    multiCam.clearStations();
                    cam.clearDollyKeyframes();
                    StudioPersistenceManager.INSTANCE.saveStudioScene(true);
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.literal("§e[导演系统] 已清空全场所有动捕轨道、分镜机位与滑轨！"), true);
                    }
                    rebuildWidgets();
                })
                .bounds(gridStartX + (btnWidth + gapX) * 2, r6Y, btnWidth, btnHeight)
                .tooltip(Tooltip.create(Component.literal("§e[Delete] 清空全场数据\n§7重置全场录制的角色动作、分镜机位与相机滑轨")))
                .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 按 C 键或 ESC 键立即退出
        if (ModKeyMappings.QUICK_MENU.matches(keyCode, scanCode) || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(450, width - 16);
        int panelHeight = Math.min(235, height - 12);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        // 1. 半透明深蓝暗色磨砂玻璃背景
        g.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0xEE0B111D);

        // 2. 外部霓虹青高质感边框
        int borderCyan = 0xFF00E5FF;
        g.fill(startX, startY, startX + panelWidth, startY + 1, borderCyan); // 顶
        g.fill(startX, startY + panelHeight - 1, startX + panelWidth, startY + panelHeight, borderCyan); // 底
        g.fill(startX, startY, startX + 1, startY + panelHeight, borderCyan); // 左
        g.fill(startX + panelWidth - 1, startY, startX + panelWidth, startY + panelHeight, borderCyan); // 右

        // 3. 标题与说明文本
        if (font != null) {
            g.drawString(font, "§6§l漫剧导演快捷操作中心 §7(Director Hub)", startX + 12, startY + 8, 0xFFFFFFFF, true);
            MultiCameraManager mcm = MultiCameraManager.INSTANCE;
            PureStudioManager psm = PureStudioManager.INSTANCE;
            MasterClockEngine mce = MasterClockEngine.INSTANCE;
            String status = "§7机位: §e" + mcm.getStations().size() + "个 §8| 航速: §e" + FpvFlightController.INSTANCE.getSpeedGear().getDisplayName() + " §8| 风格: §a" + FpvFlightController.INSTANCE.getFlightStyle().getDisplayName() + " §8| 速度: §b" + mce.getTimeScale().getDisplayName();
            g.drawString(font, status, startX + 12, startY + 22, 0xFFAAAAAA, false);
        }

        // 4. 渲染按钮组件与浮窗 Tooltips
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停单人游戏，方便排演观察
    }
}
