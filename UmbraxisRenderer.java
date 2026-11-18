package com.umbraxis.client;

import com.umbraxis.Umbraxis;
import com.umbraxis.entity.UmbraxisBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class UmbraxisRenderer extends MobRenderer<UmbraxisBoss, UmbraxisRenderState, UmbraxisModel> {
	private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Umbraxis.MOD_ID, "textures/entity/umbraxis.png");

	public UmbraxisRenderer(EntityRendererProvider.Context context) {
		super(context, new UmbraxisModel(context.bakeLayer(ModModelLayers.UMBRAXIS)), 2.6F);
	}

	@Override
	public UmbraxisRenderState createRenderState() {
		return new UmbraxisRenderState();
	}

	@Override
	public Identifier getTextureLocation(UmbraxisRenderState state) {
		return TEXTURE;
	}

	@Override
	public void extractRenderState(UmbraxisBoss entity, UmbraxisRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.phase = entity.getPhase();
		state.attackId = entity.getAttackId();
		state.attackTick = entity.getAttackTick() + partialTick;
		state.awaken = Mth.clamp((entity.getAwakenTick() + partialTick) / UmbraxisBoss.AWAKEN_TICKS, 0.0F, 1.0F);
		int d = entity.getDeathTick();
		state.death = d > 0 ? Mth.clamp((d + partialTick) / UmbraxisBoss.DEATH_TICKS, 0.0F, 1.0F) : 0.0F;
	}
}
