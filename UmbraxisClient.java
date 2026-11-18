package com.umbraxis.client;

import com.umbraxis.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class UmbraxisClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(ModModelLayers.UMBRAXIS, UmbraxisGeometry::createLayer);
		EntityRenderers.register(ModEntities.UMBRAXIS, UmbraxisRenderer::new);
	}
}
