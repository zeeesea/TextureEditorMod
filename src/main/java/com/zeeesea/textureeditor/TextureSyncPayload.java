package com.zeeesea.textureeditor;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record TextureSyncPayload(
        Identifier spriteId,
        int width,
        int height,
        int[] pixels
) implements CustomPayload {

    public static final CustomPayload.Id<TextureSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("textureeditor", "texture_sync"));

    public static final PacketCodec<PacketByteBuf, TextureSyncPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeIdentifier(value.spriteId());
                        buf.writeInt(value.width());
                        buf.writeInt(value.height());
                        buf.writeIntArray(value.pixels());
                    },
                    buf -> new TextureSyncPayload(
                            buf.readIdentifier(),
                            buf.readInt(),
                            buf.readInt(),
                            buf.readIntArray()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}