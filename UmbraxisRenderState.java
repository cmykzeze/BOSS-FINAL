package com.umbraxis.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class UmbraxisRenderState extends LivingEntityRenderState {
	public int phase = 1;
	public int attackId;
	public float attackTick;
	/** 0..1 : rising out of the ground. */
	public float awaken = 1.0F;
	/** 0..1 : death sequence progress (0 = alive). */
	public float death;
}
