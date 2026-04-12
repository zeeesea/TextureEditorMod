package com.zeeesea.textureeditor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record EntityTextureSyncPayload(
        Identifier textureId,
        Identifier spriteId,  // null if not a sprite (e.g. mob textures)
        int width,
        int height,
        int[] pixels,
        int[] originalPixels
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<EntityTextureSyncPayload> ID =
            CustomPacketPayload.createType("textureeditor:entity_texture_sync");

    public static final StreamCodec<FriendlyByteBuf, EntityTextureSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeIdentifier(value.textureId());
                        buf.writeBoolean(value.spriteId() != null);
                        if (value.spriteId() != null) buf.writeIdentifier(value.spriteId());
                        buf.writeInt(value.width());
                        buf.writeInt(value.height());
                        buf.writeVarIntArray(value.pixels());
                        buf.writeVarIntArray(value.originalPixels());
                    },
                    buf -> new EntityTextureSyncPayload(
                            buf.readIdentifier(),
                            buf.readBoolean() ? buf.readIdentifier() : null,
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