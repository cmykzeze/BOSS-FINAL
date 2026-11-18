package com.umbraxis.item;

import com.umbraxis.entity.UmbraxisBoss;
import com.umbraxis.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** Right-click the ground to awaken Umbraxis. */
public class SigilItem extends Item {
	public SigilItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());

		if (!server.getEntitiesOfClass(UmbraxisBoss.class, new AABB(pos).inflate(160.0)).isEmpty()) {
			if (player != null) {
				player.displayClientMessage(Component.translatable("umbraxis.message.already_awake"), true);
			}
			return InteractionResult.FAIL;
		}

		UmbraxisBoss boss = ModEntities.UMBRAXIS.create(server, EntitySpawnReason.SPAWN_ITEM_USE);
		if (boss == null) {
			return InteractionResult.FAIL;
		}
		boss.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
		boss.setYRot(yaw);
		boss.setYHeadRot(yaw);
		server.addFreshEntity(boss);

		context.getItemInHand().consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
