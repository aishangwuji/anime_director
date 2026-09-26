package com.mannequin.client.timeline;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.OptionalDouble;

/**
 * 幽灵运动轨迹光带渲染器（Ghost Path Visualizer）。
 *
 * <p>在片场排练和穿越机飞控运镜时，将各轨道已录制的人偶和载具行进路线以<b>半透明 3D 发光轨迹线</b>
 * 的形式呈现在游戏世界中。并在每隔 1 秒（20 Ticks）处标出时间节点，使导演在运镜时对演员的出现时机与走位
 * 一目了然，从容完成贴地穿梭、跟拍或迎面抓拍。
 */
public final class GhostPathRenderer {

    private static boolean enabled = true;

    /**
     * 自定义带半透明混合与指定线宽的幽灵光带渲染管线。
     */
    private static final RenderType GHOST_LINE_TYPE = RenderType.create(
            "mannequin_ghost_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(2.5)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private GhostPathRenderer() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        GhostPathRenderer.enabled = enabled;
    }

    public static void toggle() {
        GhostPathRenderer.enabled = !GhostPathRenderer.enabled;
    }

    /**
     * 场景渲染钩子：在半透明方块渲染完成后绘制 3D 轨迹光带。
     *
     * @param event NeoForge 世界关卡渲染阶段事件
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!enabled || com.mannequin.client.camera.Mp4VideoRecorder.INSTANCE.isRecording() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        Camera camera = event.getCamera();
        Map<String, TimelineTrack> tracks = MasterClockEngine.INSTANCE.getTracks();
        if (tracks.isEmpty()) {
            return;
        }

        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer builder = bufferSource.getBuffer(GHOST_LINE_TYPE);

        poseStack.pushPose();
        // 转换至相对相机的局部视口坐标系
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f pose = poseStack.last().pose();

        int colorIndex = 0;
        boolean renderedAny = false;

        double maxDistSq = 96.0 * 96.0; // 96 格远距离视距剔除，避免场景轨道众多时过度绘制

        for (TimelineTrack track : tracks.values()) {
            if (track.getTrackId().equals(MasterClockEngine.CAMERA_TRACK_ID) || track.getFrameCount() < 2) {
                continue;
            }

            // 视距可见性剔除：若首末帧均远离相机视野则跳过，保护帧率
            MotionFrame initial = track.getInitialFrame();
            if (initial != null && camPos.distanceToSqr(initial.position()) > maxDistSq) {
                MotionFrame last = track.getFrames().lastEntry().getValue();
                if (last != null && camPos.distanceToSqr(last.position()) > maxDistSq) {
                    continue;
                }
            }

            renderedAny = true;

            // 根据轨道分配高饱和度区分颜色（纯红、纯青、纯金、纯紫等）
            float r = 1.0F;
            float g = 0.2F;
            float b = 0.2F;
            if (colorIndex % 3 == 1) {
                r = 0.2F; g = 0.8F; b = 1.0F; // 青色
            } else if (colorIndex % 3 == 2) {
                r = 1.0F; g = 0.85F; b = 0.1F; // 金黄色
            }

            MotionFrame prev = null;
            for (MotionFrame curr : track.getFrames().values()) {
                if (prev != null) {
                    Vec3 p0 = prev.position().add(0, 0.05, 0); // 微调离地高度防 Z-Fighting
                    Vec3 p1 = curr.position().add(0, 0.05, 0);

                    // 绘制半透明线段
                    builder.addVertex(pose, (float) p0.x, (float) p0.y, (float) p0.z)
                            .setColor(r, g, b, 0.85F)
                            .setNormal(0.0F, 1.0F, 0.0F);
                    builder.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z)
                            .setColor(r, g, b, 0.85F)
                            .setNormal(0.0F, 1.0F, 0.0F);

                    // 在跨过 1 秒（20 Ticks）整数倍时绘制微缩垂直刻度标记
                    if (prev.tick() / 20 != curr.tick() / 20 || curr.tick() % 20 == 0) {
                        builder.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z)
                                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                                .setNormal(0.0F, 1.0F, 0.0F);
                        builder.addVertex(pose, (float) p1.x, (float) p1.y + 0.4F, (float) p1.z)
                                .setColor(1.0F, 1.0F, 1.0F, 1.0F)
                                .setNormal(0.0F, 1.0F, 0.0F);
                    }
                }
                prev = curr;
            }
            colorIndex++;
        }

        poseStack.popPose();
        // 仅在确实提交了有效轨迹线段时才调用 endBatch，避免打断外部合批
        if (renderedAny) {
            bufferSource.endBatch(GHOST_LINE_TYPE);
        }
    }
}
