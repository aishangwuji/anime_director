package com.mannequin.client.gui;

import com.mannequin.client.camera.FovConverter;
import com.mannequin.client.camera.FpvFlightController;
import com.mannequin.client.timeline.MasterClockEngine;
import com.mannequin.client.timeline.PuppeteerController;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.world.item.ItemStack;

/**
 * 漫剧导演系统构图遮罩与时间轴 HUD 渲染层（Director Timeline Overlay）。
 *
 * <p>特性包含：
 * <ul>
 *   <li><b>画幅裁剪遮罩（Letterbox）</b>：支持 9:16（竖屏漫剧）、16:9、21:9，在两侧或上下渲染纯黑边；</li>
 *   <li><b>黄金三分线（Rule of Thirds）</b>：在拍摄安全区内绘制精准构图辅助线；</li>
 *   <li><b>片场时间轴与机位仪表盘</b>：实时呈现时间走字（秒数）、当前录制音轨、等效镜头焦距（mm）与航速；</li>
 *   <li><b>新手交互引导卡片（Guide Card）</b>：按 [H] 键展开/折叠 3 步制片速成指引卡片；</li>
 *   <li><b>情境式智能操作教练（Situational Coaching）</b>：根据用户当前操作状态，在底部实时提示下一步最佳操作；</li>
 *   <li><b>录制防干扰（Clean Feed）</b>：当按下原版 F1 隐藏界面时，自动隱去所有文字与辅助线，只保留画幅黑边，确保录制输出纯净生肉。</li>
 * </ul>
 */
public final class TimelineHudOverlay implements LayeredDraw.Layer {

    public static final TimelineHudOverlay INSTANCE = new TimelineHudOverlay();

    private AspectRatioMode aspectRatioMode = AspectRatioMode.OFF;
    private boolean showThirdsGrid = true;
    private boolean showGuideCard = true;
    private boolean showSituationalTip = true;

    private TimelineHudOverlay() {
        loadPreferences();
    }

    public void loadPreferences() {
        try {
            net.minecraft.nbt.CompoundTag tag = com.mannequin.client.config.ClientPreferences.INSTANCE.getRoot();
            if (tag.contains("tipsDismissed")) {
                boolean dismissed = tag.getBoolean("tipsDismissed");
                this.showGuideCard = !dismissed;
                this.showSituationalTip = !dismissed;
            }
            if (tag.contains("showGuideCard")) {
                this.showGuideCard = tag.getBoolean("showGuideCard");
            }
            if (tag.contains("showSituationalTip")) {
                this.showSituationalTip = tag.getBoolean("showSituationalTip");
            }
        } catch (Throwable ignored) {
        }
    }

    public void savePreferences() {
        com.mannequin.client.config.ClientPreferences.INSTANCE.updateRoot(tag -> {
            boolean dismissed = !areTipsVisible();
            tag.putBoolean("tipsDismissed", dismissed);
            tag.putBoolean("showGuideCard", showGuideCard);
            tag.putBoolean("showSituationalTip", showSituationalTip);
        });
    }

    public AspectRatioMode getAspectRatioMode() {
        return aspectRatioMode;
    }

    public void setAspectRatioMode(AspectRatioMode mode) {
        this.aspectRatioMode = mode;
        com.mannequin.client.studio.StudioWorkspaceManager.INSTANCE.onAspectRatioChanged(mode);
    }

    public void toggleAspectRatio() {
        setAspectRatioMode(this.aspectRatioMode.next());
    }

    public boolean isShowThirdsGrid() {
        return showThirdsGrid;
    }

    public void toggleThirdsGrid() {
        this.showThirdsGrid = !this.showThirdsGrid;
    }

    public boolean isShowGuideCard() {
        return showGuideCard;
    }

    public void toggleGuideCard() {
        this.showGuideCard = !this.showGuideCard;
        savePreferences();
    }

    public boolean isShowSituationalTip() {
        return showSituationalTip;
    }

    public void toggleSituationalTip() {
        this.showSituationalTip = !this.showSituationalTip;
        savePreferences();
    }

    public boolean areTipsVisible() {
        return showGuideCard || showSituationalTip;
    }

    public void setTipsVisible(boolean visible) {
        this.showGuideCard = visible;
        this.showSituationalTip = visible;
        savePreferences();
    }

    public void toggleTips() {
        setTipsVisible(!areTipsVisible());
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            return;
        }

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        // 1. 计算画幅遮罩视口安全区
        AspectRatioMode.ViewportRect rect = aspectRatioMode.calculateViewport(screenWidth, screenHeight);

        // 2. 绘制纯黑遮罩黑边（Letterbox / Pillarbox）
        if (aspectRatioMode != AspectRatioMode.OFF) {
            renderLetterbox(guiGraphics, screenWidth, screenHeight, rect);
        }

        // 若玩家按下 F1 开启隐蔽模式，或当前正处于纯净 Clean Feed MP4 真实录制中，则彻底跳过辅助线与所有 HUD 文字，输出绝对纯净画面
        if (mc.options.hideGui || (com.mannequin.client.camera.MultiCameraBatchRunner.INSTANCE.isCleanFeedEnabled() && com.mannequin.client.camera.Mp4VideoRecorder.INSTANCE.isRecording())) {
            return;
        }

        // 3. 在拍摄安全区内绘制九宫格三分线
        if (showThirdsGrid && aspectRatioMode != AspectRatioMode.OFF) {
            renderThirdsGrid(guiGraphics, rect);
        }

        // 4. 绘制导演时间轴与状态仪表盘
        renderStatusDashboard(guiGraphics, mc, rect);

        // 5. 绘制新手引导卡片
        if (showGuideCard) {
            renderGuideCard(guiGraphics, mc, rect);
        }

        // 6. 绘制情境式下一步操作引导
        if (showSituationalTip) {
            renderSituationalTip(guiGraphics, mc, rect);
        }
    }

    /**
     * 绘制边缘纯黑裁剪边框。
     */
    private void renderLetterbox(GuiGraphics g, int screenW, int screenH, AspectRatioMode.ViewportRect rect) {
        int color = 0xFF000000;
        if (rect.x() > 0) {
            // 左右柱状黑边
            g.fill(0, 0, rect.x(), screenH, color);
            g.fill(rect.x() + rect.width(), 0, screenW, screenH, color);
        }
        if (rect.y() > 0) {
            // 上下横幅黑边
            g.fill(0, 0, screenW, rect.y(), color);
            g.fill(0, rect.y() + rect.height(), screenW, screenH, color);
        }
    }

    /**
     * 绘制构图三分线。
     */
    private void renderThirdsGrid(GuiGraphics g, AspectRatioMode.ViewportRect rect) {
        int gridColor = 0x5500E5FF; // 半透明高对比青色

        int x1 = rect.x() + rect.width() / 3;
        int x2 = rect.x() + (rect.width() * 2) / 3;
        int y1 = rect.y() + rect.height() / 3;
        int y2 = rect.y() + (rect.height() * 2) / 3;

        // 两条竖线
        g.fill(x1, rect.y(), x1 + 1, rect.y() + rect.height(), gridColor);
        g.fill(x2, rect.y(), x2 + 1, rect.y() + rect.height(), gridColor);

        // 两条横线
        g.fill(rect.x(), y1, rect.x() + rect.width(), y1 + 1, gridColor);
        g.fill(rect.x(), y2, rect.x() + rect.width(), y2 + 1, gridColor);
    }

    /**
     * 绘制状态仪表盘（时间轴、动捕状态、焦距 mm）。
     */
    private void renderStatusDashboard(GuiGraphics g, Minecraft mc, AspectRatioMode.ViewportRect rect) {
        MasterClockEngine clock = MasterClockEngine.INSTANCE;
        PuppeteerController puppeteer = PuppeteerController.INSTANCE;
        FpvFlightController fpv = FpvFlightController.INSTANCE;

        int startX = Math.max(10, rect.x() + 10);
        int startY = Math.max(10, rect.y() + 10);
        int lineHeight = 11;
        int currentY = startY;

        // 1. 时间轴状态与演播速度
        String clockStateText;
        int stateColor;
        if (com.mannequin.client.camera.MultiCameraBatchRunner.INSTANCE.isLivePovRecording()) {
            clockStateText = String.format("🔴 [REC 主视角录制中] %04.1fs / %04.1fs | [F10] 停止", clock.getCurrentTimeSeconds(), clock.getTotalDurationSeconds());
            stateColor = 0xFFFF2222; // 醒目红色 REC 状态
        } else if (clock.getState() == MasterClockEngine.State.RECORDING) {
            clockStateText = String.format("● [动捕录制中] %04.1fs / %04.1fs", clock.getCurrentTimeSeconds(), clock.getTotalDurationSeconds());
            stateColor = 0xFFFF3333; // 红色
        } else if (clock.getState() == MasterClockEngine.State.PLAYING) {
            clockStateText = String.format("▶ [%s] %04.1fs / %04.1fs", clock.getTimeScale().getDisplayName(), clock.getCurrentTimeSeconds(), clock.getTotalDurationSeconds());
            stateColor = 0xFF33FF77; // 绿色
        } else {
            clockStateText = String.format("⏸ [片场已就绪 | %s] %04.1fs / %04.1fs", clock.getTimeScale().getDisplayName(), clock.getCurrentTimeSeconds(), clock.getTotalDurationSeconds());
            stateColor = 0xFFCCCCCC; // 白色/灰色
        }
        g.drawString(mc.font, clockStateText, startX, currentY, stateColor, true);
        currentY += lineHeight;

        // 2. 附身与录制主体信息
        if (puppeteer.isPossessing()) {
            String possessText = "附身受控主体: " + puppeteer.getPossessedEntity().getName().getString();
            g.drawString(mc.font, possessText, startX, currentY, 0xFFFFAA00, true);
            currentY += lineHeight;
        }

        // 3. 运镜相机与实时监视机位参数
        com.mannequin.client.camera.CameraStation previewStation = com.mannequin.client.camera.MultiCameraManager.INSTANCE.getActivePreviewStation();
        if (previewStation != null) {
            String previewTag = String.format("§6[🔴 实拍监视] %s | 焦距 FOV: %.1f° | [P] 动作演播试看 | [C] 监视大厅",
                    previewStation.name(), previewStation.fov());
            g.drawString(mc.font, previewTag, startX, currentY, 0xFFFFCC00, true);
            currentY += lineHeight;
        }

        if (fpv.isActive()) {
            double currentFov = fpv.getFov();
            double focalLength = FovConverter.fovToFocalLength(currentFov);
            String lensDesc = FovConverter.getFocalLengthDescription(focalLength);

            String cameraInfo = String.format("上帝自由相机 [%s | %s] | 焦距: %dmm %s | 倾角: %+.1f°%s",
                    fpv.getFlightStyle().getDisplayName(),
                    fpv.getSpeedGear().getDisplayName(),
                    Math.round(focalLength),
                    lensDesc,
                    fpv.getRoll(),
                    fpv.isMicroMode() ? " | [🐞 昆虫微距模式]" : ""
            );
            g.drawString(mc.font, cameraInfo, startX, currentY, 0xFF00E5FF, true);
        }
    }

    /**
     * 绘制右上角漫剧导演新手引导卡片（按 [H] 键折叠/展开）。
     */
    private void renderGuideCard(GuiGraphics g, Minecraft mc, AspectRatioMode.ViewportRect rect) {
        int cardWidth = 190;
        int cardHeight = 92;
        int startX = Math.min(rect.x() + rect.width() - cardWidth - 10, g.guiWidth() - cardWidth - 10);
        int startY = Math.max(10, rect.y() + 10);

        // 半透明背景框
        g.fill(startX, startY, startX + cardWidth, startY + cardHeight, 0x88000000);
        g.fill(startX, startY, startX + cardWidth, startY + 1, 0xFF00E5FF); // 顶部青色装饰线

        int textX = startX + 6;
        int textY = startY + 5;
        int lineH = 11;

        g.drawString(mc.font, "§6§l漫剧导演制片指引 §b[按C控制台]", textX, textY, 0xFFFFFFFF, true);
        g.drawString(mc.font, "§c[F7隐藏]", startX + cardWidth - 42, textY, 0xFFFF7777, false);
        textY += 13;

        g.drawString(mc.font, "§e① 布景: §7手持染料染色, §b回收杖§7删除人偶", textX, textY, 0xFFFFFFFF, false);
        textY += lineH;
        g.drawString(mc.font, "§e② 附身: §7准星对人偶按 §b[G]§7 键走位", textX, textY, 0xFFFFFFFF, false);
        textY += lineH;
        g.drawString(mc.font, "§e③ 录制: §7附身后按 §b[K]§7 录制路线", textX, textY, 0xFFFFFFFF, false);
        textY += lineH;
        g.drawString(mc.font, "§e④ 复位: §7按 §b[R]§7 一键倒带回0秒", textX, textY, 0xFFFFFFFF, false);
        textY += lineH;
        g.drawString(mc.font, "§e⑤ 运镜: §7按 §b[F6]§7 上帝, §b[F9]§7 风格, §b[J]§7 航速", textX, textY, 0xFFFFFFFF, false);
        textY += lineH;
        g.drawString(mc.font, "§e⑥ 实拍: §7按 §b[F10]§7 录主视, §b[P]§7 跑, §b[C]§7 控制台", textX, textY, 0xFFFFFFFF, false);
    }

    /**
     * 在屏幕底部绘制情境式下一步操作引导条。
     */
    private void renderSituationalTip(GuiGraphics g, Minecraft mc, AspectRatioMode.ViewportRect rect) {
        PuppeteerController puppeteer = PuppeteerController.INSTANCE;
        FpvFlightController fpv = FpvFlightController.INSTANCE;
        MasterClockEngine clock = MasterClockEngine.INSTANCE;

        String tipText;
        int color;

        ItemStack mainHand = (mc.player != null) ? mc.player.getMainHandItem() : ItemStack.EMPTY;
        ItemStack offHand = (mc.player != null) ? mc.player.getOffhandItem() : ItemStack.EMPTY;
        boolean holdingBuildWand = mainHand.is(com.mannequin.registry.ModItems.BUILD_WAND.get()) || offHand.is(com.mannequin.registry.ModItems.BUILD_WAND.get());
        com.mannequin.client.building.BuildingSelectionManager bsm = com.mannequin.client.building.BuildingSelectionManager.INSTANCE;

        if (com.mannequin.client.camera.MultiCameraBatchRunner.INSTANCE.isLivePovRecording()) {
            tipText = "🔴 正在实时录制主视角运镜！WASD飞行，鼠标转动，按住[Alt]超微移，按[F10]停止录像，播完自动封包";
            color = 0xFFFF4444;
        } else if (holdingBuildWand) {
            if (bsm.isPlacing()) {
                tipText = "🏛 全息放置模式：[左键/空中右键] 旋转90°，[右键地面方块] 落地部署，[Shift+右键/Del] 取消放置";
                color = 0xFF55FF55;
            } else if (bsm.hasSelection()) {
                tipText = "🏛 建筑选区已就绪 (" + bsm.getSelectionSize().getX() + "×" + bsm.getSelectionSize().getY() + "×" + bsm.getSelectionSize().getZ() + ")：[空中右键] 蓝图库打包导出，[Shift+右键/Del] 清空选区";
                color = 0xFF00E5FF;
            } else if (bsm.getPosA() != null) {
                tipText = "🏛 正在框选建筑：已定A点，[右键方块] 设B点，[Shift+右键] 自身位置设为B点(空中/虚空选点)，[空中右键] 视线定点";
                color = 0xFFFFAA00;
            } else {
                tipText = "🏛 建筑蓝图仪：[左键] 设A点(Shift+左键空中自身)，[右键] 设B点(Shift+右键空中自身)，[空中右键] 蓝图库";
                color = 0xFF00E5FF;
            }
        } else if (puppeteer.isRecording()) {
            tipText = "🔴 正在动捕录制当前主体中！按 WASD 操纵走位与闪避，到达终点自动存入音轨并可按 [R] 倒带";
            color = 0xFFFF5555;
        } else if (puppeteer.isPossessing()) {
            tipText = "👉 当前处于附身操纵状态：按 WASD 控制跑位，按 [K] 开始动捕录制，按 [G] 退出附身归还视角";
            color = 0xFFFFAA00;
        } else if (com.mannequin.client.camera.MultiCameraManager.INSTANCE.getActivePreviewStation() != null) {
            tipText = "👀 正在实拍监视【" + com.mannequin.client.camera.MultiCameraManager.INSTANCE.getActivePreviewStation().name() + "】：按 [P] 试看动作演播，按 [B] 覆盖更新，按 [C] 打开监视大厅";
            color = 0xFFFFCC00;
        } else if (fpv.isActive()) {
            tipText = "🎥 上帝自由相机中：[F9] 风格，[J] 航速，按住[Alt]超微移，[F10] 录主视角，[PgUp/PgDn]变焦，[P]排演";
            color = 0xFF00E5FF;
        } else if (clock.getState() == MasterClockEngine.State.PLAYING) {
            tipText = "▶ 全场多轨正在同步回放中！按 [P] 暂停，按 [R] 倒带复位至第 0 秒";
            color = 0xFF55FF55;
        } else {
            tipText = "💡 新手提示：随时按 [C] 呼出导演快捷菜单，按 [F6] 上帝视角，手持回收杖右键可删除人偶 §8| §7[F7关闭提示]";
            color = 0xFFDDDDDD;
        }

        int bottomY = Math.min(rect.y() + rect.height() - 14, g.guiHeight() - 14);
        int startX = Math.max(10, rect.x() + 10);
        g.drawString(mc.font, tipText, startX, bottomY, color, true);
    }
}
