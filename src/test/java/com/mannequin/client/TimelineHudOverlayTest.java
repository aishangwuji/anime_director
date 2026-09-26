package com.mannequin.client;

import com.mannequin.client.gui.TimelineHudOverlay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 屏幕操作指引 (F7) 首选项持久化与加载单元测试。
 */
class TimelineHudOverlayTest {

    @Test
    @DisplayName("测试 F7 隐藏屏幕提示后首选项正常存盘与重载记忆")
    void testTipsPersistence() {
        TimelineHudOverlay hud = TimelineHudOverlay.INSTANCE;

        // 模拟用户按下 F7 隐藏屏幕操作指引
        hud.setTipsVisible(false);
        assertFalse(hud.areTipsVisible(), "设置不可见后 areTipsVisible 必须为 false");
        assertFalse(hud.isShowGuideCard());
        assertFalse(hud.isShowSituationalTip());

        // 重新调用 loadPreferences() 模拟游戏重启/重新加载
        hud.loadPreferences();
        assertFalse(hud.areTipsVisible(), "从本地磁盘重载后必须保持用户选择的隐藏状态 (false)，不可重置弹窗");
        assertFalse(hud.isShowGuideCard());
        assertFalse(hud.isShowSituationalTip());

        // 模拟用户再次开启提示
        hud.setTipsVisible(true);
        assertTrue(hud.areTipsVisible());
        hud.loadPreferences();
        assertTrue(hud.areTipsVisible());
    }
}
