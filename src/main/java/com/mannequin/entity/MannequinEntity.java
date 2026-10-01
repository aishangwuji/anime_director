package com.mannequin.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

/**
 * A scripted, AI-free prop used as a color-coded stunt double for AIGC footage.
 *
 * <p>It carries only the data the director cares about — solid color and the
 * shaded/unlit rendering mode — and never moves or pathfinds on its own. That
 * keeps the entity cheap and makes its visuals fully predictable for
 * segmentation-driven video generation.
 */
public class MannequinEntity extends Entity {

    private static final EntityDataAccessor<Byte> DATA_COLOR =
            SynchedEntityData.defineId(MannequinEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_UNLIT =
            SynchedEntityData.defineId(MannequinEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_POSE =
            SynchedEntityData.defineId(MannequinEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_SCALE =
            SynchedEntityData.defineId(MannequinEntity.class, EntityDataSerializers.FLOAT);

    private static final String TAG_COLOR = "Color";
    private static final String TAG_UNLIT = "Unlit";
    private static final String TAG_POSE = "Pose";
    private static final String TAG_SCALE = "Scale";

    /**
     * 常用体型比例预设（支持从 25% 微型到 1000% 摩天巨物场景）。
     */
    public static final float[] SCALE_PRESETS = {0.25F, 0.5F, 0.75F, 1.0F, 1.5F, 2.0F, 3.0F, 5.0F, 10.0F};

    private float limbSwing;
    private float limbSwingAmount;
    private float prevLimbSwingAmount;

    public MannequinEntity(EntityType<? extends MannequinEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void tick() {
        super.tick();
        this.prevLimbSwingAmount = this.limbSwingAmount;
        double dx = getX() - xo;
        double dz = getZ() - zo;
        float dist = (float) Math.sqrt(dx * dx + dz * dz) * 4.0F;
        if (dist > 1.0F) {
            dist = 1.0F;
        }
        this.limbSwingAmount += (dist - this.limbSwingAmount) * 0.4F;
        this.limbSwing += this.limbSwingAmount;
    }

    /**
     * @param partialTick 渲染子帧时间差
     * @return 实体行进步态的累积相位角（子帧平滑插值）
     */
    public float getLimbSwing(float partialTick) {
        return this.limbSwing - this.limbSwingAmount * (1.0F - partialTick);
    }

    /**
     * @param partialTick 渲染子帧时间差
     * @return 实体当前行走摆幅强度（0.0 ~ 1.0）
     */
    public float getLimbSwingAmount(float partialTick) {
        return net.minecraft.util.Mth.lerp(partialTick, this.prevLimbSwingAmount, this.limbSwingAmount);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, (byte) MannequinColor.WHITE.ordinal());
        builder.define(DATA_UNLIT, false);
        builder.define(DATA_POSE, (byte) MannequinPose.STANDING.ordinal());
        builder.define(DATA_SCALE, 1.0F);
    }

    /**
     * @return 实体体型缩放系数（1.0F = 100% 标准人体尺寸）
     */
    public float getScale() {
        return entityData.get(DATA_SCALE);
    }

    public void setScale(float scale) {
        float clamped = Math.max(0.1F, Math.min(20.0F, scale));
        entityData.set(DATA_SCALE, clamped);
        double oldX = getX();
        double oldY = getY();
        double oldZ = getZ();
        this.refreshDimensions();
        this.setPos(oldX, oldY, oldZ);
    }

    /**
     * 顺序轮转切换体型比例预设（25% ~ 1000% 巨物）。
     */
    public float cycleScale() {
        float current = getScale();
        float next = SCALE_PRESETS[0];
        for (float p : SCALE_PRESETS) {
            if (p > current + 0.05F) {
                next = p;
                break;
            }
        }
        setScale(next);
        return next;
    }

    /**
     * @return 比例对应的中文场景描述
     */
    public static String getScaleDescription(float scale) {
        if (scale <= 0.26F) return "25% 手办/微缩模型";
        if (scale <= 0.51F) return "50% 矮人/宠物";
        if (scale <= 0.76F) return "75% 少年/矮小";
        if (scale <= 1.05F) return "100% 标准人体基准";
        if (scale <= 1.55F) return "150% 魁梧巨汉";
        if (scale <= 2.05F) return "200% 小型机甲/魔像";
        if (scale <= 3.05F) return "300% 泰坦/巨型领主";
        if (scale <= 5.05F) return "500% 上古巨兽/大机甲";
        return "1000% 摩天巨像/哥斯拉级巨物";
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        float width = 0.6F;
        float height = 1.8F;
        MannequinPose currentPose = getMannequinPose();
        if (currentPose == MannequinPose.LYING) {
            width = 1.8F;
            height = 0.6F;
        } else if (currentPose == MannequinPose.CROUCHING) {
            height = 1.1F;
        }
        return net.minecraft.world.entity.EntityDimensions.scalable(width, height)
                .withEyeHeight(height * 0.85F)
                .scale(getScale());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_SCALE.equals(key) || DATA_POSE.equals(key)) {
            refreshDimensions();
        }
    }

    /**
     * @return the mannequin's current solid color
     */
    public MannequinColor getColor() {
        return MannequinColor.byId(entityData.get(DATA_COLOR));
    }

    public void setColor(MannequinColor color) {
        entityData.set(DATA_COLOR, (byte) color.ordinal());
    }

    /**
     * @return {@code true} when the mannequin renders full-bright as a flat color
     */
    public boolean isUnlit() {
        return entityData.get(DATA_UNLIT);
    }

    public void setUnlit(boolean unlit) {
        entityData.set(DATA_UNLIT, unlit);
    }

    public MannequinPose getMannequinPose() {
        return MannequinPose.byId(entityData.get(DATA_POSE));
    }

    public void setMannequinPose(MannequinPose pose) {
        entityData.set(DATA_POSE, (byte) pose.ordinal());
        refreshDimensions();
    }

    /**
     * 导演交互：
     * <ul>
     *   <li>潜行右键：轮盘/顺序循环切换动作姿态预设；</li>
     *   <li>手持染料：给替身指定角色语义纯色；</li>
     *   <li>手持荧石粉/火把：一键开启或关闭 Unlit 纯平无阴影抠像模式。</li>
     * </ul>
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 0. 手持漫剧导演工杖 (MANNEQUIN_SPAWN)
        boolean isDirectorWand = stack.is(com.mannequin.registry.ModItems.MANNEQUIN_SPAWN.get());

        if (isDirectorWand) {
            com.mannequin.item.DirectorWandHelper.recycleMannequin(this, player);
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        // 1. 空手/其他物品 + 潜行右键：循环切换经典动作姿态预设
        if (player.isShiftKeyDown()) {
            if (!level().isClientSide()) {
                MannequinPose nextPose = MannequinPose.byId((getMannequinPose().ordinal() + 1) % MannequinPose.values().length);
                setMannequinPose(nextPose);
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[替身姿态] 已切换为: §e" + nextPose.getDisplayName()), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        // 2. 染色判定
        if (stack.getItem() instanceof DyeItem dyeItem) {
            MannequinColor color = MannequinColor.byDye(dyeItem.getDyeColor());
            if (!level().isClientSide() && getColor() != color) {
                setColor(color);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        // 3. 右键切换 Unlit 纯平无光影模式（手持火把或荧石粉）
        if (stack.is(net.minecraft.world.item.Items.GLOWSTONE_DUST) || stack.is(net.minecraft.world.item.Items.TORCH)) {
            if (!level().isClientSide()) {
                setUnlit(!isUnlit());
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[人偶无光影模式] " + (isUnlit() ? "§e已开启 (便于后期提取纯色蒙版)" : "§7已关闭 (恢复场景光影)")), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        return super.interact(player, hand);
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (isRemoved()) {
            return false;
        }
        // 片场防护：仅玩家攻击、虚空或爆炸可击碎/回收人偶，免疫骷髅箭矢等非玩家意外怪伤
        boolean isPlayerAttack = source.getEntity() instanceof Player;
        boolean isCreativeOrDestructive = source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);

        if (!isPlayerAttack && !isCreativeOrDestructive) {
            return false;
        }

        if (!level().isClientSide()) {
            if (source.getEntity() instanceof Player player) {
                com.mannequin.item.DirectorWandHelper.recycleMannequin(this, player);
            } else {
                discard();
                level().playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.ITEM_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 10, 0.2, 0.5, 0.2, 0.05);
                }
                spawnAtLocation(com.mannequin.registry.ModItems.MANNEQUIN_SPAWN.get());
            }
            return true;
        }
        return true;
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (level().isClientSide() && reason.shouldDestroy()) {
            ClientRemovalHelper.cleanupTrack(getUUID().toString());
        }
    }

    @Override
    public void onClientRemoval() {
        super.onClientRemoval();
        ClientRemovalHelper.cleanupTrack(getUUID().toString());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * 当被导演附身操纵时，声明本机客户端拥有实体物理权威，阻止原版 ClientPacketListener 的位移插值回弹拉扯。
     */
    @Override
    public boolean isControlledByLocalInstance() {
        if (level().isClientSide()) {
            return ClientPossessionHelper.isPossessed(this);
        }
        return super.isControlledByLocalInstance();
    }

    private static final class ClientPossessionHelper {
        private static boolean isPossessed(MannequinEntity entity) {
            return com.mannequin.client.timeline.PuppeteerController.INSTANCE.getPossessedEntity() == entity;
        }
    }

    private static final class ClientRemovalHelper {
        private static void cleanupTrack(String uuid) {
            com.mannequin.client.timeline.ClientTrackCleanupHelper.cleanupTrack(uuid);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains(TAG_COLOR)) {
            setColor(MannequinColor.byId(tag.getByte(TAG_COLOR)));
        }
        if (tag.contains(TAG_UNLIT)) {
            setUnlit(tag.getBoolean(TAG_UNLIT));
        }
        if (tag.contains(TAG_POSE)) {
            setMannequinPose(MannequinPose.byId(tag.getByte(TAG_POSE)));
        }
        if (tag.contains(TAG_SCALE)) {
            setScale(tag.getFloat(TAG_SCALE));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte(TAG_COLOR, (byte) getColor().ordinal());
        tag.putBoolean(TAG_UNLIT, isUnlit());
        tag.putByte(TAG_POSE, (byte) getMannequinPose().ordinal());
        tag.putFloat(TAG_SCALE, getScale());
    }
}
