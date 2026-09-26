package com.mannequin.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 漫剧导演系统快捷键注册与管理。
 *
 * <p>所有快捷键均分类归纳在 "key.categories.mannequin" 类别下，创作者可在原版控制设置中自由改键。
 */
public final class ModKeyMappings {

    public static final String CATEGORY_MANNEQUIN = "key.categories.mannequin";

    /** 切换导演相机自由飞控模式 */
    public static final KeyMapping TOGGLE_CAMERA = new KeyMapping(
            "key.mannequin.toggle_camera",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            CATEGORY_MANNEQUIN
    );

    /** 在当前位置打下一个机位关键帧 / 开启附身动捕录制 */
    public static final KeyMapping ADD_KEYFRAME = new KeyMapping(
            "key.mannequin.add_keyframe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY_MANNEQUIN
    );

    /** 启动/暂停机械滑轨与全场排演播放（使用 P 键，避免与原版 Enter 聊天键冲突） */
    public static final KeyMapping START_DOLLY = new KeyMapping(
            "key.mannequin.start_dolly",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY_MANNEQUIN
    );

    /** 机械滑轨与全场演员一键倒带复位回第 0 秒 */
    public static final KeyMapping RESET_DOLLY = new KeyMapping(
            "key.mannequin.reset_dolly",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY_MANNEQUIN
    );

    /** 清空当前所有机位关键帧 */
    public static final KeyMapping CLEAR_TRACK = new KeyMapping(
            "key.mannequin.clear_track",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DELETE,
            CATEGORY_MANNEQUIN
    );

    /** 循环切换 9:16 / 16:9 / 21:9 构图画幅遮罩 */
    public static final KeyMapping TOGGLE_ASPECT_RATIO = new KeyMapping(
            "key.mannequin.toggle_aspect_ratio",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY_MANNEQUIN
    );

    /** 附身受控人偶或载具 / 退出附身归还视角 */
    public static final KeyMapping TOGGLE_POSSESSION = new KeyMapping(
            "key.mannequin.toggle_possession",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY_MANNEQUIN
    );

    /** 镜头向左倾斜（Dutch Angle 左滚转） */
    public static final KeyMapping ROLL_LEFT = new KeyMapping(
            "key.mannequin.roll_left",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY_MANNEQUIN
    );

    /** 镜头向右倾斜（Dutch Angle 右滚转） */
    public static final KeyMapping ROLL_RIGHT = new KeyMapping(
            "key.mannequin.roll_right",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY_MANNEQUIN
    );

    /** 镜头倾斜角一键回正复位 */
    public static final KeyMapping RESET_ROLL = new KeyMapping(
            "key.mannequin.reset_roll",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY_MANNEQUIN
    );

    /** 镜头焦距拉长 / 特写变焦（FOV 减小） */
    public static final KeyMapping ZOOM_IN = new KeyMapping(
            "key.mannequin.zoom_in",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            CATEGORY_MANNEQUIN
    );

    /** 镜头焦距缩短 / 广角变焦（FOV 增大） */
    public static final KeyMapping ZOOM_OUT = new KeyMapping(
            "key.mannequin.zoom_out",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            CATEGORY_MANNEQUIN
    );

    /** 延长场景时长 */
    public static final KeyMapping INCREASE_DURATION = new KeyMapping(
            "key.mannequin.increase_duration",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_BRACKET,
            CATEGORY_MANNEQUIN
    );

    /** 缩短场景时长 */
    public static final KeyMapping DECREASE_DURATION = new KeyMapping(
            "key.mannequin.decrease_duration",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_BRACKET,
            CATEGORY_MANNEQUIN
    );

    /** 展开/折叠漫剧导演新手引导手册 */
    public static final KeyMapping TOGGLE_GUIDE = new KeyMapping(
            "key.mannequin.toggle_guide",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY_MANNEQUIN
    );

    /** 开启/关闭屏幕操作指引与浮窗提示 (HUD Tips) */
    public static final KeyMapping TOGGLE_HUD_TIPS = new KeyMapping(
            "key.mannequin.toggle_hud_tips",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            CATEGORY_MANNEQUIN
    );

    /** 显隐导演玩家本体模型（上帝视角下默认隐藏） */
    public static final KeyMapping TOGGLE_PLAYER_VISIBILITY = new KeyMapping(
            "key.mannequin.toggle_player_visibility",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY_MANNEQUIN
    );

    /** 呼出导演全功能快捷操作中心 / 动作轮盘菜单（默认 C 键，可在原版按键设置中自定义改键） */
    public static final KeyMapping QUICK_MENU = new KeyMapping(
            "key.mannequin.quick_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY_MANNEQUIN
    );

    /** 在当前上帝视角/穿越机视角打下一个分镜拍摄机位（默认 B 键） */
    public static final KeyMapping ADD_CAMERA_STATION = new KeyMapping(
            "key.mannequin.add_camera_station",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY_MANNEQUIN
    );

    /** 切换导演运镜风格：防抖平稳视角 vs 穿越机航模视角（默认 F9 键） */
    public static final KeyMapping TOGGLE_CAMERA_STYLE = new KeyMapping(
            "key.mannequin.toggle_camera_style",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            CATEGORY_MANNEQUIN
    );

    /** 开启/停止主视角实时运镜录制并直出 MP4 (默认 F10 键) */
    public static final KeyMapping RECORD_LIVE_POV = new KeyMapping(
            "key.mannequin.record_live_pov",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            CATEGORY_MANNEQUIN
    );

    /** 循环切换自由相机多档位航速预设 (默认 J 键) */
    public static final KeyMapping CYCLE_SPEED_GEAR = new KeyMapping(
            "key.mannequin.cycle_speed_gear",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY_MANNEQUIN
    );

    private ModKeyMappings() {
    }

    /**
     * 注册全部导演快捷键至 NeoForge 事件总线。
     *
     * @param event NeoForge 客户端按键注册事件
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_CAMERA);
        event.register(ADD_KEYFRAME);
        event.register(START_DOLLY);
        event.register(RESET_DOLLY);
        event.register(CLEAR_TRACK);
        event.register(TOGGLE_ASPECT_RATIO);
        event.register(TOGGLE_POSSESSION);
        event.register(ROLL_LEFT);
        event.register(ROLL_RIGHT);
        event.register(RESET_ROLL);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(INCREASE_DURATION);
        event.register(DECREASE_DURATION);
        event.register(TOGGLE_GUIDE);
        event.register(TOGGLE_HUD_TIPS);
        event.register(TOGGLE_PLAYER_VISIBILITY);
        event.register(TOGGLE_CAMERA_STYLE);
        event.register(RECORD_LIVE_POV);
        event.register(CYCLE_SPEED_GEAR);
        event.register(QUICK_MENU);
        event.register(ADD_CAMERA_STATION);
    }
}
