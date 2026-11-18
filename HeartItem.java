package com.umbraxis.item;

import com.umbraxis.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click to absorb the beating heart of the wyrm. */
public class HeartItem extends Item {
	public HeartItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel) {
			player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 20 * 60 * 10, 4));
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 60 * 5, 4));
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 20 * 30, 2));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60 * 2, 1));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.0F);
			stack.consume(1, player);
		}
		return InteractionResult.SUCCESS;
	}
}
