package com.mannequin.network;

import com.mannequin.MannequinMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端向服务端发起纯建筑打包导出的网络载荷。
 */
public record ExportBuildingPayload(
        BlockPos posA,
        BlockPos posB,
        String fileName,
        String name,
        String author,
        String description
) implements CustomPacketPayload {

    public static final Type<ExportBuildingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MannequinMod.MOD_ID, "export_building"));

    public static final StreamCodec<ByteBuf, ExportBuildingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                BlockPos.STREAM_CODEC.encode(buf, payload.posA);
                BlockPos.STREAM_CODEC.encode(buf, payload.posB);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.fileName);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.name);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.author);
                ByteBufCodecs.STRING_UTF8.encode(buf, payload.description);
            },
            buf -> new ExportBuildingPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
