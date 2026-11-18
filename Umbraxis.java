package com.umbraxis;

import com.umbraxis.registry.ModEntities;
import com.umbraxis.registry.ModItems;
import com.umbraxis.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Umbraxis implements ModInitializer {
	public static final String MOD_ID = "umbraxis";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModSounds.init();
		ModItems.init();
		ModEntities.init();

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
			entries.accept(ModItems.SIGIL);
			entries.accept(ModItems.FANG);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
			entries.accept(ModItems.HEART);
			entries.accept(ModItems.SCALE);
		});

		LOGGER.info("Umbraxis awakens - the Decayed Wyrm has been loaded.");
	}
}
