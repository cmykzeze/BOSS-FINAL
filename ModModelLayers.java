package com.umbraxis.client;

import com.umbraxis.Umbraxis;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

public final class ModModelLayers {
	private ModModelLayers() {}
	public static final ModelLayerLocation UMBRAXIS =
			new ModelLayerLocation(Identifier.fromNamespaceAndPath(Umbraxis.MOD_ID, "umbraxis"), "main");
}
