package com.mannequin.client.gui;

import com.mannequin.building.BuildingBlueprintHelper;
import com.mannequin.client.building.BuildingSelectionManager;
import com.mannequin.network.ExportBuildingPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 漫剧片场建筑蓝图库与导出管理器界面（Building Library Screen）。
 */
public class BuildingLibraryScreen extends Screen {

    public enum Tab {
        LIBRARY,
        EXPORT
    }

    private Tab activeTab = Tab.LIBRARY;
    private List<BuildingBlueprintHelper.BlueprintInfo> blueprints = List.of();
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 4;

    // 导出表单控件
    private EditBox nameInput;
    private EditBox authorInput;
    private EditBox descInput;

    public BuildingLibraryScreen() {
        super(Component.literal("片场建筑蓝图库"));
    }

    @Override
    protected void init() {
        refreshBlueprints();

        int panelWidth = Math.min(480, width - 20);
        int panelHeight = Math.min(260, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        // 顶部选项卡切换
        addRenderableWidget(Button.builder(
                Component.literal("建筑蓝图库 (" + blueprints.size() + ")"),
                btn -> {
                    activeTab = Tab.LIBRARY;
                    rebuildWidgets();
                })
                .bounds(startX + 12, startY + 8, 120, 20)
                .build());

        BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;
        boolean hasSel = bsm.hasSelection();
        addRenderableWidget(Button.builder(
                Component.literal("选区打包 " + (hasSel ? "§a[就绪]" : "§7[未选]")),
                btn -> {
                    activeTab = Tab.EXPORT;
                    rebuildWidgets();
                })
                .bounds(startX + 136, startY + 8, 120, 20)
                .build());

        // 顶部右侧工具按钮：打开文件夹
        addRenderableWidget(Button.builder(
                Component.literal("打开目录"),
                btn -> BuildingBlueprintHelper.openBlueprintsFolder())
                .bounds(startX + panelWidth - 146, startY + 8, 68, 20)
                .tooltip(Tooltip.create(Component.literal("§e在 Windows 资源管理器中打开蓝图存储文件夹\n§7可直接将好友发送的 .nbt 建筑文件拖入此目录！")))
                .build());

        // 顶部右侧工具按钮：刷新列表
        addRenderableWidget(Button.builder(
                Component.literal("刷新"),
                btn -> {
                    refreshBlueprints();
                    rebuildWidgets();
                })
                .bounds(startX + panelWidth - 74, startY + 8, 44, 20)
                .tooltip(Tooltip.create(Component.literal("§e刷新本地蓝图列表")))
                .build());

        // 顶部右侧关闭按钮
        addRenderableWidget(Button.builder(
                Component.literal("✕"),
                btn -> onClose())
                .bounds(startX + panelWidth - 26, startY + 8, 18, 20)
                .tooltip(Tooltip.create(Component.literal("§c关闭 (ESC)")))
                .build());

        // ==================== 选项卡内容区域 ====================
        int contentY = startY + 36;
        int contentHeight = panelHeight - 44;

        if (activeTab == Tab.LIBRARY) {
            initLibraryTab(startX, contentY, panelWidth, contentHeight);
        } else {
            initExportTab(startX, contentY, panelWidth, contentHeight);
        }
    }

    private void refreshBlueprints() {
        this.blueprints = BuildingBlueprintHelper.listBlueprints();
    }

    private void initLibraryTab(int startX, int startY, int panelWidth, int contentHeight) {
        if (blueprints.isEmpty()) {
            return;
        }

        int maxPage = Math.max(0, (blueprints.size() - 1) / ITEMS_PER_PAGE);
        if (page > maxPage) {
            page = maxPage;
        }

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, blueprints.size());

        int itemY = startY + 4;
        int itemHeight = 36;
        int gap = 6;

        for (int i = startIndex; i < endIndex; i++) {
            BuildingBlueprintHelper.BlueprintInfo bp = blueprints.get(i);
            int currentItemY = itemY;

            // 放置按钮
            addRenderableWidget(Button.builder(
                    Component.literal("▶ 选择放置"),
                    btn -> {
                        BuildingSelectionManager.INSTANCE.setSelectedBlueprint(bp);
                        onClose();
                        if (minecraft != null && minecraft.player != null) {
                            minecraft.player.displayClientMessage(
                                    Component.literal(String.format("§a[建筑蓝图] 已选定「%s」！请手持蓝图仪对准地面右键放置 (按 [R] 旋转方向)", bp.name())),
                                    true
                            );
                        }
                    })
                    .bounds(startX + panelWidth - 170, currentItemY + 6, 86, 22)
                    .tooltip(Tooltip.create(Component.literal("§e选择此建筑进入地面全息对齐放置模式\n§7手持蓝图仪对准地面右键即可瞬间生成！")))
                    .build());

            // 删除按钮
            addRenderableWidget(Button.builder(
                    Component.literal("🗑"),
                    btn -> {
                        BuildingBlueprintHelper.deleteBlueprintFile(bp.fileName());
                        refreshBlueprints();
                        rebuildWidgets();
                    })
                    .bounds(startX + panelWidth - 78, currentItemY + 6, 26, 22)
                    .tooltip(Tooltip.create(Component.literal("§c删除此本地建筑蓝图文件")))
                    .build());

            itemY += itemHeight + gap;
        }

        // 分页控制按钮（底部）
        int bottomY = startY + contentHeight - 26;
        if (page > 0) {
            addRenderableWidget(Button.builder(
                    Component.literal("◀ 上一页"),
                    btn -> {
                        page--;
                        rebuildWidgets();
                    })
                    .bounds(startX + 16, bottomY, 70, 20)
                    .build());
        }

        if (page < maxPage) {
            addRenderableWidget(Button.builder(
                    Component.literal("下一页 ▶"),
                    btn -> {
                        page++;
                        rebuildWidgets();
                    })
                    .bounds(startX + panelWidth - 86, bottomY, 70, 20)
                    .build());
        }
    }

    private void initExportTab(int startX, int startY, int panelWidth, int contentHeight) {
        BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;
        if (!bsm.hasSelection()) {
            return;
        }

        int inputX = startX + 80;
        int inputWidth = panelWidth - 100;
        int currentY = startY + 44;

        // 1. 建筑名称输入框
        nameInput = new EditBox(font, inputX, currentY, inputWidth, 18, Component.literal("建筑名称"));
        nameInput.setMaxLength(40);
        nameInput.setValue("新建片场建筑_" + (System.currentTimeMillis() % 1000));
        addRenderableWidget(nameInput);
        currentY += 26;

        // 2. 创作者署名输入框
        authorInput = new EditBox(font, inputX, currentY, inputWidth, 18, Component.literal("作者名称"));
        authorInput.setMaxLength(30);
        authorInput.setValue(minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "片场置景师");
        addRenderableWidget(authorInput);
        currentY += 26;

        // 3. 描述说明输入框
        descInput = new EditBox(font, inputX, currentY, inputWidth, 18, Component.literal("描述说明"));
        descInput.setMaxLength(80);
        descInput.setValue("精选漫剧片场布景");
        addRenderableWidget(descInput);
        currentY += 34;

        // 4. 一键导出执行按钮
        addRenderableWidget(Button.builder(
                Component.literal("💾 打包并导出为 .NBT 建筑文件"),
                btn -> {
                    String bName = nameInput.getValue();
                    String bAuthor = authorInput.getValue();
                    String bDesc = descInput.getValue();
                    String fileName = BuildingBlueprintHelper.sanitizeFileName(bName) + ".nbt";

                    BlockPos a = bsm.getPosA();
                    BlockPos b = bsm.getPosB();

                    // 发送网络包由服务端进行安全序列化落盘
                    PacketDistributor.sendToServer(new ExportBuildingPayload(a, b, fileName, bName, bAuthor, bDesc));

                    // 切换回建筑库标签
                    activeTab = Tab.LIBRARY;
                    refreshBlueprints();
                    rebuildWidgets();
                })
                .bounds(startX + (panelWidth - 240) / 2, currentY, 240, 24)
                .tooltip(Tooltip.create(Component.literal("§a将框选区域内的方块结构完整打包为单文件！\n§7导出后可直接复制发给朋友或在片场库一键生成")))
                .build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R) {
            BuildingSelectionManager.INSTANCE.rotatePlacement();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int panelWidth = Math.min(480, width - 20);
        int panelHeight = Math.min(260, height - 20);
        int startX = (width - panelWidth) / 2;
        int startY = (height - panelHeight) / 2;

        // 0. 全屏半透明深色暗角，压暗背景世界并彻底隔绝模糊干扰
        g.fill(0, 0, width, height, 0x88000000);

        // 1. 实心不透明深色面板背景 (0xFF0D1527)，确保文字边缘像素 100% 清晰不发虚
        g.fill(startX, startY, startX + panelWidth, startY + panelHeight, 0xFF0D1527);

        // 2. 外部金黄/琥珀高质感边框
        int borderGold = 0xFFF59E0B;
        g.fill(startX, startY, startX + panelWidth, startY + 1, borderGold);
        g.fill(startX, startY + panelHeight - 1, startX + panelWidth, startY + panelHeight, borderGold);
        g.fill(startX, startY, startX + 1, startY + panelHeight, borderGold);
        g.fill(startX + panelWidth - 1, startY, startX + panelWidth, startY + panelHeight, borderGold);

        int contentY = startY + 36;
        int contentHeight = panelHeight - 44;

        if (activeTab == Tab.LIBRARY) {
            renderLibraryTab(g, startX, contentY, panelWidth, contentHeight);
        } else {
            renderExportTab(g, startX, contentY, panelWidth, contentHeight);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderLibraryTab(GuiGraphics g, int startX, int startY, int panelWidth, int contentHeight) {
        if (blueprints.isEmpty()) {
            int cardX = startX + 20;
            int cardY = startY + 16;
            int cardW = panelWidth - 40;
            int cardH = contentHeight - 24;

            // 实心卡片底板与深青边框
            g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF1E293B);
            g.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF38BDF8);
            g.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF38BDF8);
            g.fill(cardX, cardY, cardX + 1, cardY + cardH, 0xFF38BDF8);
            g.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0xFF38BDF8);

            int cx = startX + panelWidth / 2;
            g.drawString(font, "§e§l[提示] 蓝图库暂无建筑文件", cx - font.width("[提示] 蓝图库暂无建筑文件") / 2, cardY + 28, 0xFFFDE047, true);
            g.drawString(font, "§f点击右上角【打开目录】将好友分享的 .nbt 建筑直接丢入文件夹，", cx - font.width("点击右上角【打开目录】将好友分享的 .nbt 建筑直接丢入文件夹，") / 2, cardY + 54, 0xFFFFFFFF, true);
            g.drawString(font, "§b或在游戏中使用【片场建筑蓝图仪】框选任意建筑并导出！", cx - font.width("或在游戏中使用【片场建筑蓝图仪】框选任意建筑并导出！") / 2, cardY + 74, 0xFF7DD3FC, true);
            return;
        }

        int maxPage = Math.max(0, (blueprints.size() - 1) / ITEMS_PER_PAGE);
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, blueprints.size());

        int itemY = startY + 4;
        int itemHeight = 36;
        int gap = 6;

        for (int i = startIndex; i < endIndex; i++) {
            BuildingBlueprintHelper.BlueprintInfo bp = blueprints.get(i);

            // 实心卡片背景与细边框
            g.fill(startX + 14, itemY, startX + panelWidth - 14, itemY + itemHeight, 0xFF1E293B);
            g.fill(startX + 14, itemY, startX + panelWidth - 14, itemY + 1, 0xFF475569);
            g.fill(startX + 14, itemY + itemHeight - 1, startX + panelWidth - 14, itemY + itemHeight, 0xFF475569);

            // 建筑名称 (带阴影，清晰锐利)
            g.drawString(font, "§f§l" + bp.name(), startX + 22, itemY + 6, 0xFFFFFFFF, true);

            // 尺寸与体积 (高对比度)
            String info = String.format("§e尺寸: §f%s §8| §b作者: §f%s §8| §a大小: §f%.1f KB",
                    bp.getDimensionsText(),
                    bp.author(),
                    bp.fileSizeBytes() / 1024.0
            );
            g.drawString(font, info, startX + 22, itemY + 20, 0xFFE2E8F0, true);

            itemY += itemHeight + gap;
        }

        // 分页指示 (带阴影)
        String pageStr = String.format("§7第 §f%d §7/ §f%d §7页 (共 §e%d §7个建筑)", page + 1, maxPage + 1, blueprints.size());
        g.drawString(font, pageStr, (startX + panelWidth / 2) - font.width(pageStr) / 2, startY + contentHeight - 20, 0xFFFFFFFF, true);
    }

    private void renderExportTab(GuiGraphics g, int startX, int startY, int panelWidth, int contentHeight) {
        BuildingSelectionManager bsm = BuildingSelectionManager.INSTANCE;
        if (!bsm.hasSelection()) {
            int cardX = startX + 20;
            int cardY = startY + 16;
            int cardW = panelWidth - 40;
            int cardH = contentHeight - 24;

            // 实心卡片底板与朱红警示边框，彻底告别文字发虚模糊
            g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xFF1E293B);
            g.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFFEF4444);
            g.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFFEF4444);
            g.fill(cardX, cardY, cardX + 1, cardY + cardH, 0xFFEF4444);
            g.fill(cardX + cardW - 1, cardY, cardX + cardW, cardY + cardH, 0xFFEF4444);

            int cx = startX + panelWidth / 2;
            int textY = cardY + 18;
            g.drawString(font, "§c§l● 未检测到有效框选区域", cx - font.width("● 未检测到有效框选区域") / 2, textY, 0xFFFF5555, true);
            textY += 24;

            g.drawString(font, "§f请先手持 §e【片场建筑蓝图仪】§f 进行场景框选：", cardX + 24, textY, 0xFFFFFFFF, true);
            textY += 18;

            g.drawString(font, "§b1. 左键点击方块 §7— 锁定三维立体选区角点 A", cardX + 32, textY, 0xFFE0E7FF, true);
            textY += 16;

            g.drawString(font, "§b2. 右键点击方块 §7— 锁定三维立体选区角点 B", cardX + 32, textY, 0xFFE0E7FF, true);
            textY += 22;

            g.drawString(font, "§a★ 技巧：框选完整建筑后，回到此处即可一键打包导出为 .nbt 单文件！", cardX + 24, textY, 0xFF86EFAC, true);
            return;
        }

        Vec3i size = bsm.getSelectionSize();
        long volume = (long) size.getX() * size.getY() * size.getZ();

        // 选区信息摘要 (高对比度带阴影)
        String summary = String.format("§6当前选区: §e%d × %d × %d §7(长×高×宽，共 §a%,d §7方块)", size.getX(), size.getY(), size.getZ(), volume);
        g.drawString(font, summary, startX + 20, startY + 16, 0xFFFFFFFF, true);

        // 标签文字 (白色清晰带阴影)
        g.drawString(font, "§f建筑名称:", startX + 20, startY + 48, 0xFFFFFFFF, true);
        g.drawString(font, "§f创作者:", startX + 20, startY + 74, 0xFFFFFFFF, true);
        g.drawString(font, "§f描述说明:", startX + 20, startY + 100, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
