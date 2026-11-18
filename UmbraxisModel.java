package com.umbraxis.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Animation for the generated geometry (see UmbraxisGeometry / tools/generate_assets.py). */
public class UmbraxisModel extends EntityModel<UmbraxisRenderState> {
	private static final float BASE_SCALE = 1.7F;
	private static final float DEG = (float) (Math.PI / 180.0);

	private final ModelPart base, body, legFL, legFR, legBL, legBR, neck1, neck2, head, jaw, hornL, hornR;
	private final ModelPart tail1, tail2, tail3, tail4, wingR, wingRTip, wingL, wingLTip;

	public UmbraxisModel(ModelPart root) {
		super(root);
		base = root.getChild("base");
		body = base.getChild("body");
		legFL = base.getChild("legFL");
		legFR = base.getChild("legFR");
		legBL = base.getChild("legBL");
		legBR = base.getChild("legBR");
		neck1 = body.getChild("neck1");
		neck2 = neck1.getChild("neck2");
		head = neck2.getChild("head");
		jaw = head.getChild("jaw");
		hornL = head.getChild("hornL");
		hornR = head.getChild("hornR");
		tail1 = body.getChild("tail1");
		tail2 = tail1.getChild("tail2");
		tail3 = tail2.getChild("tail3");
		tail4 = tail3.getChild("tail4");
		wingR = body.getChild("wingR");
		wingRTip = wingR.getChild("wingRTip");
		wingL = body.getChild("wingL");
		wingLTip = wingL.getChild("wingLTip");
	}

	private static float c01(float v) { return Mth.clamp(v, 0.0F, 1.0F); }
	private static float ease(float x) { return x * x * (3.0F - 2.0F * x); }

	@Override
	public void setupAnim(UmbraxisRenderState s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		float t = s.attackTick;
		float ph = s.phase;

		// ---------- global scale: rise from the ground / shrink into ash on death ----------
		float scale = BASE_SCALE, sink = 0.0F, shake = 0.0F;
		if (s.awaken < 1.0F) {
			float e = ease(s.awaken);
			sink = (1.0F - e) * 34.0F;
			scale *= 0.85F + 0.15F * e;
			shake = (1.0F - s.awaken) * 0.8F;
		}
		if (s.death > 0.0F) {
			float k = c01((s.death - 0.5F) / 0.5F);
			scale *= Math.max(0.02F, 1.0F - k * k);
			sink += k * k * 14.0F;
			shake = 0.4F + s.death * 1.6F;
		}
		base.xScale = scale;
		base.yScale = scale;
		base.zScale = scale;
		base.y = 24.0F + sink;

		// ---------- attack poses ----------
		float neckLift = 0, headPitch = 0, jawOpen = 0, bodyPitch = 0, wingSpread = 0, frontRaise = 0, crouch = 0;
		switch (s.attackId) {
			case 1 -> { // stomp
				float w = c01(t / 22.0F), slam = c01((t - 22.0F) / 3.0F), rec = c01((t - 26.0F) / 14.0F);
				float raise = w * (1.0F - slam);
				frontRaise = raise * 1.15F;
				bodyPitch = -0.28F * raise + 0.14F * slam * (1.0F - rec);
				neckLift = -0.2F * raise;
				headPitch = -0.3F * raise + 0.5F * slam * (1.0F - rec);
				jawOpen = 0.6F * raise;
				wingSpread = 0.3F * raise;
			}
			case 2 -> { // lightning
				float w = c01(t / 14.0F);
				neckLift = -0.45F * w; headPitch = -0.8F * w; jawOpen = w; wingSpread = 0.6F * w; bodyPitch = -0.15F * w;
			}
			case 3 -> { // dragon beam
				float w = c01(t / 40.0F);
				boolean fire = t >= 40.0F;
				neckLift = -0.35F * w;
				headPitch = fire ? 0.55F : -0.7F * w;
				jawOpen = 0.5F + 0.7F * w;
				wingSpread = 0.4F;
				bodyPitch = fire ? 0.06F : -0.12F * w;
			}
			case 4 -> { // sonic boom
				float w = c01(t / 30.0F);
				float thrust = c01((t - 34.0F) / 3.0F) * (1.0F - c01((t - 38.0F) / 10.0F));
				neckLift = -0.4F * w + 0.5F * thrust;
				headPitch = -0.6F * w + 0.9F * thrust;
				jawOpen = 0.9F * w;
			}
			case 5, 6 -> { // roar / meteors
				float w = c01(t / 12.0F) * (1.0F - c01((t - 58.0F) / 12.0F));
				neckLift = -0.6F * w;
				headPitch = -0.9F * w + Mth.sin(age * 1.2F) * 0.08F * w;
				jawOpen = 1.1F * w; wingSpread = 0.9F * w; bodyPitch = -0.22F * w;
			}
			case 7 -> { // leap
				crouch = t < 20.0F ? c01(t / 18.0F) : Math.max(0.0F, 1.0F - (t - 20.0F) / 6.0F);
				if (t >= 20.0F) { wingSpread = 0.9F; frontRaise = 0.6F; bodyPitch = 0.2F; headPitch = -0.3F; jawOpen = 0.8F; }
			}
			case 8 -> { // eruption
				frontRaise = Math.max(0.0F, Mth.sin(t * 0.45F)) * 0.9F;
				neckLift = -0.3F; headPitch = -0.4F; jawOpen = 0.5F; wingSpread = 0.3F;
			}
			case 9 -> { // claw / bite
				float w = c01(t / 16.0F);
				float strike = c01((t - 16.0F) / 2.0F) * (1.0F - c01((t - 18.0F) / 8.0F));
				neckLift = -0.5F * w + 0.6F * strike;
				headPitch = -0.5F * w + 1.0F * strike;
				jawOpen = 0.7F * w * (1.0F - strike) + 0.4F * strike;
			}
			default -> { }
		}

		// ---------- body ----------
		float breathe = Mth.sin(age * 0.08F);
		body.y = -24.0F + breathe * 0.6F + crouch * 4.0F;
		body.xRot = bodyPitch;
		body.yRot = 0.0F;
		body.zRot = 0.0F;
		if (shake > 0.0F) {
			body.xRot += Mth.sin(age * 5.3F) * shake * 0.05F;
			body.zRot += Mth.sin(age * 4.1F) * shake * 0.05F;
		}

		// ---------- neck / head / jaw ----------
		neck1.xRot = -0.55F + neckLift + breathe * 0.02F;
		neck2.xRot = -0.35F + neckLift * 0.5F + Mth.sin(age * 0.05F) * 0.03F;
		head.xRot = 0.75F + headPitch + s.xRot * DEG * 0.4F;
		head.yRot = Mth.clamp(s.yRot, -35.0F, 35.0F) * DEG * 0.5F;
		jaw.xRot = 0.04F + jawOpen * 0.85F + Mth.sin(age * 0.1F) * 0.03F;
		hornL.xRot = 0.5F;
		hornR.xRot = 0.5F;

		// ---------- legs ----------
		float swing = Mth.cos(s.walkAnimationPos * 0.45F) * 0.75F * Mth.clamp(s.walkAnimationSpeed * 2.0F, 0.0F, 1.0F);
		legFL.xRot = swing - frontRaise;
		legFR.xRot = -swing - frontRaise;
		legBL.xRot = -swing;
		legBR.xRot = swing;

		// ---------- tail ----------
		float sp = 0.09F + 0.02F * ph;
		float amp = 0.12F + 0.05F * ph;
		tail1.yRot = Mth.sin(age * sp) * amp;
		tail2.yRot = Mth.sin(age * sp - 0.7F) * amp * 1.5F;
		tail3.yRot = Mth.sin(age * sp - 1.4F) * amp * 1.7F;
		tail4.yRot = Mth.sin(age * sp - 2.1F) * amp * 2.0F;
		tail1.xRot = -bodyPitch * 0.5F;

		// ---------- wings (spread wider each phase) ----------
		float flap = Mth.sin(age * (0.07F + 0.02F * ph)) * (0.10F + 0.04F * ph);
		float spread = 0.15F * (ph - 1.0F) + wingSpread * 0.5F;
		wingR.zRot = -(0.9F + spread) + flap;
		wingRTip.zRot = 0.5F - spread * 0.8F + flap * 1.4F;
		wingL.zRot = (0.9F + spread) - flap;
		wingLTip.zRot = -(0.5F - spread * 0.8F + flap * 1.4F);
	}
}
