package com.mannequin.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 导演统一数值微调滑动条（支持无级滑动拖拽 + 点击数字直接文本输入具体数值）。
 */
public class DirectorNumericSlider extends AbstractSliderButton {

    private final double minValue;
    private final double maxValue;
    private final String prefix;
    private final String suffix;
    private final int decimalPlaces;
    private final boolean isPercentage;
    private final Consumer<Double> onApply;

    private boolean editing = false;
    private final EditBox editBox;
    private int numBoxX;
    private int numBoxY;
    private int numBoxW;
    private int numBoxH;

    public DirectorNumericSlider(int x, int y, int width, int height,
                                 double minValue, double maxValue, double initialActualValue,
                                 String prefix, String suffix, int decimalPlaces, boolean isPercentage,
                                 Consumer<Double> onApply) {
        super(x, y, width, height, Component.empty(),
                Math.max(0.0, Math.min(1.0, (initialActualValue - minValue) / (maxValue - minValue))));
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.prefix = prefix != null ? prefix : "";
        this.suffix = suffix != null ? suffix : "";
        this.decimalPlaces = decimalPlaces;
        this.isPercentage = isPercentage;
        this.onApply = onApply;

        Font font = Minecraft.getInstance().font;
        this.editBox = new EditBox(font, x, y, width, height, Component.empty());
        this.editBox.setBordered(true);
        this.editBox.setFilter(s -> s.matches("^-?\\d*\\.?\\d*$"));

        updateMessage();
    }

    public double getActualValue() {
        return minValue + this.value * (maxValue - minValue);
    }

    public void setActualValue(double actualValue) {
        double clamped = Math.max(minValue, Math.min(maxValue, actualValue));
        this.value = Math.max(0.0, Math.min(1.0, (clamped - minValue) / (maxValue - minValue)));
        updateMessage();
        applyValue();
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.literal(prefix + formatDisplayNumber(getActualValue()) + suffix));
    }

    @Override
    protected void applyValue() {
        if (onApply != null) {
            onApply.accept(getActualValue());
        }
    }

    public String formatDisplayNumber(double val) {
        if (isPercentage) {
            return String.valueOf(Math.round(val * 100.0));
        }
        if (decimalPlaces == 0) {
            return String.format(Locale.ROOT, "%.0f", val);
        } else if (decimalPlaces == 1) {
            return String.format(Locale.ROOT, "%.1f", val);
        } else {
            return String.format(Locale.ROOT, "%.2f", val);
        }
    }

    public String formatRawNumber(double val) {
        if (isPercentage) {
            return String.valueOf(Math.round(val * 100.0));
        }
        if (decimalPlaces == 0) {
            return String.valueOf((long) Math.round(val));
        } else if (decimalPlaces == 1) {
            return String.format(Locale.ROOT, "%.1f", val);
        } else {
            return String.format(Locale.ROOT, "%.2f", val);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        if (editing) {
            // 绘制输入态底衬与金边提示
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xEE0F172A);
            graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), 0xFFEAB308);

            // 绘制前缀提示
            String hint = prefix.trim();
            graphics.drawString(font, hint, getX() + 6, getY() + (getHeight() - 8) / 2, 0xFFE2E8F0, true);

            // 渲染输入框本体
            editBox.render(graphics, mouseX, mouseY, partialTick);

            // 绘制后缀提示
            if (!suffix.isEmpty()) {
                graphics.drawString(font, suffix, editBox.getX() + editBox.getWidth() + 4, getY() + (getHeight() - 8) / 2, 0xFF94A3B8, true);
            }
            return;
        }

        // 非输入态：调用父类绘制滑块与轨道
        Component originalMsg = this.getMessage();
        this.setMessage(Component.empty());
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        this.setMessage(originalMsg);

        // 自定义分段排版：前缀 + [ 数字胶囊 ] + 后缀
        String numStr = formatDisplayNumber(getActualValue());
        int prefixW = font.width(prefix);
        int numW = font.width(numStr);
        int suffixW = font.width(suffix);

        int totalW = prefixW + numW + suffixW + 8;
        int startX = getX() + (getWidth() - totalW) / 2;
        int textY = getY() + (getHeight() - 8) / 2;

        // 1. 绘制前缀
        if (!prefix.isEmpty()) {
            graphics.drawString(font, prefix, startX, textY, 0xFFFFFFFF, true);
        }

        // 2. 测量并绘制数字胶囊区域（支持直接点击输入）
        numBoxX = startX + prefixW + 2;
        numBoxY = getY() + 2;
        numBoxW = numW + 6;
        numBoxH = getHeight() - 4;

        boolean isHoveringNum = mouseX >= numBoxX && mouseX <= numBoxX + numBoxW && mouseY >= numBoxY && mouseY <= numBoxY + numBoxH;

        if (isHoveringNum) {
            graphics.fill(numBoxX, numBoxY, numBoxX + numBoxW, numBoxY + numBoxH, 0xD01E293B);
            graphics.renderOutline(numBoxX, numBoxY, numBoxW, numBoxH, 0xFFFDE047);
            graphics.drawString(font, numStr, numBoxX + 3, textY, 0xFFFDE047, true);
        } else {
            graphics.fill(numBoxX, numBoxY, numBoxX + numBoxW, numBoxY + numBoxH, 0x800F172A);
            graphics.renderOutline(numBoxX, numBoxY, numBoxW, numBoxH, 0x50FFFFFF);
            graphics.drawString(font, numStr, numBoxX + 3, textY, 0xFFFFFFFF, true);
        }

        // 3. 绘制后缀
        if (!suffix.isEmpty()) {
            graphics.drawString(font, suffix, numBoxX + numBoxW + 2, textY, 0xFFCBD5E1, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) {
            return false;
        }

        if (editing) {
            if (editBox.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            commitEdit();
            return true;
        }

        // 点击数字胶囊区域或右键滑动条任意位置：进入直接键盘输入数值模式
        boolean clickedNum = (mouseX >= numBoxX && mouseX <= numBoxX + numBoxW && mouseY >= numBoxY && mouseY <= numBoxY + numBoxH);
        if (clickedNum || button == 1) {
            startEditing();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void startEditing() {
        this.editing = true;
        Font font = Minecraft.getInstance().font;
        int prefixW = font.width(prefix.trim());
        int boxW = 56;
        int boxX = getX() + prefixW + 12;
        if (boxX + boxW > getX() + getWidth() - 15) {
            boxX = getX() + getWidth() - boxW - 15;
        }
        editBox.setX(boxX);
        editBox.setY(getY() + 2);
        editBox.setWidth(boxW);
        editBox.setHeight(getHeight() - 4);
        editBox.setValue(formatRawNumber(getActualValue()));
        editBox.setFocused(true);
        editBox.moveCursorToEnd(false);
    }

    public void commitEdit() {
        if (!editing) return;
        try {
            String txt = editBox.getValue().trim();
            if (!txt.isEmpty()) {
                double typed = Double.parseDouble(txt);
                if (isPercentage) {
                    typed = typed / 100.0;
                }
                setActualValue(typed);
            }
        } catch (NumberFormatException ignored) {
        }
        this.editing = false;
        editBox.setFocused(false);
    }

    public void cancelEdit() {
        this.editing = false;
        editBox.setFocused(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                cancelEdit();
                return true;
            }
            return editBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editing) {
            return editBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    public boolean isEditing() {
        return editing;
    }
}
