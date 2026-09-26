package com.mannequin.client.camera;

import com.mannequin.client.gui.DirectorQuickMenuScreen;
import com.mannequin.client.gui.StudioMusicScreen;
import com.mannequin.client.gui.TimelineHudOverlay;
import com.mannequin.client.gui.tutorial.DirectorTutorialScreen;
import com.mannequin.client.input.ModKeyMappings;
import com.mannequin.client.studio.StudioWorkspaceManager;
import com.mannequin.client.timeline.MasterClockEngine;
import com.mannequin.client.timeline.PuppeteerController;
import com.mannequin.client.timeline.TimelineTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * 导演相机输入处理器（CameraInputHandler）。
 *
 * <p>负责全局导演按键监听消费、视角微调与玩家本体操控锁死。
 */
public final class CameraInputHandler {

    public static final CameraInputHandler INSTANCE = new CameraInputHandler();

    private CameraInputHandler() {
    }

    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }

        // 切换导演相机自由飞控
        if (ModKeyMappings.TOGGLE_CAMERA.consumeClick()) {
            DirectorCameraController.INSTANCE.toggleCamera();
        }

        // 一键在「场景搭建模式」与「运镜实拍模式」之间原子级切换 (F4 键)
        if (ModKeyMappings.TOGGLE_WORKSPACE_MODE.consumeClick()) {
            StudioWorkspaceManager.INSTANCE.toggleMode();
        }

        // 切换附身受控人偶/载具 (G 键)
        if (ModKeyMappings.TOGGLE_POSSESSION.consumeClick()) {
            PuppeteerController.INSTANCE.togglePossession();
        }

        // 镜头倾斜角一键回正复位 (N 键)
        if (ModKeyMappings.RESET_ROLL.consumeClick()) {
            FpvFlightController.INSTANCE.resetRoll();
        }

        // 切换 9:16 / 16:9 / 21:9 画幅遮罩 (V 键)
        if (ModKeyMappings.TOGGLE_ASPECT_RATIO.consumeClick()) {
            TimelineHudOverlay.INSTANCE.toggleAspectRatio();
        }

        // 打开/关闭游戏内新手实训教程手册界面 (H 键)
        if (ModKeyMappings.TOGGLE_GUIDE.consumeClick()) {
            if (mc.screen instanceof DirectorTutorialScreen) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                mc.setScreen(new DirectorTutorialScreen());
            }
        }

        // 开启/关闭屏幕操作指引与浮窗提示 (F7 键)
        if (ModKeyMappings.TOGGLE_HUD_TIPS.consumeClick()) {
            TimelineHudOverlay.INSTANCE.toggleTips();
            if (mc.player != null) {
                boolean visible = TimelineHudOverlay.INSTANCE.areTipsVisible();
                mc.player.displayClientMessage(Component.literal(visible ? "§a[导演系统] 已开启屏幕操作指引浮窗" : "§7[导演系统] 已关闭屏幕操作指引（已记住设置，不会再自动弹出）"), true);
            }
        }

        // 显隐导演玩家本体模型 (F8 键)
        if (ModKeyMappings.TOGGLE_PLAYER_VISIBILITY.consumeClick()) {
            DirectorCameraController.INSTANCE.toggleHidePlayerModel();
            if (mc.player != null) {
                boolean hide = DirectorCameraController.INSTANCE.isHidePlayerModel();
                mc.player.displayClientMessage(Component.literal(hide ? "§a[导演系统] 上帝视角：已隐去导演玩家本体模型" : "§e[导演系统] 上帝视角：已显示导演玩家本体模型"), true);
            }
        }

        // 切换导演运镜风格：防抖平稳视角 vs 穿越机航模视角 (F9 键)
        if (ModKeyMappings.TOGGLE_CAMERA_STYLE.consumeClick()) {
            FpvFlightController.INSTANCE.toggleFlightStyle();
        }

        // 开启/停止主视角实时运镜录制并直出 MP4 (F10 键)
        if (ModKeyMappings.RECORD_LIVE_POV.consumeClick()) {
            if (MultiCameraBatchRunner.INSTANCE.isLivePovRecording()) {
                MultiCameraBatchRunner.INSTANCE.stopLivePovRecording();
            } else {
                MultiCameraBatchRunner.INSTANCE.startLivePovRecording();
            }
        }

        // 循环切换自由相机多档位航速预设 (J 键)
        if (ModKeyMappings.CYCLE_SPEED_GEAR.consumeClick()) {
            FpvFlightController.INSTANCE.cycleSpeedGear();
        }

        // 呼出导演全功能快捷操作中心 / 动作面板 (默认 C 键，可自定义)
        if (ModKeyMappings.QUICK_MENU.consumeClick()) {
            if (mc.screen instanceof DirectorQuickMenuScreen) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                mc.setScreen(new DirectorQuickMenuScreen());
            }
        }

        // 呼出片场配乐与背景曲库界面 (快捷键默认未绑定，用户可自定义)
        if (ModKeyMappings.TOGGLE_MUSIC.consumeClick()) {
            if (mc.screen instanceof StudioMusicScreen) {
                mc.setScreen(null);
            } else if (mc.screen == null) {
                mc.setScreen(new StudioMusicScreen());
            }
        }

        // 启动/暂停排演回放 (P 键)
        if (ModKeyMappings.START_DOLLY.consumeClick()) {
            if (MasterClockEngine.INSTANCE.getState() == MasterClockEngine.State.PLAYING) {
                MasterClockEngine.INSTANCE.pause();
            } else {
                MasterClockEngine.INSTANCE.play();
            }
        }

        // 一键倒带复位至第 0 秒 (R 键)
        if (ModKeyMappings.RESET_DOLLY.consumeClick()) {
            MasterClockEngine.INSTANCE.rewindToStart();
        }

        // 开始/停止对附身实体录制动捕 (K 键)，或打下机械滑轨关键机位
        if (ModKeyMappings.ADD_KEYFRAME.consumeClick()) {
            if (PuppeteerController.INSTANCE.isPossessing()) {
                if (PuppeteerController.INSTANCE.isRecording()) {
                    PuppeteerController.INSTANCE.stopRecordingMoCap();
                } else {
                    PuppeteerController.INSTANCE.startRecordingMoCap();
                }
            } else if (DirectorCameraController.INSTANCE.isCameraActive()) {
                DollyPlaybackDriver.INSTANCE.addDollyKeyframe(DirectorCameraController.INSTANCE.getAnchor());
            }
        }

        // 清空当前动捕轨道与机械滑轨机位 (Delete 键)
        if (ModKeyMappings.CLEAR_TRACK.consumeClick()) {
            if (PuppeteerController.INSTANCE.isPossessing()) {
                Entity possessed = PuppeteerController.INSTANCE.getPossessedEntity();
                TimelineTrack track = MasterClockEngine.INSTANCE.getTracks().get(possessed.getUUID().toString());
                if (track != null) {
                    track.clear();
                    mc.player.displayClientMessage(Component.literal("§e[导演系统] 已清空当前附身实体的动捕数据"), true);
                }
            } else {
                DollyPlaybackDriver.INSTANCE.clearDollyKeyframes();
                MasterClockEngine.INSTANCE.clearAllTracks();
                mc.player.displayClientMessage(Component.literal("§e[导演系统] 已清空全场所有动捕轨道与机械滑轨样条机位"), true);
            }
        }

        // 时间轴总长度调节 ([ / ])
        if (ModKeyMappings.INCREASE_DURATION.consumeClick()) {
            MasterClockEngine.INSTANCE.setTotalDurationTicks(MasterClockEngine.INSTANCE.getTotalDurationTicks() + 20); // +1s
        }
        if (ModKeyMappings.DECREASE_DURATION.consumeClick()) {
            MasterClockEngine.INSTANCE.setTotalDurationTicks(MasterClockEngine.INSTANCE.getTotalDurationTicks() - 20); // -1s
        }

        // 添加/覆盖分镜固定拍摄机位 (B 键添加新机位 / Shift+B 覆盖更新当前机位)
        if (ModKeyMappings.ADD_CAMERA_STATION.consumeClick()) {
            CameraStation previewStation = MultiCameraManager.INSTANCE.getActivePreviewStation();
            if (previewStation != null && mc.options.keyShift.isDown()) {
                MultiCameraManager.INSTANCE.updateStationToCurrent(previewStation.id());
            } else {
                MultiCameraManager.INSTANCE.addStationAtCurrent(null);
            }
        }
    }

    public void onCalculatePlayerTurn(CalculatePlayerTurnEvent event) {
        if (!DirectorCameraController.INSTANCE.isCameraActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.mouseHandler.isMouseGrabbed() && mc.isWindowActive()) {
            double dx = mc.mouseHandler.getXVelocity();
            double dy = mc.mouseHandler.getYVelocity();

            double sensitivity = mc.options.sensitivity().get();
            double d2 = sensitivity * 0.6F + 0.2F;
            double d3 = d2 * d2 * d2;
            double d4 = d3 * 8.0;
            int invertY = mc.options.invertYMouse().get() ? -1 : 1;

            double deltaYaw = dx * d4;
            double deltaPitch = dy * d4 * invertY;

            FpvFlightController.INSTANCE.onMouseTurn(deltaYaw, deltaPitch);
        }

        // 彻底切断对玩家本体模型的旋转影响：
        // 将计算灵敏度设为抵消值 (-0.2 / 0.6F)，使 d2 = 0，从而使 vanilla 的 player.turn(d0, d1) 接收到的位移恒为 0
        event.setMouseSensitivity(-0.2 / 0.6F);
    }

    public void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (DirectorCameraController.INSTANCE.isCameraActive()) {
            net.minecraft.client.player.Input input = event.getInput();
            input.forwardImpulse = 0.0F;
            input.leftImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        }
    }
}
