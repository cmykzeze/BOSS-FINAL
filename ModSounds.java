package com.umbraxis.registry;

import com.umbraxis.Umbraxis;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Custom sound events. Each one is mapped to layered vanilla (Warden / Ender Dragon / Wither) audio in assets/umbraxis/sounds.json. */
public final class ModSounds {
	private ModSounds() {}

	public static final SoundEvent SPAWN = register("boss.spawn");
	public static final SoundEvent ROAR = register("boss.roar");
	public static final SoundEvent AMBIENT = register("boss.ambient");
	public static final SoundEvent GROWL = register("boss.growl");
	public static final SoundEvent HURT = register("boss.hurt");
	public static final SoundEvent DEATH_SCREAM = register("boss.death_scream");
	public static final SoundEvent DEATH_FINAL = register("boss.death_final");
	public static final SoundEvent STOMP = register("boss.stomp");
	public static final SoundEvent STEP = register("boss.step");
	public static final SoundEvent SONIC_CHARGE = register("boss.sonic_charge");
	public static final SoundEvent SONIC_BOOM = register("boss.sonic_boom");
	public static final SoundEvent BEAM_CHARGE = register("boss.beam_charge");
	public static final SoundEvent BEAM_FIRE = register("boss.beam_fire");
	public static final SoundEvent THUNDER = register("boss.thunder");
	public static final SoundEvent PHASE_CHANGE = register("boss.phase_change");
	public static final SoundEvent EXPLOSION = register("boss.explosion");
	public static final SoundEvent HEARTBEAT = register("boss.heartbeat");

	private static SoundEvent register(String name) {
		Identifier id = Identifier.fromNamespaceAndPath(Umbraxis.MOD_ID, name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {}
}
