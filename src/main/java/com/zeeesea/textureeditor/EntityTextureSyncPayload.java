package com.zeeesea.textureeditor;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record EntityTextureSyncPayload(
        Identifier textureId,
        Identifier spriteId,  // null if not a sprite (e.g. mob textures)
        int width,
        int height,
        int[] pixels,
        int[] originalPixels
) implements CustomPayload {

    public static final CustomPayload.Id<EntityTextureSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("textureeditor", "entity_texture_sync"));

    public static final PacketCodec<PacketByteBuf, EntityTextureSyncPayload> CODEC =
            PacketCodec.of(
                    (value, buf) -> {
                        buf.writeIdentifier(value.textureId());
                        buf.writeBoolean(value.spriteId() != null);
                        if (value.spriteId() != null) buf.writeIdentifier(value.spriteId());
                        buf.writeInt(value.width());
                        buf.writeInt(value.height());
                        buf.writeIntArray(value.pixels());
                        buf.writeIntArray(value.originalPixels());
                    },
                    buf -> new EntityTextureSyncPayload(
                            buf.readIdentifier(),
                            buf.readBoolean() ? buf.readIdentifier() : null,
                            buf.readInt(),
                            buf.readInt(),
                            buf.readIntArray(),
                            buf.readIntArray()
                    )
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}