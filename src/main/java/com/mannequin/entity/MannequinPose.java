package com.mannequin.entity;

/**
 * 漫剧人偶高频经典姿态预设（Mannequin Pose Presets）。
 *
 * <p>预置漫剧与动漫中高频使用的身体骨骼姿势，方便导演快速摆 Pose 并喂给下游视频大模型（Kling、Wan2.1、ComfyUI），
 * 极大增强替身剪影的语义辨识度。
 */
public enum MannequinPose {
    /** 自然站桩待机姿势 */
    REST("站立待机"),
    /** 拔刀冲刺/前倾奔跑姿势 */
    RUNNING("奔跑冲刺"),
    /** 双手抱胸/格斗迎敌姿态 */
    FIGHTING("双手抱胸/迎敌"),
    /** 低头思索/下蹲潜伏 */
    CROUCHING("下蹲潜伏"),
    /** 抬臂瞄准/向前施法指引 */
    AIMING("抬臂瞄准"),
    /** 受击后仰/倒地负伤姿势 */
    FALLEN("受击倒地"),
    /** 安详平躺在地上 */
    LYING("平躺在地"),
    /** 侧身捂着肚子单臂支撑在地上 */
    HOLDING_BELLY_GROUND("捂腹侧撑在地"),
    /** 双手抱头防卫/受降姿势 */
    HANDS_ON_HEAD("双手抱头");

    private final String displayName;

    MannequinPose(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MannequinPose byId(int id) {
        MannequinPose[] values = values();
        if (id < 0 || id >= values.length) {
            return REST;
        }
        return values[id];
    }
}
