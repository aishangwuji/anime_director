package com.mannequin.client.studio;

import com.mannequin.client.camera.DirectorCameraController;
import com.mannequin.client.gui.AspectRatioMode;
import com.mannequin.client.gui.TimelineHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 漫剧片场双模式工作区管理器（Studio Workspace Manager）。
 *
 * <p>本类实现「场景搭建模式」（Stage/Build Mode）与「运镜实拍模式」（Cinematic/Shoot Mode）
 * 的一键原子级无缝切换与白纸化自由配置记忆：
 * <ul>
 *   <li><b>🔨 场景搭建模式</b>：面向场景布景、道具陈设、演员站位与方块搭建。
 *       视角锁定玩家本体，完全显示原版物品栏、准星与HUD，原生全屏无黑边，方便建造与精准交互；</li>
 *   <li><b>🎬 运镜实拍模式</b>：面向镜头调度、穿越机运镜、推拉变焦与成片录制。
 *       一键进入自由飞控相机，全屏纯净无UI遮挡，自动加载电影宽幅遮罩，导演本体隐身不穿帮；</li>
 *   <li><b>白纸化无预设记忆</b>：不进行强制死板限制，用户在各模式下所设定的画幅、显隐与飞控参数将自动记忆保存。</li>
 * </ul>
 */
public final class StudioWorkspaceManager {

    public static final StudioWorkspaceManager INSTANCE = new StudioWorkspaceManager();

    public enum Mode {
        /** 场景搭建模式：玩家本体视角、原版物品栏与HUD全开、全屏原生视野 */
        BUILD("场景搭建", "🔨"),
        /** 运镜实拍模式：上帝/穿越机自由运镜、全屏纯净清屏、电影宽画幅、导演隐身 */
        CINEMATIC("运镜实拍", "🎬");

        private final String displayName;
        private final String icon;

        Mode(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }
    }

    private Mode currentMode = Mode.BUILD;

    // 实拍模式用户偏好（白纸化记忆，支持用户自由配置）
    private AspectRatioMode cinematicAspectRatio = AspectRatioMode.RATIO_21_9;
    private boolean cinematicHideGui = true;
    private boolean cinematicHidePlayer = true;

    // 搭建模式用户偏好（白纸化记忆，支持用户自由配置）
    private AspectRatioMode buildAspectRatio = AspectRatioMode.OFF;
    private boolean buildHideGui = false;
    private boolean buildHidePlayer = false;

    private StudioWorkspaceManager() {
        loadPreferences();
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    public boolean isCinematicMode() {
        return currentMode == Mode.CINEMATIC;
    }

    public boolean isBuildMode() {
        return currentMode == Mode.BUILD;
    }

    public AspectRatioMode getCinematicAspectRatio() {
        return cinematicAspectRatio;
    }

    public void setCinematicAspectRatio(AspectRatioMode cinematicAspectRatio) {
        this.cinematicAspectRatio = cinematicAspectRatio != null ? cinematicAspectRatio : AspectRatioMode.RATIO_21_9;
        savePreferences();
    }

    public AspectRatioMode getBuildAspectRatio() {
        return buildAspectRatio;
    }

    public void setBuildAspectRatio(AspectRatioMode buildAspectRatio) {
        this.buildAspectRatio = buildAspectRatio != null ? buildAspectRatio : AspectRatioMode.OFF;
        savePreferences();
    }

    public boolean isCinematicHideGui() {
        return cinematicHideGui;
    }

    public void setCinematicHideGui(boolean cinematicHideGui) {
        this.cinematicHideGui = cinematicHideGui;
        savePreferences();
    }

    public boolean isBuildHideGui() {
        return buildHideGui;
    }

    public void setBuildHideGui(boolean buildHideGui) {
        this.buildHideGui = buildHideGui;
        savePreferences();
    }

    /**
     * 当用户通过快捷键或菜单手动修改画幅时，白纸化记忆对应当前工作区模式的偏好。
     */
    public void onAspectRatioChanged(AspectRatioMode mode) {
        if (mode != null) {
            if (currentMode == Mode.CINEMATIC) {
                this.cinematicAspectRatio = mode;
            } else {
                this.buildAspectRatio = mode;
            }
            savePreferences();
        }
    }

    /**
     * 一键在「场景搭建模式」与「运镜实拍模式」之间原子级切换。
     */
    public void toggleMode() {
        if (currentMode == Mode.BUILD) {
            switchToMode(Mode.CINEMATIC);
        } else {
            switchToMode(Mode.BUILD);
        }
    }

    /**
     * 切换到目标工作区模式并自适应执行所有环境变更。
     */
    public void switchToMode(Mode targetMode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            this.currentMode = targetMode;
            return;
        }

        DirectorCameraController cam = DirectorCameraController.INSTANCE;
        TimelineHudOverlay hud = TimelineHudOverlay.INSTANCE;

        if (targetMode == Mode.CINEMATIC) {
            // 1. 若当前在搭建模式，先记忆用户当前的搭建偏好
            if (currentMode == Mode.BUILD) {
                this.buildAspectRatio = hud.getAspectRatioMode();
                this.buildHideGui = mc.options.hideGui;
            }

            this.currentMode = Mode.CINEMATIC;

            // 2. 视角转入上帝/穿越机自由相机
            if (!cam.isCameraActive()) {
                cam.toggleCamera();
            }

            // 3. 画面纯净化清屏（隐藏原生物品栏、准星、血条等）
            mc.options.hideGui = cinematicHideGui;

            // 4. 应用电影宽银幕画幅遮罩（默认 21:9 或用户上次记忆的比例）
            hud.setAspectRatioModeInternal(cinematicAspectRatio);

            // 5. 导演本体隐身（杜绝穿帮）
            cam.setHidePlayerModel(cinematicHidePlayer);

            // 6. 音效与视觉反馈
            if (mc.level != null) {
                mc.level.playSound(mc.player, mc.player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.7F, 1.4F);
            }
            mc.player.displayClientMessage(
                    Component.literal("§b[工作区] 🎬 已切入「运镜实拍模式」 §7(自由运镜 | 纯净画面 | " + cinematicAspectRatio.getDisplayName() + " | [F4]切回搭建)"),
                    true
            );
        } else {
            // 1. 若当前在实拍模式，先记忆用户实拍时自选的画幅
            if (currentMode == Mode.CINEMATIC) {
                this.cinematicAspectRatio = hud.getAspectRatioMode();
                this.cinematicHideGui = mc.options.hideGui;
            }

            this.currentMode = Mode.BUILD;

            // 2. 视角退出上帝自由相机，归还玩家本体
            if (cam.isCameraActive()) {
                cam.toggleCamera();
            }

            // 3. 恢复原版物品栏、快捷栏、准星与HUD显示
            mc.options.hideGui = buildHideGui;

            // 4. 恢复原生全屏视野（无黑边遮罩，方便方块搭建）
            hud.setAspectRatioModeInternal(buildAspectRatio);

            // 5. 显现导演本体模型
            cam.setHidePlayerModel(buildHidePlayer);

            // 6. 音效与视觉反馈
            if (mc.level != null) {
                mc.level.playSound(mc.player, mc.player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.7F, 0.9F);
            }
            mc.player.displayClientMessage(
                    Component.literal("§6[工作区] 🔨 已切入「场景搭建模式」 §7(本体视角 | 显示物品栏 | 原生全屏 | [F4]切回实拍)"),
                    true
            );
        }

        savePreferences();
    }

    public void loadPreferences() {
        try {
            CompoundTag tag = com.mannequin.client.config.ClientPreferences.INSTANCE.getRoot();
            if (tag.contains("workspaceMode")) {
                try {
                    this.currentMode = Mode.valueOf(tag.getString("workspaceMode"));
                } catch (Exception ignored) {
                }
            }
            if (tag.contains("cinematicAspectRatio")) {
                try {
                    this.cinematicAspectRatio = AspectRatioMode.valueOf(tag.getString("cinematicAspectRatio"));
                } catch (Exception ignored) {
                }
            }
            if (tag.contains("buildAspectRatio")) {
                try {
                    this.buildAspectRatio = AspectRatioMode.valueOf(tag.getString("buildAspectRatio"));
                } catch (Exception ignored) {
                }
            }
            if (tag.contains("cinematicHideGui")) {
                this.cinematicHideGui = tag.getBoolean("cinematicHideGui");
            }
            if (tag.contains("buildHideGui")) {
                this.buildHideGui = tag.getBoolean("buildHideGui");
            }
            if (tag.contains("cinematicHidePlayer")) {
                this.cinematicHidePlayer = tag.getBoolean("cinematicHidePlayer");
            }
        } catch (Throwable ignored) {
        }
    }

    public void savePreferences() {
        com.mannequin.client.config.ClientPreferences.INSTANCE.updateRoot(tag -> {
            tag.putString("workspaceMode", currentMode.name());
            tag.putString("cinematicAspectRatio", cinematicAspectRatio.name());
            tag.putString("buildAspectRatio", buildAspectRatio.name());
            tag.putBoolean("cinematicHideGui", cinematicHideGui);
            tag.putBoolean("buildHideGui", buildHideGui);
            tag.putBoolean("cinematicHidePlayer", cinematicHidePlayer);
        });
    }
}
