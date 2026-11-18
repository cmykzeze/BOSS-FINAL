package com.umbraxis.registry;

import com.umbraxis.Umbraxis;
import com.umbraxis.item.HeartItem;
import com.umbraxis.item.SigilItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

public final class ModItems {
	private ModItems() {}

	/** The summoning item (crafted from the 9-ingredient recipe). */
	public static final Item SIGIL = register("sigil_of_umbraxis", SigilItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	/** Boss-exclusive netherite-tier sword (damage 12 base, loot table adds custom enchantments). */
	public static final Item FANG = register("fang_of_umbraxis", Item::new,
			new Item.Properties().rarity(Rarity.EPIC).sword(ToolMaterial.NETHERITE, 7.0F, -2.3F)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	/** Consumable relic: +20 max HP (10 min), absorption, regeneration, resistance. */
	public static final Item HEART = register("heart_of_umbraxis", HeartItem::new,
			new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	public static final Item SCALE = register("umbral_scale", Item::new, new Item.Properties().rarity(Rarity.RARE));

	private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Umbraxis.MOD_ID, name));
		Item item = factory.apply(props.setId(key));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void init() {}
}
