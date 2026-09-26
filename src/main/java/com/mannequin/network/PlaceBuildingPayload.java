package com.mannequin.network;

import com.mannequin.MannequinMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端向服务端发起纯建筑一键放置部署的网络载荷。
 */
public record PlaceBuildingPayload(
        BlockPos targetOrigin,
        String fileName,
        int rotationDegrees
) implements CustomPacketPayload {

    public static final Type<PlaceBuildingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MannequinMod.MOD_ID, "place_building"));

    public static final StreamCodec<ByteBuf, PlaceBuildingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.targetOrigin);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.fileName);
                ByteBufCodecs.VAR_INT.encode(buf, payload.rotationDegrees);
            },
            buf -> new PlaceBuildingPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
