package com.zeeesea.textureeditor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextureEditor implements ModInitializer {
	public static final String MOD_ID = "textureeditor";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Live Texture Editor Server initializing...");

		PayloadTypeRegistry.serverboundPlay().register(TextureSyncPayload.ID, TextureSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(TextureSyncPayload.ID, TextureSyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EntityTextureSyncPayload.ID, EntityTextureSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(EntityTextureSyncPayload.ID, EntityTextureSyncPayload.CODEC);

		// Standard Texture Sync
		ServerPlayNetworking.registerGlobalReceiver(TextureSyncPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				context.server().getPlayerList().getPlayers().forEach(p -> {
					if (p != context.player()) {
						ServerPlayNetworking.send(p, payload);
					}
				});
			});
		});

		// Entity/Mob Texture Sync
		ServerPlayNetworking.registerGlobalReceiver(EntityTextureSyncPayload.ID, (payload, context) -> {
			context.server().execute(() -> {
				context.server().getPlayerList().getPlayers().forEach(p -> {
					if (p != context.player()) {
						ServerPlayNetworking.send(p, payload);
					}
				});
			});
		});

		LOGGER.info("Live Texture Editor Server initialized");
	}
}