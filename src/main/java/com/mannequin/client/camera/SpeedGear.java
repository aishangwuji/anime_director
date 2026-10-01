package com.mannequin.client.camera;

/**
 * 自由相机多档位航速预设（Speed Gears）。
 *
 * <p>为导演提供可预期的电影级速度档位：
 * <ul>
 *   <li><b>CRAWL (0.5 m/s)</b>：超微移档位，轻按按键仅微动几厘米，配合 Alt 键更可达到毫米级构图微调；</li>
 *   <li><b>SLOW (2.0 m/s)</b>：慢推拉档位，电影级慢速推轨、子弹时间配合运镜；</li>
 *   <li><b>NORMAL (6.0 m/s)</b>：标准拍摄巡航速度；</li>
 *   <li><b>FAST (16.0 m/s)</b>：高速巡视与大场景转场。</li>
 * </ul>
 */
public enum SpeedGear {
    CRAWL(0.5, "0.5m/s (超微移)", "0.5m/s (Crawl)"),
    SLOW(2.0, "2.0m/s (慢推拉)", "2.0m/s (Slow)"),
    NORMAL(6.0, "6.0m/s (标准)", "6.0m/s (Normal)"),
    FAST(16.0, "16.0m/s (高速)", "16.0m/s (Fast)"),
    HYPERSONIC(120.0, "120.0m/s (极速巡航)", "120.0m/s (Hypersonic)");

    private final double speed;
    private final String displayNameZh;
    private final String displayNameEn;

    SpeedGear(double speed, String displayNameZh, String displayNameEn) {
        this.speed = speed;
        this.displayNameZh = displayNameZh;
        this.displayNameEn = displayNameEn;
    }

    public double getSpeed() {
        return speed;
    }

    public String getDisplayName() {
        return displayNameZh;
    }

    public String getDisplayNameEn() {
        return displayNameEn;
    }

    public SpeedGear next() {
        SpeedGear[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
