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
		LOGGER.info("Hello Fabric world!");

		// Register payload types for texture sync
		PayloadTypeRegistry.playC2S().register(TextureSyncPayload.ID, TextureSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(TextureSyncPayload.ID, TextureSyncPayload.CODEC);

		// Server receives texture change from Client A and forwards to all others
		ServerPlayNetworking.registerGlobalReceiver(TextureSyncPayload.ID, (payload, context) -> {
			context.server().getPlayerManager().getPlayerList().forEach(p -> {
				if (p != context.player()) {
					ServerPlayNetworking.send(p, payload);
				}
			});
		});
	}
}