package com.mannequin.network;

import com.mannequin.MannequinMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端向服务端同步人偶受控位移与朝向的网络载荷。
 *
 * <p>用于在多人生存或局域网联机虚拟制片时，将客户端操纵的受控实体位置即时广播同步给服务端，
 * 解决服务端物理校验导致的“瞬移回弹（Rubberbanding）”硬伤。
 */
public record SyncMannequinPosPayload(
        int entityId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        double vx,
        double vy,
        double vz
) implements CustomPacketPayload {

    public static final Type<SyncMannequinPosPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MannequinMod.MOD_ID, "sync_mannequin_pos"));

    public static final StreamCodec<ByteBuf, SyncMannequinPosPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
                ByteBufCodecs.DOUBLE.encode(buf, payload.x);
                ByteBufCodecs.DOUBLE.encode(buf, payload.y);
                ByteBufCodecs.DOUBLE.encode(buf, payload.z);
                ByteBufCodecs.FLOAT.encode(buf, payload.yaw);
                ByteBufCodecs.FLOAT.encode(buf, payload.pitch);
                ByteBufCodecs.DOUBLE.encode(buf, payload.vx);
                ByteBufCodecs.DOUBLE.encode(buf, payload.vy);
                ByteBufCodecs.DOUBLE.encode(buf, payload.vz);
            },
            buf -> new SyncMannequinPosPayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
