package com.mannequin.client.studio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 纯净片场环境管理器（Pure Studio Manager）。
 *
 * <p>专为漫剧超平坦与搭建片场设计，彻底解决原生 Minecraft 超平坦世界史莱姆（Slime）无休止刷怪、
 * 挤占镜头、撞翻人偶演员、发出黏液噪音等痛点问题：
 * <ul>
 *   <li><b>实时生成拦截（Slime Spawn Blocker）</b>：通过 {@link MobSpawnEvent.FinalizeSpawn} 事件在生成根源直接取消史莱姆/岩浆怪生成；</li>
 *   <li><b>一键清场驱逐（Slime Purge）</b>：瞬时从客户端与服务端双端抹除已生成的所有存活史莱姆；</li>
 *   <li><b>防撞击保护</b>：保障拍摄环境纯净无干扰。</li>
 * </ul>
 */
public final class PureStudioManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PureStudioManager.class);
    public static final PureStudioManager INSTANCE = new PureStudioManager();

    private boolean slimeShieldEnabled = true;

    private PureStudioManager() {
    }

    public boolean isSlimeShieldEnabled() {
        return slimeShieldEnabled;
    }

    public void setSlimeShieldEnabled(boolean enabled) {
        this.slimeShieldEnabled = enabled;
    }

    /**
     * 切换纯净片场史莱姆力场开关。开启时自动执行一次全场清屏。
     */
    public void toggleSlimeShield() {
        this.slimeShieldEnabled = !this.slimeShieldEnabled;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            if (slimeShieldEnabled) {
                int purged = purgeAllSlimes();
                mc.player.displayClientMessage(Component.literal("§a[纯净片场] 史莱姆力场：已开启（自动清理并阻止史莱姆生成，本次已驱逐 " + purged + " 只）"), false);
            } else {
                mc.player.displayClientMessage(Component.literal("§e[纯净片场] 史莱姆力场：已关闭（允许原版生物自然生成）"), false);
            }
        }
    }

    /**
     * NeoForge 生物生成事件监听（根源拦截史莱姆）。
     */
    public void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!slimeShieldEnabled) {
            return;
        }

        Entity entity = event.getEntity();
        if (isSlimeEntity(entity)) {
            event.setSpawnCancelled(true);
        }
    }

    /**
     * NeoForge 实体加入世界事件拦截（双重保险，彻底阻止任何史莱姆实体进入世界渲染与物理更新）。
     */
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!slimeShieldEnabled) {
            return;
        }

        if (isSlimeEntity(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * 瞬时清理片场内所有现存史莱姆实体。
     *
     * @return 本次清理的史莱姆总数
     */
    public int purgeAllSlimes() {
        int count = 0;
        Minecraft mc = Minecraft.getInstance();

        // 1. 服务端清理（单人游戏集成服务端）
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity e : level.getAllEntities()) {
                    if (isSlimeEntity(e)) {
                        e.discard();
                        count++;
                    }
                }
            }
        }

        // 2. 客户端实体缓存清理
        ClientLevel clientLevel = mc.level;
        if (clientLevel != null) {
            for (Entity e : clientLevel.entitiesForRendering()) {
                if (isSlimeEntity(e)) {
                    e.discard();
                }
            }
        }

        LOGGER.info("[Pure Studio] 已清空片场中的史莱姆实体，数量: {}", count);
        return count;
    }

    /**
     * 判断目标实体是否属于史莱姆系列生物（包括普通史莱姆与岩浆怪）。
     */
    public boolean isSlimeEntity(Entity entity) {
        return entity instanceof Slime;
    }
}
