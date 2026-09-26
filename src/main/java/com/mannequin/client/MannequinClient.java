package com.mannequin.client;

import com.mannequin.MannequinMod;
import com.mannequin.client.camera.DirectorCameraController;
import com.mannequin.client.gui.TimelineHudOverlay;
import com.mannequin.client.input.ModKeyMappings;
import com.mannequin.client.render.MannequinModel;
import com.mannequin.client.render.MannequinRenderer;
import com.mannequin.client.render.NoopRenderer;
import com.mannequin.client.timeline.GhostPathRenderer;
import com.mannequin.client.timeline.MasterClockEngine;
import com.mannequin.client.timeline.PuppeteerController;
import com.mannequin.registry.ModEntityTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 客户端专属模块初始化与事件总线注册器。
 *
 * <p>集中管理客户端生命周期内的实体渲染器、GUI 遮罩图层、导演快捷键及世界渲染钩子。
 */
public final class MannequinClient {

    private MannequinClient() {
    }

    /**
     * 初始化客户端全部业务系统。
     *
     * @param modEventBus NeoForge 模组生命周期事件总线 (MOD Bus)
     */
    public static void init(IEventBus modEventBus) {
        // 1. 注册模组级声明事件 (MOD Bus)
        modEventBus.addListener(MannequinClient::onRegisterLayerDefinitions);
        modEventBus.addListener(MannequinClient::onRegisterRenderers);
        modEventBus.addListener(MannequinClient::onRegisterKeyMappings);
        modEventBus.addListener(MannequinClient::onRegisterGuiLayers);

        // 2. 注册游戏运行时动态事件 (GAME Bus / NeoForge.EVENT_BUS)
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(DirectorCameraController.INSTANCE::onClientTick);
        gameBus.addListener(DirectorCameraController.INSTANCE::onRenderTick);
        gameBus.addListener(DirectorCameraController.INSTANCE::onComputeCameraAngles);
        gameBus.addListener(DirectorCameraController.INSTANCE::onComputeFov);
        gameBus.addListener(DirectorCameraController.INSTANCE::onKeyInput);
        gameBus.addListener(DirectorCameraController.INSTANCE::onCalculatePlayerTurn);
        gameBus.addListener(DirectorCameraController.INSTANCE::onMovementInputUpdate);
        gameBus.addListener(DirectorCameraController.INSTANCE::onRenderPlayer);
        gameBus.addListener(DirectorCameraController.INSTANCE::onMouseScroll);
        gameBus.addListener(com.mannequin.client.camera.MultiCameraBatchRunner.INSTANCE::onRenderFramePost);
        gameBus.addListener(GhostPathRenderer::onRenderLevelStage);
        gameBus.addListener(com.mannequin.client.camera.CameraStationRenderer::onRenderLevelStage);
        gameBus.addListener(com.mannequin.client.studio.PureStudioManager.INSTANCE::onFinalizeSpawn);
        gameBus.addListener(com.mannequin.client.studio.PureStudioManager.INSTANCE::onEntityJoinLevel);

        // 3. 进入世界时自动加载并恢复本存档对应的专属片场工程数据，并激活纯净片场防怪力场
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> {
            PuppeteerController.INSTANCE.releasePossession();
            DirectorCameraController.INSTANCE.onLogout();
            com.mannequin.client.camera.MultiCameraBatchRunner.INSTANCE.cancel();
            com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.loadStudioScene();
            TimelineHudOverlay.INSTANCE.loadPreferences();
            if (com.mannequin.client.studio.PureStudioManager.INSTANCE.isSlimeShieldEnabled()) {
                com.mannequin.client.studio.PureStudioManager.INSTANCE.purgeAllSlimes();
            }
        });

        // 4. 离开世界或断开连接时，先同步落盘持久化本存档片场工程，再释放资源
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(false);
            PuppeteerController.INSTANCE.releasePossession();
            DirectorCameraController.INSTANCE.onLogout();
            com.mannequin.client.camera.MultiCameraBatchRunner.INSTANCE.cancel();
            MasterClockEngine.INSTANCE.clearAllTracks();
            com.mannequin.client.camera.MultiCameraManager.INSTANCE.clearStations();
            DirectorCameraController.INSTANCE.clearDollyKeyframes();
        });
    }

    /**
     * 注册 7 头身人偶实体的几何模型骨骼定义。
     */
    private static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MannequinModel.LAYER_LOCATION, MannequinModel::createBodyLayer);
    }

    /**
     * 注册人偶及隐形相机锚点的渲染器。
     */
    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 纯色人偶实体渲染器
        event.registerEntityRenderer(ModEntityTypes.MANNEQUIN.get(), MannequinRenderer::new);
        // 相机锚点实体渲染器（隐形跳过渲染）
        event.registerEntityRenderer(ModEntityTypes.CAMERA_ANCHOR.get(), NoopRenderer::new);
    }

    /**
     * 注册漫剧导演快捷键。
     */
    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeyMappings.register(event);
    }

    /**
     * 注册导演画幅遮罩（Letterbox）与时间轴仪表盘 GUI 图层。
     */
    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        TimelineHudOverlay.INSTANCE.loadPreferences();
        event.registerAboveAll(
                MannequinMod.id("director_timeline_overlay"),
                TimelineHudOverlay.INSTANCE
        );
    }
}
