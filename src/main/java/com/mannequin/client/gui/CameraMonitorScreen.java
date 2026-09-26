package com.mannequin.client.gui;

import com.mannequin.client.camera.CameraStation;
import com.mannequin.client.camera.FpvFlightController;
import com.mannequin.client.camera.MultiCameraBatchRunner;
import com.mannequin.client.camera.MultiCameraManager;
import com.mannequin.client.timeline.MasterClockEngine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 导播多机位监视大厅与分镜实拍试看中心（Camera Studio Monitor Screen）。
 *
 * <p>创作者在此可：
 * <ul>
 *   <li>总览片场中布置的全部拍摄机位列表；</li>
 *   <li>一键瞬移切入任一机位进行沉浸式实拍试看（Live Preview）；</li>
 *   <li>联动主时钟时间轴（按 P 键）观看演员动作进入该镜头的具体走位表现；</li>
 *   <li>微调构图后一键覆盖更新机位、重命名镜头或直接一键触发批量录制。</li>
 * </ul>
 */
public class CameraMonitorScreen extends Screen {

    private int selectedIndex = 0;
    private int pageIndex = 0;
    private static final int PAGE_SIZE = 5;
    private EditBox renameBox;
    private boolean isRenaming = false;

    public CameraMonitorScreen() {
        super(Component.literal("导播多机位监视大厅"));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, width - 20);
        int panelHeight = Math.min(290, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        MultiCameraManager mcm = MultiCameraManager.INSTANCE;
        List<CameraStation> stations = mcm.getStations();

        if (selectedIndex >= stations.size()) {
            selectedIndex = Math.max(0, stations.size() - 1);
        }

        // 顶部关闭按钮 (✕)
        addRenderableWidget(Button.builder(Component.literal("✕"), btn -> onClose())
                .bounds(startX + panelWidth - 22, startY + 4, 18, 18)
                .tooltip(Tooltip.create(Component.literal("§c返回片场 (ESC)")))
                .build());

        // 左侧机位列表区域 (宽 180)
        int listStartX = startX + 12;
        int listStartY = startY + 42;
        int itemWidth = 175;
        int itemHeight = 26;

        int totalPages = Math.max(1, (int) Math.ceil((double) stations.size() / PAGE_SIZE));
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }

        int startIdx = pageIndex * PAGE_SIZE;
        int endIdx = Math.min(stations.size(), startIdx + PAGE_SIZE);

        for (int i = startIdx; i < endIdx; i++) {
            final int idx = i;
            CameraStation s = stations.get(idx);
            boolean isSel = (idx == selectedIndex);
            String title = (isSel ? "§6▶ " : "§f") + s.name() + " §7(" + (int) s.fov() + "°)";

            addRenderableWidget(Button.builder(Component.literal(title), btn -> {
                selectedIndex = idx;
                isRenaming = false;
                rebuildWidgets();
            })
                    .bounds(listStartX, listStartY + (idx - startIdx) * (itemHeight + 4), itemWidth, itemHeight)
                    .tooltip(Tooltip.create(Component.literal("§e点击选中【" + s.name() + "】查看与操作机位")))
                    .build());
        }

        // 翻页控件（若机位超过 5 个）
        if (totalPages > 1) {
            int pageBtnY = listStartY + PAGE_SIZE * (itemHeight + 4) + 2;
            addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
                if (pageIndex > 0) {
                    pageIndex--;
                    rebuildWidgets();
                }
            }).bounds(listStartX, pageBtnY, 30, 16).build());

            addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
                if (pageIndex < totalPages - 1) {
                    pageIndex++;
                    rebuildWidgets();
                }
            }).bounds(listStartX + itemWidth - 30, pageBtnY, 30, 16).build());
        }

        // 右侧操作与详情面板 (宽 205)
        int rightStartX = startX + 200;
        int rightStartY = startY + 42;
        int rightWidth = panelWidth - 212;

        if (!stations.isEmpty() && selectedIndex < stations.size()) {
            CameraStation current = stations.get(selectedIndex);

            // 操作按钮 1: 👀 立即切入实拍试看 (全屏视口沉浸试看)
            addRenderableWidget(Button.builder(Component.literal("👀 立即切入实拍试看"), btn -> {
                onClose();
                mcm.previewStation(current.id());
            })
                    .bounds(rightStartX, rightStartY + 72, rightWidth, 20)
                    .tooltip(Tooltip.create(Component.literal("§6★ 瞬移切入当前机位镜头\n§7关闭菜单并直接以该机位角度进行全屏沉浸式监视，\n可直接按 [P] 试看演员动作录制效果！")))
                    .build());

            // 操作按钮 2: 🎬 录制此机位 MP4
            addRenderableWidget(Button.builder(Component.literal("🎬 录制此机位 MP4"), btn -> {
                onClose();
                MultiCameraBatchRunner.INSTANCE.startSingleStationRecording(current);
            })
                    .bounds(rightStartX, rightStartY + 94, rightWidth, 20)
                    .tooltip(Tooltip.create(Component.literal("§6★ 直出该机位标准 H.264 MP4 视频\n§7自动倒带至第 0 秒并开启录制演播，\n录制完成后自动生成真实可播放的 MP4 视频文件！")))
                    .build());

            // 操作按钮 3: 🔄 覆盖为当前视角
            addRenderableWidget(Button.builder(Component.literal("🔄 覆盖为当前视角"), btn -> {
                mcm.updateStationToCurrent(current.id());
                rebuildWidgets();
            })
                    .bounds(rightStartX, rightStartY + 116, rightWidth, 18)
                    .tooltip(Tooltip.create(Component.literal("§e将当前相机游走的新坐标与焦距更新保存到此机位")))
                    .build());

            // 操作按钮 4: ⚡ 速度与 ⏱ 时长微调行
            int halfW = (rightWidth - 4) / 2;
            addRenderableWidget(Button.builder(Component.literal("⚡ " + MasterClockEngine.INSTANCE.getTimeScale().getDisplayName()), btn -> {
                MasterClockEngine.INSTANCE.cycleTimeScale();
                rebuildWidgets();
            })
                    .bounds(rightStartX, rightStartY + 136, halfW, 18)
                    .tooltip(Tooltip.create(Component.literal("§e循环切换演播速度\n§71.0x原速 ➔ 0.5x慢动作 ➔ 0.25x子弹时间 ➔ 2.0x快进")))
                    .build());

            addRenderableWidget(Button.builder(Component.literal("⏱ " + String.format("%.0fs", MasterClockEngine.INSTANCE.getTotalDurationSeconds())), btn -> {
                MasterClockEngine.INSTANCE.cycleDuration();
                rebuildWidgets();
            })
                    .bounds(rightStartX + halfW + 4, rightStartY + 136, halfW, 18)
                    .tooltip(Tooltip.create(Component.literal("§e循环切换片场时长\n§76秒 ➔ 10秒 ➔ 15秒 ➔ 30秒 ➔ 60秒")))
                    .build());

            // 操作按钮 4.5: 🎥 视角风格 与 🚀 航速档位
            addRenderableWidget(Button.builder(
                    Component.literal("🎥 " + FpvFlightController.INSTANCE.getFlightStyle().getDisplayName()),
                    btn -> {
                        FpvFlightController.INSTANCE.toggleFlightStyle();
                        rebuildWidgets();
                    })
                    .bounds(rightStartX, rightStartY + 156, halfW, 18)
                    .tooltip(Tooltip.create(Component.literal("§e[F9] 切换运镜风格\n§a[🛡 防抖平稳]：三轴防抖零侧倾\n§6[🚁 穿越机]：气动侧倾与惯性滑翔")))
                    .build());

            addRenderableWidget(Button.builder(
                    Component.literal("🚀 " + FpvFlightController.INSTANCE.getSpeedGear().getDisplayName()),
                    btn -> {
                        FpvFlightController.INSTANCE.cycleSpeedGear();
                        rebuildWidgets();
                    })
                    .bounds(rightStartX + halfW + 4, rightStartY + 156, halfW, 18)
                    .tooltip(Tooltip.create(Component.literal("§e[J] 切换自由相机航速档位\n0.5m/s超微移 ➔ 2.0m/s慢推拉 ➔ 6.0m/s标准 ➔ 16.0m/s高速\n按住 [Alt] 键可随时切入 0.25x 超微爬行")))
                    .build());

            // 重命名输入框
            if (isRenaming) {
                renameBox = new EditBox(font, rightStartX, rightStartY + 176, rightWidth - 45, 18, Component.literal("机位名称"));
                renameBox.setValue(current.name());
                addRenderableWidget(renameBox);

                addRenderableWidget(Button.builder(Component.literal("保存"), btn -> {
                    mcm.renameStation(current.id(), renameBox.getValue());
                    isRenaming = false;
                    rebuildWidgets();
                }).bounds(rightStartX + rightWidth - 42, rightStartY + 176, 42, 18).build());
            } else {
                addRenderableWidget(Button.builder(Component.literal("✏️ 重命名此机位"), btn -> {
                    isRenaming = true;
                    rebuildWidgets();
                }).bounds(rightStartX, rightStartY + 176, rightWidth, 18).build());
            }

            // 操作按钮 5: 🗑 删除此机位
            addRenderableWidget(Button.builder(Component.literal("🗑 删除此机位"), btn -> {
                mcm.removeStation(current.id());
                rebuildWidgets();
            })
                    .bounds(rightStartX, rightStartY + 196, rightWidth, 18)
                    .tooltip(Tooltip.create(Component.literal("§c从机位库中永久移除该分镜机位")))
                    .build());
        }

        // 底部全局功能行 (4 键紧凑排布)
        int botY = startY + panelHeight - 30;
        int botBtnW = (panelWidth - 24 - 18) / 4;

        // 1. ➕ 在当前视角打新机位
        addRenderableWidget(Button.builder(Component.literal("➕ 打新机位 (B)"), btn -> {
            mcm.addStationAtCurrent(null);
            selectedIndex = mcm.getStations().size() - 1;
            rebuildWidgets();
        }).bounds(startX + 12, botY, botBtnW, 20).build());

        // 2. 🔴 录制主视角实时运镜 (F10)
        boolean isLivePov = MultiCameraBatchRunner.INSTANCE.isLivePovRecording();
        addRenderableWidget(Button.builder(
                Component.literal(isLivePov ? "⏹ 停止主视" : "🔴 录主视角"),
                btn -> {
                    if (isLivePov) {
                        MultiCameraBatchRunner.INSTANCE.stopLivePovRecording();
                        rebuildWidgets();
                    } else {
                        onClose();
                        MultiCameraBatchRunner.INSTANCE.startLivePovRecording();
                    }
                })
                .bounds(startX + 12 + botBtnW + 6, botY, botBtnW, 20)
                .tooltip(Tooltip.create(Component.literal("§c[F10] 导演主视角实时录制\n§7以自由相机第一人称手持运镜，实时录制高品质 MP4 视频")))
                .build());

        // 3. 📼 批量录制全机位 MP4
        addRenderableWidget(Button.builder(Component.literal("📼 批量录制"), btn -> {
            onClose();
            MultiCameraBatchRunner.INSTANCE.startBatchRecording();
        }).bounds(startX + 12 + (botBtnW + 6) * 2, botY, botBtnW, 20)
                .tooltip(Tooltip.create(Component.literal("§6按顺序自动演播并录制全部机位 MP4 视频")))
                .build());

        // 4. 返回主快捷菜单 (C)
        addRenderableWidget(Button.builder(Component.literal("返回中心 (C)"), btn -> {
            if (minecraft != null) {
                minecraft.setScreen(new DirectorQuickMenuScreen());
            }
        }).bounds(startX + 12 + (botBtnW + 6) * 3, botY, botBtnW, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameBox != null && renameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                MultiCameraManager.INSTANCE.renameStation(
                        MultiCameraManager.INSTANCE.getStations().get(selectedIndex).id(),
                        renameBox.getValue()
                );
                isRenaming = false;
                rebuildWidgets();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(420, width - 20);
        int panelHeight = Math.min(290, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        // 1. 背景与青色边框
        g.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0xEE0B111D);
        int borderCyan = 0xFF00E5FF;
        g.fill(startX, startY, startX + panelWidth, startY + 1, borderCyan);
        g.fill(startX, startY + panelHeight - 1, startX + panelWidth, startY + panelHeight, borderCyan);
        g.fill(startX, startY, startX + 1, startY + panelHeight, borderCyan);
        g.fill(startX + panelWidth - 1, startY, startX + panelWidth, startY + panelHeight, borderCyan);

        // 2. 标题文本
        if (font != null) {
            g.drawString(font, "§6§l导播多机位监视大厅 §7(Camera Studio Monitor)", startX + 12, startY + 8, 0xFFFFFFFF, true);
            g.drawString(font, "§8选择机位即刻切入实拍试看 | 3D场景中已标识摄像头与视锥线框", startX + 12, startY + 22, 0xFFAAAAAA, false);

            MultiCameraManager mcm = MultiCameraManager.INSTANCE;
            List<CameraStation> stations = mcm.getStations();

            // 右侧机位详情信息面板
            if (!stations.isEmpty() && selectedIndex < stations.size()) {
                CameraStation cur = stations.get(selectedIndex);
                int rX = startX + 200;
                int rY = startY + 42;

                g.fill(rX - 4, rY - 4, startX + panelWidth - 12, rY + 68, 0x55000000);
                g.drawString(font, "§e【机位详情】" + cur.name(), rX, rY, 0xFFFFFFFF, false);
                g.drawString(font, String.format("§7坐标: §f%.1f, %.1f, %.1f", cur.position().x, cur.position().y, cur.position().z), rX, rY + 14, 0xFFCCCCCC, false);
                g.drawString(font, String.format("§7朝向: §fYaw %.0f° | Pitch %.0f°", cur.yaw(), cur.pitch()), rX, rY + 28, 0xFFCCCCCC, false);
                g.drawString(font, String.format("§7焦距: §bFOV %.1f° §7(横滚: §f%.0f°§7)", cur.fov(), cur.roll()), rX, rY + 42, 0xFFCCCCCC, false);
                g.drawString(font, "§a✔ 3D发光机位与视锥体已在世界呈现", rX, rY + 56, 0xFF88FF88, false);
            } else {
                int rX = startX + 200;
                int rY = startY + 42;
                g.drawString(font, "§c尚未添加任何拍摄机位！", rX, rY, 0xFFFF8888, false);
                g.drawString(font, "§7请在上帝视角飞到理想构图处，", rX, rY + 16, 0xFFAAAAAA, false);
                g.drawString(font, "§7按 [B] 键或点击下方按钮打下机位。", rX, rY + 30, 0xFFAAAAAA, false);
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
