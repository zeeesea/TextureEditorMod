package com.zeeesea.textureeditor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record TextureSyncPayload(
        Identifier spriteId,
        int width,
        int height,
        int[] pixels,
        int[] originalPixels
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TextureSyncPayload> ID =
            CustomPacketPayload.createType("textureeditor:texture_sync");

    public static final StreamCodec<FriendlyByteBuf, TextureSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeIdentifier(value.spriteId());
                        buf.writeInt(value.width());
                        buf.writeInt(value.height());
                        buf.writeVarIntArray(value.pixels());
                        buf.writeVarIntArray(value.originalPixels());
                    },
                    buf -> new TextureSyncPayload(
                            buf.readIdentifier(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readVarIntArray(),
                            buf.readVarIntArray()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}