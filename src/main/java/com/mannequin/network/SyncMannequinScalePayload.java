package com.mannequin.network;

import com.mannequin.MannequinMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端向服务端同步人偶实体无级体型缩放（Shift+滚轮微调）的网络载荷。
 */
public record SyncMannequinScalePayload(
        int entityId,
        float scale
) implements CustomPacketPayload {

    public static final Type<SyncMannequinScalePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MannequinMod.MOD_ID, "sync_mannequin_scale"));

    public static final StreamCodec<ByteBuf, SyncMannequinScalePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.entityId);
                ByteBufCodecs.FLOAT.encode(buf, payload.scale);
            },
            buf -> new SyncMannequinScalePayload(
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.FLOAT.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
