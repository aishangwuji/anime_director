package com.mannequin.client.gui;

import com.mannequin.client.audio.StudioMusicEngine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 漫剧片场背景音乐与曲库管理界面（Studio Music Player Screen）。
 *
 * <p>提供本地音乐文件夹指定、多格式音频库扫描与浏览、实时推流播放控制、循环模式切换与无级音量调节。
 */
public class StudioMusicScreen extends Screen {

    private final Screen parentScreen;
    private EditBox dirInput;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 5;

    public StudioMusicScreen() {
        this(null);
    }

    public StudioMusicScreen(Screen parentScreen) {
        super(Component.literal("片场配乐与背景曲库"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(500, width - 20);
        int panelHeight = Math.min(270, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        StudioMusicEngine engine = StudioMusicEngine.INSTANCE;
        List<StudioMusicEngine.Track> tracks = engine.getPlaylist();

        // ==================== 顶部标题栏工具按钮 ====================
        // 1. 打开本地曲库文件夹 (Windows 资源管理器)
        addRenderableWidget(Button.builder(
                Component.literal("打开目录"),
                btn -> engine.openMusicFolder())
                .bounds(startX + panelWidth - 146, startY + 8, 68, 20)
                .tooltip(Tooltip.create(Component.literal("§e在 Windows 资源管理器中打开当前音乐文件夹\n§7可直接将 .mp3, .wav, .ogg 音乐文件拖入此目录！")))
                .build());

        // 2. 刷新曲库
        addRenderableWidget(Button.builder(
                Component.literal("刷新"),
                btn -> {
                    engine.rescanMusicDirectory();
                    rebuildWidgets();
                })
                .bounds(startX + panelWidth - 74, startY + 8, 44, 20)
                .tooltip(Tooltip.create(Component.literal("§e重新扫描当前文件夹中的音频文件")))
                .build());

        // 3. 关闭按钮 (✕)
        addRenderableWidget(Button.builder(
                Component.literal("✕"),
                btn -> onClose())
                .bounds(startX + panelWidth - 26, startY + 8, 18, 20)
                .tooltip(Tooltip.create(Component.literal("§c关闭 (ESC)")))
                .build());

        // ==================== 路径配置栏 ====================
        int pathRowY = startY + 34;
        int dirLabelWidth = 62;
        int btnGroupWidth = 126;
        int editBoxWidth = panelWidth - 24 - dirLabelWidth - btnGroupWidth - 8;

        String currentDirText = dirInput != null ? dirInput.getValue() : engine.getMusicDirectory().toAbsolutePath().toString();
        dirInput = new EditBox(font, startX + 12 + dirLabelWidth, pathRowY, editBoxWidth, 20, Component.literal("曲库目录"));
        dirInput.setMaxLength(300);
        dirInput.setValue(currentDirText);
        addRenderableWidget(dirInput);

        // 保存路径按钮
        addRenderableWidget(Button.builder(
                Component.literal("保存路径"),
                btn -> {
                    String input = dirInput.getValue().trim();
                    if (!input.isEmpty()) {
                        try {
                            Path newPath = Paths.get(input);
                            engine.setMusicDirectory(newPath);
                            this.page = 0;
                            rebuildWidgets();
                            if (minecraft != null && minecraft.player != null) {
                                minecraft.player.displayClientMessage(
                                        Component.literal("§a[片场配乐] 曲库文件夹已成功更新！共找到 " + engine.getPlaylist().size() + " 首音乐"),
                                        true
                                );
                            }
                        } catch (Exception e) {
                            if (minecraft != null && minecraft.player != null) {
                                minecraft.player.displayClientMessage(
                                        Component.literal("§c[片场配乐] 路径格式无效: " + e.getMessage()),
                                        true
                                );
                            }
                        }
                    }
                })
                .bounds(startX + 12 + dirLabelWidth + editBoxWidth + 4, pathRowY, 60, 20)
                .tooltip(Tooltip.create(Component.literal("§e保存并切换到指定音乐文件夹")))
                .build());

        // 恢复默认路径按钮
        addRenderableWidget(Button.builder(
                Component.literal("默认"),
                btn -> {
                    engine.resetToDefaultMusicDirectory();
                    this.page = 0;
                    if (dirInput != null) {
                        dirInput.setValue(engine.getMusicDirectory().toAbsolutePath().toString());
                    }
                    rebuildWidgets();
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.displayClientMessage(Component.literal("§a[片场配乐] 已恢复默认曲库目录: config/mannequin/music"), true);
                    }
                })
                .bounds(startX + 12 + dirLabelWidth + editBoxWidth + 68, pathRowY, 44, 20)
                .tooltip(Tooltip.create(Component.literal("§e恢复为模组默认曲库目录\n§7路径: config/mannequin/music")))
                .build());

        // ==================== 播放列表区域 ====================
        int listStartY = pathRowY + 26;
        int listHeight = 125;

        if (!tracks.isEmpty()) {
            int maxPage = Math.max(0, (tracks.size() - 1) / ITEMS_PER_PAGE);
            if (page > maxPage) {
                page = maxPage;
            }

            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, tracks.size());
            int itemY = listStartY;
            int itemHeight = 21;
            int itemGap = 3;

            for (int i = startIndex; i < endIndex; i++) {
                final int trackIndex = i;
                StudioMusicEngine.Track track = tracks.get(i);
                boolean isCurrent = (engine.getCurrentTrackIndex() == trackIndex);
                boolean isPlayingThis = isCurrent && engine.isPlaying();

                // 播放/暂停操作按钮
                String actionBtnText = isPlayingThis ? "⏸ 暂停" : (isCurrent && engine.isPaused() ? "▶ 继续" : "▶ 播放");
                addRenderableWidget(Button.builder(
                        Component.literal(actionBtnText),
                        btn -> {
                            if (isCurrent) {
                                engine.togglePlayPause();
                            } else {
                                engine.playTrack(trackIndex);
                            }
                            rebuildWidgets();
                        })
                        .bounds(startX + panelWidth - 76, itemY, 64, itemHeight)
                        .tooltip(Tooltip.create(Component.literal(isPlayingThis ? "§e暂停播放" : "§a播放该曲目")))
                        .build());

                itemY += itemHeight + itemGap;
            }

            // 分页按钮
            int pageBarY = listStartY + listHeight - 16;
            if (page > 0) {
                addRenderableWidget(Button.builder(
                        Component.literal("◀ 上页"),
                        btn -> {
                            page--;
                            rebuildWidgets();
                        })
                        .bounds(startX + 14, pageBarY, 52, 16)
                        .build());
            }

            if (page < maxPage) {
                addRenderableWidget(Button.builder(
                        Component.literal("下页 ▶"),
                        btn -> {
                            page++;
                            rebuildWidgets();
                        })
                        .bounds(startX + panelWidth - 66, pageBarY, 52, 16)
                        .build());
            }
        }

        // ==================== 底部控制栏控件 ====================
        int bottomBarY = startY + panelHeight - 34;
        int ctrlBtnHeight = 20;

        // 1. 上一首
        addRenderableWidget(Button.builder(
                Component.literal("⏮"),
                btn -> {
                    engine.previous();
                    rebuildWidgets();
                })
                .bounds(startX + 12, bottomBarY, 32, ctrlBtnHeight)
                .tooltip(Tooltip.create(Component.literal("§e播放上一首")))
                .build());

        // 2. 播放/暂停切换
        String playPauseText = engine.isPlaying() ? "⏸ 暂停" : "▶ 播放";
        addRenderableWidget(Button.builder(
                Component.literal(playPauseText),
                btn -> {
                    engine.togglePlayPause();
                    rebuildWidgets();
                })
                .bounds(startX + 48, bottomBarY, 58, ctrlBtnHeight)
                .tooltip(Tooltip.create(Component.literal("§e播放 / 暂停切换")))
                .build());

        // 3. 停止
        addRenderableWidget(Button.builder(
                Component.literal("⏹"),
                btn -> {
                    engine.stop();
                    rebuildWidgets();
                })
                .bounds(startX + 110, bottomBarY, 28, ctrlBtnHeight)
                .tooltip(Tooltip.create(Component.literal("§c停止播放")))
                .build());

        // 4. 下一首
        addRenderableWidget(Button.builder(
                Component.literal("⏭"),
                btn -> {
                    engine.next();
                    rebuildWidgets();
                })
                .bounds(startX + 142, bottomBarY, 32, ctrlBtnHeight)
                .tooltip(Tooltip.create(Component.literal("§e播放下一首")))
                .build());

        // 5. 循环模式切换
        StudioMusicEngine.LoopMode loopMode = engine.getLoopMode();
        addRenderableWidget(Button.builder(
                Component.literal(loopMode.getIcon() + " " + loopMode.getDisplayName()),
                btn -> {
                    engine.cycleLoopMode();
                    rebuildWidgets();
                })
                .bounds(startX + 178, bottomBarY, 84, ctrlBtnHeight)
                .tooltip(Tooltip.create(Component.literal("§e切换曲目循环模式\n" +
                        "§7🔁 列表循环：顺序播放全曲库并循环\n" +
                        "§7🔂 单曲循环：单首无限重复\n" +
                        "§7🔀 随机播放：随机挑选下一首\n" +
                        "§7📋 顺序播放：播放完毕自动停止")))
                .build());

        // 6. 无级音量调节滑动条 (0% ~ 100%)
        float currentVol = engine.getVolume();
        VolumeSlider volSlider = new VolumeSlider(
                startX + panelWidth - 132, bottomBarY, 120, ctrlBtnHeight,
                currentVol,
                engine::setVolume
        );
        volSlider.setTooltip(Tooltip.create(Component.literal("§e[片场音量无级微调]\n§70% ~ 100% 实时硬件级 PCM 无损增益缩放\n保证拍摄运镜时背景乐与环境音质完美平衡")));
        addRenderableWidget(volSlider);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(500, width - 20);
        int panelHeight = Math.min(270, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        // 0. 全屏半透明深色暗角，压暗背景世界并彻底隔绝模糊干扰
        g.fill(0, 0, width, height, 0x88000000);

        // 1. 实心不透明深蓝底色 (0xFF0B132B)，彻底杜绝背景世界模糊透射
        g.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0xFF0B132B);

        // 2. 外部霓虹紫金质感边框
        int borderMagenta = 0xFFD946EF;
        g.fill(startX, startY, startX + panelWidth, startY + 1, borderMagenta);
        g.fill(startX, startY + panelHeight - 1, startX + panelWidth, startY + panelHeight, borderMagenta);
        g.fill(startX, startY, startX + 1, startY + panelHeight, borderMagenta);
        g.fill(startX + panelWidth - 1, startY, startX + panelWidth, startY + panelHeight, borderMagenta);

        // 3. 顶部标题 (带阴影)
        if (font != null) {
            g.drawString(font, "§d§l片场配乐与背景曲库 §7(Studio Music Player)", startX + 12, startY + 8, 0xFFFFFFFF, true);
            g.drawString(font, "§f原生解码推流 §8| §b支持 MP3 / WAV / OGG §8| §a异步推流不占帧率", startX + 12, startY + 20, 0xFFE2E8F0, true);

            // 路径前缀标签 (带阴影)
            g.drawString(font, "§e曲库目录:", startX + 12, startY + 40, 0xFFFFFFFF, true);
        }

        // 4. 渲染列表区域
        StudioMusicEngine engine = StudioMusicEngine.INSTANCE;
        List<StudioMusicEngine.Track> tracks = engine.getPlaylist();

        int pathRowY = startY + 34;
        int listStartY = pathRowY + 26;
        int listHeight = 125;

        // 列表背景板 (实心深色底框)
        g.fill(startX + 12, listStartY, startX + panelWidth - 12, listStartY + listHeight, 0xFF111827);

        if (tracks.isEmpty()) {
            if (font != null) {
                int cx = startX + panelWidth / 2;
                g.drawString(font, "§e§l[提示] 曲库文件夹内暂无音频文件 (.mp3, .wav, .ogg)", cx - font.width("[提示] 曲库文件夹内暂无音频文件 (.mp3, .wav, .ogg)") / 2, listStartY + 30, 0xFFFDE047, true);
                g.drawString(font, "§f请点击右上角【打开目录】将音乐拖入文件夹，然后点击【刷新】！", cx - font.width("请点击右上角【打开目录】将音乐拖入文件夹，然后点击【刷新】！") / 2, listStartY + 54, 0xFFFFFFFF, true);
                g.drawString(font, "§b支持格式：MP3 (MPEG-1 Layer 3)、WAV (16-bit PCM)、OGG (Vorbis)", cx - font.width("支持格式：MP3 (MPEG-1 Layer 3)、WAV (16-bit PCM)、OGG (Vorbis)") / 2, listStartY + 74, 0xFF7DD3FC, true);
            }
        } else {
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, tracks.size());
            int itemY = listStartY;
            int itemHeight = 21;
            int itemGap = 3;

            for (int i = startIndex; i < endIndex; i++) {
                StudioMusicEngine.Track track = tracks.get(i);
                boolean isCurrent = (engine.getCurrentTrackIndex() == i);
                boolean isPlayingThis = isCurrent && engine.isPlaying();
                boolean isPausedThis = isCurrent && engine.isPaused();

                // 单项背景框 (实心)
                int bgColor = isCurrent ? 0xFF2E1065 : 0xFF1E293B;
                int itemRight = startX + panelWidth - 12;
                g.fill(startX + 12, itemY, itemRight, itemY + itemHeight, bgColor);

                if (isCurrent) {
                    // 当前歌曲高亮边框
                    int highlightColor = isPlayingThis ? 0xFF10B981 : 0xFFF59E0B;
                    g.fill(startX + 12, itemY, startX + 15, itemY + itemHeight, highlightColor);
                }

                if (font != null) {
                    // 序号与状态图标 (带阴影)
                    String statusPrefix = isPlayingThis ? "§a▶ " : (isPausedThis ? "§e⏸ " : String.format("§7#%02d ", i + 1));
                    g.drawString(font, statusPrefix, startX + 18, itemY + 6, 0xFFFFFFFF, true);

                    // 格式徽章 [MP3] / [WAV] / [OGG]
                    String formatTag = switch (track.format().toUpperCase()) {
                        case "MP3" -> "§b[MP3]§r ";
                        case "WAV" -> "§d[WAV]§r ";
                        case "OGG" -> "§e[OGG]§r ";
                        default -> "§7[" + track.format() + "]§r ";
                    };

                    // 曲目标题 (带阴影)
                    int maxTitleWidth = panelWidth - 210;
                    String titleDisplay = font.plainSubstrByWidth(track.title(), maxTitleWidth);
                    if (titleDisplay.length() < track.title().length()) {
                        titleDisplay += "...";
                    }
                    String fullTitle = formatTag + (isCurrent ? "§6§l" : "§f") + titleDisplay;
                    g.drawString(font, fullTitle, startX + 46, itemY + 6, 0xFFFFFFFF, true);

                    // 文件大小 (高对比度带阴影)
                    String sizeText = formatFileSize(track.fileSizeBytes());
                    g.drawString(font, "§7" + sizeText, startX + panelWidth - 140, itemY + 6, 0xFFCBD5E1, true);
                }

                itemY += itemHeight + itemGap;
            }

            // 分页居中文字 (带阴影)
            if (font != null) {
                int maxPage = Math.max(0, (tracks.size() - 1) / ITEMS_PER_PAGE);
                String pageText = String.format("§7第 §f%d§7 / §f%d§7 页 (共 §e%d§7 首)", page + 1, maxPage + 1, tracks.size());
                g.drawString(font, pageText, (startX + panelWidth / 2) - font.width(pageText) / 2, listStartY + listHeight - 12, 0xFFFFFFFF, true);
            }
        }

        // 5. 底部播放状态与控制板底框 (实心)
        int bottomBarY = startY + panelHeight - 56;
        g.fill(startX + 12, bottomBarY, startX + panelWidth - 12, startY + panelHeight - 8, 0xFF1E293B);

        if (font != null) {
            StudioMusicEngine.Track current = engine.getCurrentTrack();
            String nowPlayingText;
            if (current != null && (engine.isPlaying() || engine.isPaused())) {
                String stateTag = engine.isPaused() ? "§e[已暂停]" : "§a[正在播放]";
                nowPlayingText = "§d🎵 当前: §f" + current.title() + " §7[" + current.format() + "] " + stateTag;
            } else {
                nowPlayingText = "§7🎵 播放器待机中 (点击曲目右侧播放按钮开始)";
            }
            g.drawString(font, nowPlayingText, startX + 16, bottomBarY + 5, 0xFFFFFFFF, true);
        }

        // 6. 渲染子按钮组件及 Tooltips
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (parentScreen != null && minecraft != null) {
            minecraft.setScreen(parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停单人游戏，背景音乐与排演无缝同步
    }

    /**
     * 连续音量调节无级滑动条。
     */
    private static class VolumeSlider extends AbstractSliderButton {
        private final java.util.function.Consumer<Float> onApply;

        public VolumeSlider(int x, int y, int width, int height, float initialValue,
                            java.util.function.Consumer<Float> onApply) {
            super(x, y, width, height, Component.empty(), initialValue);
            this.onApply = onApply;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int pct = (int) Math.round(this.value * 100.0);
            setMessage(Component.literal("🔊 音量: " + pct + "%"));
        }

        @Override
        protected void applyValue() {
            onApply.accept((float) this.value);
        }
    }
}
