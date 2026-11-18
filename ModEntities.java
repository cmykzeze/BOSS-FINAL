package com.umbraxis.registry;

import com.umbraxis.Umbraxis;
import com.umbraxis.entity.UmbraxisBoss;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private ModEntities() {}

	public static final ResourceKey<EntityType<?>> UMBRAXIS_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(Umbraxis.MOD_ID, "umbraxis"));

	public static final EntityType<UmbraxisBoss> UMBRAXIS = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			UMBRAXIS_KEY,
			EntityType.Builder.of(UmbraxisBoss::new, MobCategory.MONSTER)
					.sized(4.0F, 6.0F)
					.eyeHeight(5.0F)
					.clientTrackingRange(10)
					.fireImmune()
					.build(UMBRAXIS_KEY));

	public static void init() {
		FabricDefaultAttributeRegistry.register(UMBRAXIS, UmbraxisBoss.createAttributes());
	}
}
