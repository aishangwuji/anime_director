package com.mannequin.client.gui.tutorial;

import com.mannequin.client.timeline.MasterClockEngine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 游戏内原生的漫剧导演新手教学手册界面（Director In-Game Tutorial Screen）。
 *
 * <p>本界面在游戏内提供沉浸式、分章节图文并茂的完整制片教程，支持：
 * <ul>
 *   <li>6 大核心教程章节切换（角色染色、提线动捕、穿越机运镜、竖屏遮罩、AI 提示词、快捷键清单）；</li>
 *   <li>按键高亮提示与实战操作步骤拆解；</li>
 *   <li>随时通过快捷键 [H] 或右键「导演指南手册」呼出与查阅。</li>
 * </ul>
 */
public class DirectorTutorialScreen extends Screen {

    private static final String[] TAB_LABELS = {
            "角色染色", "提线动捕", "FPV运镜", "画幅构图", "AI技巧", "快捷键"
    };

    private int activeChapter = 0;
    private int scrollOffset = 0;

    /**
     * 教程章节数据定义。
     */
    private record TutorialChapter(String title, List<String> lines) {
    }

    private final List<TutorialChapter> chapters = new ArrayList<>();

    public DirectorTutorialScreen() {
        super(Component.literal("漫剧导演制作实训手册"));
        initChapters();
    }

    private void initChapters() {
        chapters.clear();

        // 1. 角色染色
        chapters.add(new TutorialChapter("1. 角色代理与染色", List.of(
                "§6【核心原理：纯色替身与语义解耦】",
                "在制作漫剧视频时，AI 大模型最容易发生“多角色同框串色”问题。",
                "本 Mod 采用业界成熟的纯色色块代理方案：",
                "",
                "§e• 放置人偶：§7右键地面放置 7 头身极简无贴图人偶。",
                "§e• 染料染色：§7手持原版染料右键人偶即可更换高纯度单色：",
                "  - §c纯红人偶 §7= 代表角色 A（如刺客/主角）",
                "  - §9纯蓝人偶 §7= 代表角色 B（如反派/警探）",
                "  - §a纯绿人偶 §7= 代表角色 C / 场景关键道具",
                "",
                "§a【优势】：§7后期进入剪辑软件或 ComfyUI 时，使用吸管工具 1 秒就能提纯",
                "出无瑕疵的 Alpha 蒙版，或者让大模型通过颜色强相关性锁定身份。"
        )));

        // 2. 提线动捕
        chapters.add(new TutorialChapter("2. 提线动捕与分轨叠录", List.of(
                "§6【核心原理：像录制音乐一样一人分饰多角】",
                "无需繁琐调关键帧！导演化身演员，分批次把各个角色的运动“演”出来：",
                "",
                "§e• 附身操纵：§7准星对准红人偶按 §b[G]§7 键，视角瞬间切入该人偶第一人称。",
                "§e• 动捕录制：§7按 §b[K]§7 键启动录制，时间轴开始走字，按 §bWASD§7 操纵跑位。",
                "  - 例如在第 3 秒向右急转弯假装闪避，跑满 6 秒自动停止。",
                "§e• 一键倒带：§7按 §b[R]§7 键，红人偶瞬间吸回第 0 秒起点初始姿势！",
                "§e• 幽灵伴跑：§7接着去附身第二台汽车开动，刚才录好的红人偶将在旁边",
                "  §a完全自动重播闪避动作§7！你开着车擦身而过，完成极致多人互动。"
        )));

        // 3. 上帝自由飞控
        chapters.add(new TutorialChapter("3. 上帝自由运镜", List.of(
                "§6【核心原理：平滑阻尼、自由穿梭与上帝视角】",
                "打破地面角色束缚，化身全知全能的上帝视角导演：",
                "",
                "§e• 开启上帝视角：§7随时按 §b[F6]§7 键脱离角色本体，进入自由上帝飞控视角。",
                "§e• 隐去本体：§7上帝视角下默认自动隐藏玩家模型，按 §b[F8]§7 随时切换本体显隐。",
                "§e• 自由转向：§7鼠标自由 360° 无限制环视，彻底解耦，玩家本体绝不乱晃。",
                "§e• 极速巡场：§7WASD 空间飞行，按住 §b[Ctrl]§7 疾跑键可获 2.5 倍极速高空巡场！",
                "§e• 昆虫微观视角：§7支持近裁剪面优化（0.001），贴地 1 厘米草皮掠过、",
                "  穿过车底钻过鞋底时§a绝对不会发生几何体穿模破面§7！"
        )));

        // 4. 构图与录屏
        chapters.add(new TutorialChapter("4. 构图遮罩与实拍", List.of(
                "§6【核心原理：专为竖屏短视频漫剧定制】",
                "常规 Minecraft 画面为 16:9，无法直观把握抖音/快手/TikTok 竖屏构图：",
                "",
                "§e• 切换画幅：§7按键盘 §b[V]§7 键循环切换画幅遮罩：",
                "  - §e9:16 竖屏漫剧§7（短视频/竖屏漫剧黄金标准，两侧精准黑边）",
                "  - §e16:9 标准横屏§7（常规番剧/横屏动画）",
                "  - §e21:9 电影宽银幕§7（强压迫感电影构图）",
                "§e• 构图辅助：§7安全区内内置高对比度黄金三分线，人物站位一目了然。",
                "§e• 纯净实拍（Clean Feed）：§7打开 OBS 准备录屏前，按原版 §b[F1]§7 键，",
                "  模组自动隐去所有文字和辅助线，仅保留纯黑遮罩，输出干净生肉！"
        )));

        // 5. 视频实拍与 AI 工具链
        chapters.add(new TutorialChapter("5. 视频实拍与 AI 生产", List.of(
                "§6【直出 MP4 参考视频：怎么喂给视频大模型？】",
                "",
                "§e• 直出标准 H.264 MP4 分镜视频：",
                "  在快捷菜单 [C] 或导播监视大厅中，点击 §b[录制当前机位]§7 或 §b[批量录制 MP4]§7，",
                "  系统将自动倒带并播放排演，实时编码为 MP4 视频，直接存入视频文件夹！",
                "",
                "§e• 视频生视频（Kling / 可灵 / 剪映 / PR）：",
                "  生成的 MP4 视频可直接拖入可灵 (Kling) 的【参考视频 / 视频生视频】控制区，",
                "  以纯净人偶走位和真实的镜头景别、焦距运动作为强引导，生成动作完美的漫剧成片！",
                "",
                "§e• 颜色对应角色设定（Prompt 提示词模板）：",
                "  §f\"A cinematic anime scene, high dynamic camera movement, cyberpunk alley.",
                "  §cThe pure red figure represents a male detective with silver hair in coat, dodging.",
                "  §9The pure blue vehicle represents a speeding cyber neon hover-car.\""
        )));

        // 6. 快捷键
        chapters.add(new TutorialChapter("6. 导演快捷键大全", List.of(
                "§6【常用按键速查一览】（可在原版控制设置中自定义）：",
                "",
                "  §b[C]         §f呼出导演全功能快捷控制台（聚合全模组操作）",
                "  §b[F4]        §f一键原子级切换「🔨场景搭建」与「🎬运镜实拍」模式",
                "  §b[F6]        §f开启 / 退出上帝自由视角（导演自由相机）",
                "  §b[F8]        §f显隐导演玩家本体模型（上帝视角下默认隐藏）",
                "  §b[F9]        §f切换运镜飞控风格：防抖平稳 (云台) vs 穿越机 (特技航模)",
                "  §b[F10]       §f开启 / 停止主视角实时运镜录像（直出 MP4 视频）",
                "  §b[J]         §f循环切换自由相机多档位航速预设 (0.5 ~ 16 m/s)",
                "  §b[B]         §f打下分镜固定机位 (按住 Shift+B 覆盖更新当前机位)",
                "  §b[G]         §f对准人偶/载具一键附身 / 退出附身归还视角",
                "  §b[K]         §f附身后开始 / 停止动捕录制路线与位移",
                "  §b[P]         §f启动 / 暂停全场排演同步回放",
                "  §b[R]         §f一键倒带复位回第 0 秒初始站桩",
                "  §b[V]         §f循环切换 9:16 / 16:9 / 21:9 画幅遮罩",
                "  §b[F7]        §f一键隐藏/显示屏幕常驻操作指引卡片",
                "  §b[Alt]       §f自由相机中按住进入 0.25x 超微移爬行 (死磕构图神器)",
                "  §b[Shift+滚轮]§f准星对准人偶无级缩放体型 (5%微缩 ~ 2000%巨物)",
                "  §b[PgUp/PgDn] §f镜头焦距拉近 (特写) / 拉远 (广角)",
                "  §b[Z] / [X]   §f镜头向左 / 向右手动倾斜滚转（Roll）",
                "  §b[N]         §f镜头倾斜角一键回正复位 (Roll 归零)",
                "  §b[Shift+Del] §f一键彻底清空全场所有动捕轨道与滑轨机位",
                "  §b[H]         §f打开 / 关闭本新手教程手册",
                "  §b[]] / [[]   §f延长 / 缩短场景录制时长（每档 1 秒）",
                "",
                "§6【建筑蓝图仪快捷键】: §7左键角点 A | 右键角点 B | 滚轮旋转 | Shift+滚轮升降 | Enter 保存 | Delete 清空"
        )));
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(420, width - 20);
        int panelHeight = Math.min(260, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        // 顶部右上角醒目关闭按钮 (✕)
        addRenderableWidget(Button.builder(Component.literal("✕"), btn -> onClose())
                .bounds(startX + panelWidth - 22, startY + 3, 18, 18)
                .tooltip(Tooltip.create(Component.literal("§c关闭手册 (H / ESC)")))
                .build());

        // 章节切换按钮
        int tabY = startY + 26;
        int tabWidth = (panelWidth - 10) / chapters.size();
        for (int i = 0; i < chapters.size(); i++) {
            final int index = i;
            String label = (i < TAB_LABELS.length) ? TAB_LABELS[i] : ("P" + (i + 1));
            String fullTitle = chapters.get(i).title();
            addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                this.activeChapter = index;
                this.scrollOffset = 0;
            })
                    .bounds(startX + 5 + i * tabWidth, tabY, tabWidth - 2, 18)
                    .tooltip(Tooltip.create(Component.literal("§e" + fullTitle)))
                    .build());
        }

        // 底部上一页、下一页、HUD提示开关、打开视频目录、关闭按钮
        int btnY = startY + panelHeight - 26;
        addRenderableWidget(Button.builder(Component.literal("◀ 上一章"), btn -> {
            if (activeChapter > 0) {
                activeChapter--;
                scrollOffset = 0;
            }
        }).bounds(startX + 8, btnY, 58, 20).build());

        addRenderableWidget(Button.builder(Component.literal("下一章 ▶"), btn -> {
            if (activeChapter < chapters.size() - 1) {
                activeChapter++;
                scrollOffset = 0;
            }
        }).bounds(startX + 70, btnY, 58, 20).build());

        // HUD 浮窗提示开启/关闭切换按钮
        var hudOverlay = com.mannequin.client.gui.TimelineHudOverlay.INSTANCE;
        Button hudTipsBtn = Button.builder(
                Component.literal(hudOverlay.areTipsVisible() ? "HUD提示: 开" : "HUD提示: 关"),
                btn -> {
                    hudOverlay.toggleTips();
                    boolean vis = hudOverlay.areTipsVisible();
                    btn.setMessage(Component.literal(vis ? "HUD提示: 开" : "HUD提示: 关"));
                }
        ).bounds(startX + 132, btnY, 82, 20)
         .tooltip(Tooltip.create(Component.literal("§7开启或关闭游戏主界面上的新手提示框与底部引导 (快捷键: F7)")))
         .build();
        addRenderableWidget(hudTipsBtn);

        addRenderableWidget(Button.builder(Component.literal("打开视频目录"), btn -> com.mannequin.client.camera.MultiCameraManager.INSTANCE.openExportFolder())
                .bounds(startX + 218, btnY, 90, 20)
                .tooltip(Tooltip.create(Component.literal("§7打开录制的 MP4 参考视频所在文件夹")))
                .build());

        addRenderableWidget(Button.builder(Component.literal("关闭 (ESC)"), btn -> onClose())
                .bounds(startX + panelWidth - 75, btnY, 68, 20)
                .build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - (int) (scrollY * 16));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_H || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景暗色遮罩
        renderBackground(g, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(420, width - 20);
        int panelHeight = Math.min(260, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        // 手册主面板底框 (实心不透明深蓝黑底，杜绝背景世界模糊透射)
        g.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0xFF111827);
        // 顶部标题栏
        g.fill(startX, startY, startX + panelWidth, startY + 24, 0xFF1F2937);
        g.fill(startX, startY, startX + panelWidth, startY + 2, 0xFF00E5FF); // 顶部青色流光饰条

        // 绘制当前激活 Tab 的青色底部高亮条
        int tabY = startY + 26;
        int tabWidth = (panelWidth - 10) / chapters.size();
        int activeTabX = startX + 5 + activeChapter * tabWidth;
        g.fill(activeTabX, tabY + 16, activeTabX + tabWidth - 2, tabY + 18, 0xFF00E5FF);

        // 标题文字 (带阴影)
        String headTitle = "§6§l漫剧导演制片实训手册 §7(按 H 或 ESC 关闭)";
        g.drawString(font, headTitle, (width - font.width(headTitle)) / 2, startY + 7, 0xFFFFFFFF, true);

        // 渲染当前章节标题与文本内容（带裁剪与自动换行）
        if (activeChapter >= 0 && activeChapter < chapters.size()) {
            TutorialChapter chapter = chapters.get(activeChapter);
            g.drawString(font, "§b【" + chapter.title() + "】", startX + 12, startY + 48, 0xFFFFFFFF, true);

            int contentTop = startY + 60;
            int contentBottom = startY + panelHeight - 30;
            int maxTextWidth = panelWidth - 24;

            // 启用裁剪区域，彻底防止文本溢出遮挡底部操作按钮
            g.enableScissor(startX + 8, contentTop, startX + panelWidth - 8, contentBottom);

            int textY = contentTop - scrollOffset;
            int totalTextHeight = 0;

            for (String rawLine : chapter.lines()) {
                if (rawLine.isEmpty()) {
                    textY += 6;
                    totalTextHeight += 6;
                    continue;
                }
                var formattedLines = font.split(Component.literal(rawLine), maxTextWidth);
                for (var seq : formattedLines) {
                    if (textY + 9 >= contentTop && textY <= contentBottom) {
                        g.drawString(font, seq, startX + 12, textY, 0xFFFFFFFF, true);
                    }
                    textY += 11;
                    totalTextHeight += 11;
                }
            }

            g.disableScissor();

            // 若内容超出视口，在右侧绘制滚动条
            int availableHeight = contentBottom - contentTop;
            if (totalTextHeight > availableHeight) {
                int maxScroll = totalTextHeight - availableHeight;
                if (scrollOffset > maxScroll) scrollOffset = maxScroll;

                int scrollBarH = Math.max(12, availableHeight * availableHeight / totalTextHeight);
                int scrollBarY = contentTop + (availableHeight - scrollBarH) * scrollOffset / maxScroll;
                g.fill(startX + panelWidth - 7, contentTop, startX + panelWidth - 5, contentBottom, 0x44000000);
                g.fill(startX + panelWidth - 7, scrollBarY, startX + panelWidth - 5, scrollBarY + scrollBarH, 0xFF00E5FF);
            } else {
                scrollOffset = 0;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 查看教程时不强行暂停单人世界，方便边看边试
    }
}
