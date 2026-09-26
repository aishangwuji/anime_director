package com.mannequin.client.camera;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;

import java.util.List;
import java.util.OptionalDouble;

/**
 * 3D 世界空间机位标记与视锥体线框渲染器（Camera Station 3D Visualizer）。
 *
 * <p>在游戏世界中为每个已打下的分镜机位渲染：
 * <ul>
 *   <li><b>高亮摄像机机身</b>：深灰与青色机身模型，标定摄像机物理安放点；</li>
 *   <li><b>真实 FOV 视锥体金字塔线框</b>：按该机位的 Yaw/Pitch/Roll 与镜头 FOV 沿实际镜头视线正前方投射，取景视野一目了然；</li>
 *   <li><b>3D 自发光悬浮铭牌 (Billboard)</b>：机位名称与焦距度数，永远面向观察者视口，极易辨认。</li>
 * </ul>
 */
public final class CameraStationRenderer {

    private static final Logger LOGGER = LogManager.getLogger("CameraStationRenderer");
    private static boolean enabled = true;

    private static final RenderType STATION_LINE_TYPE = RenderType.create(
            "mannequin_station_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private CameraStationRenderer() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        CameraStationRenderer.enabled = enabled;
    }

    public static void toggle() {
        CameraStationRenderer.enabled = !CameraStationRenderer.enabled;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!enabled || Mp4VideoRecorder.INSTANCE.isRecording() || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        List<CameraStation> stations = MultiCameraManager.INSTANCE.getStations();
        if (stations.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        try {
            Camera camera = event.getCamera();
            Vec3 camPos = camera.getPosition();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

            double maxDistSq = 128.0 * 128.0;

            // ========================================================
            // PASS 1: 批量绘制摄像机机身与金色视锥体线框
            // 注意：必须在单独的 Batch 中绘制完毕，严禁与 Font.drawInBatch 穿插，
            // 否则会因 BufferSource 内部切换 RenderType 导致 "Not building!" 崩溃。
            // ========================================================
            VertexConsumer lines = bufferSource.getBuffer(STATION_LINE_TYPE);
            boolean renderedAnyLines = false;

            for (CameraStation station : stations) {
                Vec3 pos = station.position();
                double distSq = pos.distanceToSqr(camPos);
                if (distSq > maxDistSq) {
                    continue;
                }

                renderedAnyLines = true;
                poseStack.pushPose();
                poseStack.translate(pos.x - camPos.x, pos.y - camPos.y, pos.z - camPos.z);

                // 镜头三维朝向投影：
                // Minecraft 相机视线定义为 Yaw 0 时指向正南 (+Z)，向下旋转 Pitch。
                // 通过复合逆偏航角与俯仰角，将本地 +Z 轴准确映射至机位视锥投射方向。
                poseStack.mulPose(Axis.YP.rotationDegrees(-station.yaw()));
                poseStack.mulPose(Axis.XP.rotationDegrees(station.pitch()));
                poseStack.mulPose(Axis.ZP.rotationDegrees(station.roll()));

                Matrix4f pose = poseStack.last().pose();

                // (A) 机身小立方体 (青色 0x00E5FF，位于视锥原点后方 -Z)
                float rBody = 0.0F, gBody = 0.9F, bBody = 1.0F, aBody = 0.9F;
                drawBoxWireframe(lines, pose, -0.2F, -0.15F, -0.35F, 0.2F, 0.15F, 0.05F, rBody, gBody, bBody, aBody);

                // 顶部提手
                drawLine(lines, pose, -0.05F, 0.15F, -0.25F, -0.05F, 0.22F, -0.25F, rBody, gBody, bBody, aBody);
                drawLine(lines, pose, 0.05F, 0.15F, -0.25F, 0.05F, 0.22F, -0.25F, rBody, gBody, bBody, aBody);
                drawLine(lines, pose, -0.05F, 0.22F, -0.25F, 0.05F, 0.22F, -0.25F, rBody, gBody, bBody, aBody);

                // (B) 视锥体金字塔线框 (金黄色 0xFFD700，沿视线正前方 +Z 投射)
                float fovRad = (float) Math.toRadians(station.fov() / 2.0F);
                float dist = 2.5F; // 视锥向前方投射 2.5 格
                float halfH = (float) (dist * Math.tan(fovRad));
                float halfW = halfH * (16.0F / 9.0F); // 16:9 画幅视锥基准

                float rCone = 1.0F, gCone = 0.84F, bCone = 0.0F, aCone = 0.85F;
                // 四条金字塔边缘光芒（从镜头前端 0, 0, 0.05 向前射出）
                drawLine(lines, pose, 0, 0, 0.05F, -halfW, halfH, dist, rCone, gCone, bCone, aCone);
                drawLine(lines, pose, 0, 0, 0.05F, halfW, halfH, dist, rCone, gCone, bCone, aCone);
                drawLine(lines, pose, 0, 0, 0.05F, halfW, -halfH, dist, rCone, gCone, bCone, aCone);
                drawLine(lines, pose, 0, 0, 0.05F, -halfW, -halfH, dist, rCone, gCone, bCone, aCone);

                // 远端矩形取景框
                drawLine(lines, pose, -halfW, halfH, dist, halfW, halfH, dist, rCone, gCone, bCone, aCone);
                drawLine(lines, pose, halfW, halfH, dist, halfW, -halfH, dist, rCone, gCone, bCone, aCone);
                drawLine(lines, pose, halfW, -halfH, dist, -halfW, -halfH, dist, rCone, gCone, bCone, aCone);
                drawLine(lines, pose, -halfW, -halfH, dist, -halfW, halfH, dist, rCone, gCone, bCone, aCone);

                poseStack.popPose();
            }

            if (renderedAnyLines) {
                bufferSource.endBatch(STATION_LINE_TYPE);
            }

            // ========================================================
            // PASS 2: 批量绘制 3D 自发光悬浮铭牌 (Billboard)
            // ========================================================
            for (CameraStation station : stations) {
                Vec3 pos = station.position();
                double distSq = pos.distanceToSqr(camPos);
                if (distSq > maxDistSq) {
                    continue;
                }

                poseStack.pushPose();
                poseStack.translate(pos.x - camPos.x, pos.y - camPos.y + 0.65, pos.z - camPos.z);
                poseStack.mulPose(camera.rotation()); // 始终朝向观察者相机
                poseStack.scale(-0.022F, -0.022F, 0.022F);

                String label = "📷 " + station.name() + " [FOV " + (int) station.fov() + "°]";
                float textWidth = mc.font.width(label);
                Matrix4f textPose = poseStack.last().pose();

                mc.font.drawInBatch(
                        label,
                        -textWidth / 2.0F,
                        0.0F,
                        0xFF00E5FF,
                        false,
                        textPose,
                        bufferSource,
                        Font.DisplayMode.SEE_THROUGH,
                        0x80000000,
                        15728880
                );

                poseStack.popPose();
            }

            bufferSource.endBatch();

        } catch (Throwable t) {
            LOGGER.error("Error in CameraStationRenderer.onRenderLevelStage", t);
        }
    }

    private static void drawBoxWireframe(VertexConsumer builder, Matrix4f pose,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2,
                                         float r, float g, float b, float a) {
        // 底面
        drawLine(builder, pose, x1, y1, z1, x2, y1, z1, r, g, b, a);
        drawLine(builder, pose, x2, y1, z1, x2, y1, z2, r, g, b, a);
        drawLine(builder, pose, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawLine(builder, pose, x1, y1, z2, x1, y1, z1, r, g, b, a);
        // 顶面
        drawLine(builder, pose, x1, y2, z1, x2, y2, z1, r, g, b, a);
        drawLine(builder, pose, x2, y2, z1, x2, y2, z2, r, g, b, a);
        drawLine(builder, pose, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawLine(builder, pose, x1, y2, z2, x1, y2, z1, r, g, b, a);
        // 四条竖立立柱
        drawLine(builder, pose, x1, y1, z1, x1, y2, z1, r, g, b, a);
        drawLine(builder, pose, x2, y1, z1, x2, y2, z1, r, g, b, a);
        drawLine(builder, pose, x2, y1, z2, x2, y2, z2, r, g, b, a);
        drawLine(builder, pose, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void drawLine(VertexConsumer builder, Matrix4f pose,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float r, float g, float b, float a) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-4) {
            nx /= len;
            ny /= len;
            nz /= len;
        }

        builder.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(nx, ny, nz);
        builder.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(nx, ny, nz);
    }
}
