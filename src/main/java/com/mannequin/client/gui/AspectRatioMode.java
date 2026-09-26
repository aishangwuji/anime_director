package com.mannequin.client.gui;

/**
 * 漫剧构图画幅遮罩模式。
 *
 * <p>在漫剧工业化制作中，不同分发平台（如抖音/小红书/TikTok 竖屏、B站/YouTube 宽屏、院线宽银幕）
 * 对画幅比例有着极其严格的要求。本枚举定义主流画幅比例，并提供屏幕遮罩（Letterbox/Pillarbox）
 * 的实时裁剪矩形计算。
 */
public enum AspectRatioMode {

    /**
     * 关闭画幅遮罩，全屏显示。
     */
    OFF("全屏 (无遮罩)", 0.0),

    /**
     * 9:16 竖屏漫剧画幅（抖音、快手、小红书、TikTok、短视频漫剧首选）。
     */
    RATIO_9_16("9:16 竖屏漫剧", 9.0 / 16.0),

    /**
     * 16:9 标准宽屏画幅（电视、常规番剧动画、横屏视频标准）。
     */
    RATIO_16_9("16:9 标准宽屏", 16.0 / 9.0),

    /**
     * 21:9 电影宽银幕画幅（电影感、强张力对峙镜头）。
     */
    RATIO_21_9("21:9 电影宽银幕", 21.0 / 9.0);

    private final String displayName;
    private final double targetRatio;

    AspectRatioMode(String displayName, double targetRatio) {
        this.displayName = displayName;
        this.targetRatio = targetRatio;
    }

    /**
     * @return 画幅中文显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return 目标画幅宽高比（宽 / 高）
     */
    public double getTargetRatio() {
        return targetRatio;
    }

    /**
     * 切换到下一个画幅模式。
     *
     * @return 下一个画幅模式
     */
    public AspectRatioMode next() {
        AspectRatioMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * 计算当前窗口尺寸下的有效拍摄安全区与黑边裁剪范围。
     *
     * @param screenWidth  屏幕宽度（像素）
     * @param screenHeight 屏幕高度（像素）
     * @return 拍摄安全区视口矩形（包含左右/上下黑边大小）
     */
    public ViewportRect calculateViewport(int screenWidth, int screenHeight) {
        if (this == OFF || screenWidth <= 0 || screenHeight <= 0) {
            return new ViewportRect(0, 0, screenWidth, screenHeight, 0, 0);
        }

        double currentRatio = (double) screenWidth / (double) screenHeight;

        int activeWidth;
        int activeHeight;
        int offsetX;
        int offsetY;

        if (currentRatio > targetRatio) {
            // 屏幕过宽：左右添加柱状黑边（Pillarbox），例如在 16:9 显示器上看 9:16 画面
            activeHeight = screenHeight;
            activeWidth = (int) Math.round(activeHeight * targetRatio);
            offsetX = (screenWidth - activeWidth) / 2;
            offsetY = 0;
        } else {
            // 屏幕过高：上下添加横向黑边（Letterbox），例如在 16:9 显示器上看 21:9 画面
            activeWidth = screenWidth;
            activeHeight = (int) Math.round(activeWidth / targetRatio);
            offsetX = 0;
            offsetY = (screenHeight - activeHeight) / 2;
        }

        return new ViewportRect(offsetX, offsetY, activeWidth, activeHeight, screenWidth, screenHeight);
    }

    /**
     * 安全区矩形数据记录。
     *
     * @param x            安全区左上角 X 坐标
     * @param y            安全区左上角 Y 坐标
     * @param width        安全区有效宽度
     * @param height       安全区有效高度
     * @param screenWidth  屏幕完整宽度
     * @param screenHeight 屏幕完整高度
     */
    public record ViewportRect(
            int x,
            int y,
            int width,
            int height,
            int screenWidth,
            int screenHeight
    ) {
    }
}
