package com.umbraxis.entity;

import com.umbraxis.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Umbraxis, the Decayed Wyrm - 10,000 HP, three phases, nine attacks.
 *
 * Health model: vanilla caps max health at 1024, so the boss keeps its own 10,000 point "pool"
 * (see {@link #MAX_POOL}) and mirrors it into the vanilla health value for compatibility.
 * Phase and awakening state are derived / stored as entity tags, so nothing needs custom NBT.
 */
public class UmbraxisBoss extends Monster {
	// ---------- TUNING (change these and rebuild) ----------
	public static final float MAX_POOL = 10000.0F;
	/** Per-hit damage cap, so nothing can one-shot a phase. */
	private static final float MAX_HIT = 400.0F;
	private static final float DAMAGE_TAKEN_MULTIPLIER = 1.0F;
	private static final int[] MINION_CAP = {8, 12, 16};

	public static final String TAG_MINION = "umbraxis_minion";
	public static final String TAG_AWAKE = "umbraxis_awake";
	public static final int AWAKEN_TICKS = 150;
	public static final int DEATH_TICKS = 260;
	public static final int TRANSITION_TICKS = 90;

	public static final int A_NONE = 0, A_STOMP = 1, A_LIGHTNING = 2, A_BEAM = 3, A_SONIC = 4,
			A_ROAR = 5, A_METEOR = 6, A_LEAP = 7, A_ERUPT = 8, A_CLAW = 9;

	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(UmbraxisBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(UmbraxisBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK_TICK = SynchedEntityData.defineId(UmbraxisBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_AWAKEN = SynchedEntityData.defineId(UmbraxisBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DEATH = SynchedEntityData.defineId(UmbraxisBoss.class, EntityDataSerializers.INT);

	private final ServerBossEvent bossEvent = new ServerBossEvent(
			Component.translatable("entity.umbraxis.umbraxis"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

	private record Strike(Vec3 pos, int at) {}

	private static final class Scheduled {
		int delay;
		final Runnable task;
		Scheduled(int delay, Runnable task) { this.delay = delay; this.task = task; }
	}

	private final List<Scheduled> scheduled = new ArrayList<>();
	private final List<Strike> strikes = new ArrayList<>();
	private final List<Set<UUID>> ringHits = new ArrayList<>();
	private final Vec3[] locks = new Vec3[3];

	private float pool = -1.0F;
	private boolean initialized;
	private boolean awake;
	private boolean freeze;
	private int phase = 1;
	private int attack;
	private int attackTick;
	private int cooldown = 40;
	private int lastAttack;
	private int transition;
	private int leapTicks;
	private Vec3 leapVel = Vec3.ZERO;
	private Vec3 lock = Vec3.ZERO;

	public UmbraxisBoss(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.bossEvent.setDarkenScreen(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 1024.0)
				.add(Attributes.MOVEMENT_SPEED, 0.26)
				.add(Attributes.ATTACK_DAMAGE, 30.0)
				.add(Attributes.FOLLOW_RANGE, 96.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.ARMOR, 20.0)
				.add(Attributes.ARMOR_TOUGHNESS, 12.0)
				.add(Attributes.STEP_HEIGHT, 2.0);
	}

	// ====================================================================================
	// Synched data (read by the client renderer)
	// ====================================================================================
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_ATTACK, 0);
		builder.define(DATA_ATTACK_TICK, 0);
		builder.define(DATA_AWAKEN, 0);
		builder.define(DATA_DEATH, 0);
	}

	public int getPhase() { return this.entityData.get(DATA_PHASE); }
	public int getAttackId() { return this.entityData.get(DATA_ATTACK); }
	public int getAttackTick() { return this.entityData.get(DATA_ATTACK_TICK); }
	public int getAwakenTick() { return this.entityData.get(DATA_AWAKEN); }
	public int getDeathTick() { return this.entityData.get(DATA_DEATH); }
	public boolean isDying() { return getDeathTick() > 0; }

	// ====================================================================================
	// Goals / basic overrides
	// ====================================================================================
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 48.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isPushable() { return false; }

	@Override
	public boolean removeWhenFarAway(double distanceSqr) { return false; }

	@Override
	protected SoundEvent getAmbientSound() { return ModSounds.AMBIENT; }

	@Override
	protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.HURT; }

	@Override
	protected SoundEvent getDeathSound() { return ModSounds.DEATH_FINAL; }

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		this.bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	// ====================================================================================
	// Damage model: 10,000 HP pool, phase gates, immunities
	// ====================================================================================
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		// /kill, void, and our own final blow go straight through to vanilla death handling.
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return super.hurtServer(level, source, amount);
		}
		if (!awake || isDying() || transition > 0) return false; // untouchable while rising / dying / changing phase
		if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_FIRE)) return false;
		Entity attacker = source.getEntity();
		if (attacker != null && (attacker == this || attacker.getTags().contains(TAG_MINION))) return false;

		float dmg = Math.min(amount * DAMAGE_TAKEN_MULTIPLIER, MAX_HIT);
		if (dmg <= 0.0F) return false;

		this.pool = Math.max(0.0F, this.pool - dmg);
		syncHealth();
		level.broadcastEntityEvent(this, (byte) 2); // red hurt flash + hurt sound
		if (attacker instanceof LivingEntity living && getTarget() == null) setTarget(living);

		if (this.pool <= 0.0F) {
			startDying(level);
		} else {
			int next = phaseFor(this.pool);
			if (next > this.phase) beginTransition(level, next);
		}
		return true;
	}

	private void syncHealth() {
		setHealth(Math.max(1.0F, getMaxHealth() * this.pool / MAX_POOL));
	}

	private static int phaseFor(float p) {
		return p > MAX_POOL * 0.6667F ? 1 : (p > MAX_POOL * 0.3333F ? 2 : 3);
	}

	// ====================================================================================
	// Main server tick
	// ====================================================================================
	@Override
	public void aiStep() {
		this.freeze = false;
		if (this.level() instanceof ServerLevel sl) {
			serverTick(sl);
		}
		if (this.freeze) {
			getNavigation().stop();
			Vec3 m = getDeltaMovement();
			setDeltaMovement(0.0, m.y, 0.0);
		}
		super.aiStep();
	}

	private void serverTick(ServerLevel sl) {
		if (!this.initialized) init(sl);

		if (!this.scheduled.isEmpty()) {
			List<Runnable> due = new ArrayList<>();
			this.scheduled.removeIf(s -> {
				if (--s.delay <= 0) { due.add(s.task); return true; }
				return false;
			});
			due.forEach(Runnable::run);
		}

		this.bossEvent.setProgress(Mth.clamp(this.pool / MAX_POOL, 0.0F, 1.0F));

		if (isDying()) { this.freeze = true; tickDying(sl); return; }
		if (!this.awake) { this.freeze = true; tickAwakening(sl); return; }
		aura(sl);
		if (this.transition > 0) { this.freeze = true; tickTransition(sl); return; }
		decayAura(sl);

		LivingEntity target = getTarget();
		if (target == null || !target.isAlive()) {
			Player nearest = sl.getNearestPlayer(this, 72.0);
			if (nearest != null) setTarget(nearest);
			target = getTarget();
		}
		if (target == null) {
			if (this.attack != A_NONE) endAttack();
			return;
		}

		if (this.attack == A_NONE) {
			walkTowards(target);
			if (this.cooldown > 0) { this.cooldown--; return; }
			chooseAttack(sl, target);
		} else {
			this.attackTick++;
			this.freeze = this.attack != A_LEAP;
			runAttack(sl, target);
			this.entityData.set(DATA_ATTACK_TICK, this.attackTick);
		}
	}

	private void init(ServerLevel sl) {
		this.initialized = true;
		if (this.pool < 0.0F) this.pool = MAX_POOL * getHealth() / getMaxHealth();
		this.phase = phaseFor(this.pool);
		this.entityData.set(DATA_PHASE, this.phase);
		if (getTags().contains(TAG_AWAKE)) {
			this.awake = true;
			this.entityData.set(DATA_AWAKEN, AWAKEN_TICKS);
			applyPhaseStats();
		}
	}

	private void walkTowards(LivingEntity t) {
		double d = distanceTo(t);
		if (d > 4.5) {
			if (this.tickCount % 10 == 0 || getNavigation().isDone()) getNavigation().moveTo(t, 1.0);
		} else {
			getNavigation().stop();
			faceTarget(t, 25.0F);
		}
		Vec3 m = getDeltaMovement();
		if (onGround() && m.horizontalDistanceSqr() > 0.002 && this.tickCount % 14 == 0) {
			sfx(ModSounds.STEP, 1.6F, 0.9F);
			sendParticles(ParticleTypes.POOF, getX(), getY() + 0.1, getZ(), 6, 1.2, 0.1, 1.2, 0.02);
		}
	}

	// ====================================================================================
	// Attack selection & dispatch
	// ====================================================================================
	private void chooseAttack(ServerLevel sl, LivingEntity t) {
		double d = distanceTo(t);
		List<int[]> options = new ArrayList<>();
		options.add(new int[] {A_STOMP, d < 16 ? 5 : 1});
		options.add(new int[] {A_LIGHTNING, 4});
		if (d < 6.5) options.add(new int[] {A_CLAW, 9});
		if (d > 5) options.add(new int[] {A_BEAM, 4});
		if (d > 7) options.add(new int[] {A_SONIC, 4});
		if (minionCount(sl) < MINION_CAP[this.phase - 1]) options.add(new int[] {A_ROAR, 4});
		if (this.phase >= 2) {
			options.add(new int[] {A_METEOR, 3});
			options.add(new int[] {A_ERUPT, 3});
			if (d > 9) options.add(new int[] {A_LEAP, 4});
		}
		if (options.size() > 1) options.removeIf(o -> o[0] == this.lastAttack);

		int total = 0;
		for (int[] o : options) total += o[1];
		int roll = this.random.nextInt(total);
		int chosen = options.get(0)[0];
		for (int[] o : options) {
			roll -= o[1];
			if (roll < 0) { chosen = o[0]; break; }
		}
		startAttack(chosen);
	}

	private void startAttack(int id) {
		this.attack = id;
		this.attackTick = 0;
		this.strikes.clear();
		this.ringHits.clear();
		this.leapTicks = 0;
		getNavigation().stop();
		this.entityData.set(DATA_ATTACK, id);
		this.entityData.set(DATA_ATTACK_TICK, 0);
	}

	private void endAttack() {
		this.lastAttack = this.attack;
		this.attack = A_NONE;
		this.attackTick = 0;
		this.strikes.clear();
		this.entityData.set(DATA_ATTACK, 0);
		this.entityData.set(DATA_ATTACK_TICK, 0);
		this.cooldown = switch (this.phase) {
			case 1 -> 50 + this.random.nextInt(30);
			case 2 -> 30 + this.random.nextInt(25);
			default -> 12 + this.random.nextInt(20);
		};
	}

	private void runAttack(ServerLevel sl, LivingEntity t) {
		switch (this.attack) {
			case A_STOMP -> tickStomp(sl, t);
			case A_LIGHTNING -> tickLightning(sl, t);
			case A_BEAM -> tickBeam(sl, t);
			case A_SONIC -> tickSonic(sl, t);
			case A_ROAR -> tickRoar(sl, t);
			case A_METEOR -> tickMeteor(sl, t);
			case A_LEAP -> tickLeap(sl, t);
			case A_ERUPT -> tickErupt(sl, t);
			case A_CLAW -> tickClaw(sl, t);
			default -> endAttack();
		}
	}

	// ----------------------------- 1. STOMP (jump over the shockwave) -----------------------------
	private void tickStomp(ServerLevel sl, LivingEntity t) {
		final int impact = 24;
		final int rings = this.phase;
		if (this.attackTick < impact) faceTarget(t, 20.0F);
		if (this.attackTick == 1) sfx(ModSounds.GROWL, 2.0F, 1.0F);
		if (this.attackTick < impact && this.attackTick % 3 == 0) {
			sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.2, getZ(), 6, 2.0, 0.1, 2.0, 0.02);
		}
		if (this.attackTick == impact) {
			sfx(ModSounds.STOMP, 3.0F, 0.9F);
			sfx(ModSounds.EXPLOSION, 2.5F, 0.8F);
			sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
			for (int i = 0; i < rings; i++) this.ringHits.add(new HashSet<>());
			if (this.phase >= 2) { // break the ground
				for (int i = 0; i < 6; i++) {
					double a = i * (Math.PI / 3.0);
					craterAt(sl, getX() + Math.cos(a) * 6.0, getY(), getZ() + Math.sin(a) * 6.0, 2.2F);
				}
			}
		}
		for (int i = 0; i < this.ringHits.size(); i++) {
			int start = impact + i * 10;
			int age = this.attackTick - start;
			if (age < 0 || age >= 34) continue;
			double r = 3.0 + age * 0.75;
			ring(sl, ParticleTypes.LARGE_SMOKE, position(), r, 40, getY() + 0.2, 0.01);
			ring(sl, ParticleTypes.CRIMSON_SPORE, position(), r, 24, getY() + 0.4, 0.02);
			if (age % 6 == 0) sfx(ModSounds.STEP, 1.5F, 0.6F);
			for (LivingEntity v : victims(sl, position(), r + 3.0)) {
				double dh = Math.hypot(v.getX() - getX(), v.getZ() - getZ());
				if (Math.abs(dh - r) <= 1.7 && v.onGround() && this.ringHits.get(i).add(v.getUUID())) {
					hit(sl, v, damageSources().mobAttack(this), 24.0F, 1.2);
				}
			}
		}
		if (this.attackTick > impact + (rings - 1) * 10 + 38) endAttack();
	}

	// ----------------------------- 2. LIGHTNING (leave the marked circles) -----------------------------
	private void tickLightning(ServerLevel sl, LivingEntity t) {
		faceTarget(t, 10.0F);
		if (this.attackTick == 1) {
			sfx(ModSounds.ROAR, 2.5F, 1.1F);
			int n = new int[] {6, 10, 15}[this.phase - 1];
			this.strikes.add(new Strike(t.position(), 34));
			for (int i = 1; i < n; i++) {
				Vec3 c = (this.phase == 3 && i % 3 == 0) ? position() : t.position();
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = (c == t.position() ? 2.0 : 5.0) + this.random.nextDouble() * 9.0;
				this.strikes.add(new Strike(c.add(Math.cos(a) * r, 0, Math.sin(a) * r), 34 + (i * 20) / n));
			}
		}
		if (this.phase >= 2 && this.attackTick == 18) this.strikes.add(new Strike(t.position(), 46));
		if (this.phase >= 3 && this.attackTick == 30) this.strikes.add(new Strike(t.position(), 58));
		runStrikes(sl, A_LIGHTNING);
	}

	// ----------------------------- 3. DRAGON BEAM (sidestep / outrun the sweep) -----------------------------
	private void tickBeam(ServerLevel sl, LivingEntity t) {
		final int wind = 40, fire = 50;
		if (this.attackTick == 1) sfx(ModSounds.BEAM_CHARGE, 3.0F, 0.9F);
		if (this.attackTick < wind) {
			faceTarget(t, 20.0F);
			Vec3 o = mouth();
			sendParticles(ParticleTypes.PORTAL, o.x, o.y, o.z, 12, 0.5, 0.5, 0.5, 0.6);
			if (this.attackTick > 14 && this.attackTick % 2 == 0) {
				Vec3 dir = new Vec3(t.getX() - o.x, 0, t.getZ() - o.z).normalize();
				double len = Math.min(40.0, Math.hypot(t.getX() - o.x, t.getZ() - o.z) + 12.0);
				for (double s = 2.0; s < len; s += 1.5) {
					sendParticles(ParticleTypes.ELECTRIC_SPARK, o.x + dir.x * s, t.getY() + 0.4, o.z + dir.z * s, 1, 0, 0, 0, 0);
				}
			}
		}
		if (this.attackTick == wind) {
			this.lock = t.position();
			sfx(ModSounds.BEAM_FIRE, 3.0F, 0.8F);
		}
		if (this.attackTick >= wind && this.attackTick < wind + fire) fireBeam(sl, this.attackTick - wind, fire);
		if (this.attackTick >= wind + fire + 8) endAttack();
	}

	private void fireBeam(ServerLevel sl, int k, int fireTicks) {
		double half = Math.toRadians(new double[] {12.0, 30.0, 42.0}[this.phase - 1]);
		double ang = -half + 2.0 * half * (k / (double) (fireTicks - 1));
		Vec3 o = mouth();
		Vec3 base = new Vec3(this.lock.x - o.x, 0, this.lock.z - o.z);
		base = base.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, getYRot()) : base.normalize();
		double c = Math.cos(ang), sn = Math.sin(ang);
		Vec3 h = new Vec3(base.x * c + base.z * sn, 0, -base.x * sn + base.z * c);
		double d = Math.max(4.0, Math.hypot(this.lock.x - o.x, this.lock.z - o.z));
		double groundY = this.lock.y + 0.4;
		final double len = 40.0;
		setYRot(Mth.approachDegrees(getYRot(), (float) (Mth.atan2(h.z, h.x) * 57.29577951308232) - 90.0F, 6.0F));

		for (double s = 0.0; s <= len; s += 1.4) {
			double y = o.y + (groundY - o.y) * Math.min(1.0, s / d);
			double x = o.x + h.x * s, z = o.z + h.z * s;
			sendParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.1, 0.1, 0.1, 0.0);
			sendParticles(ParticleTypes.CRIMSON_SPORE, x, y, z, 2, 0.35, 0.35, 0.35, 0.02);
			if (k % 2 == 0) sendParticles(ParticleTypes.SQUID_INK, x, y, z, 1, 0.3, 0.3, 0.3, 0.02);
		}
		double endX = o.x + h.x * d, endZ = o.z + h.z * d;
		if (k % 8 == 0) craterAt(sl, endX, this.lock.y, endZ, 1.6F);
		if (k % 4 == 0) sfx(ModSounds.BEAM_FIRE, 1.2F, 1.2F);

		if (k % 2 == 0) {
			for (LivingEntity v : victims(sl, o, len + 2.0)) {
				Vec3 rel = v.position().subtract(o);
				double sp = rel.x * h.x + rel.z * h.z;
				if (sp < 0.0 || sp > len) continue;
				double perp = Math.abs(rel.x * h.z - rel.z * h.x);
				double by = o.y + (groundY - o.y) * Math.min(1.0, sp / d);
				if (perp <= 1.8 && v.getY() <= by + 0.7 && v.getY() + v.getBbHeight() >= by - 0.5) {
					hit(sl, v, damageSources().indirectMagic(this, this), 16.0F, 0.6);
				}
			}
		}
	}

	// ----------------------------- 4. SONIC BOOM (Warden attack; break line after the lock) -----------------------------
	private void tickSonic(ServerLevel sl, LivingEntity t) {
		int booms = this.phase;
		if (this.attackTick == 1) sfx(ModSounds.SONIC_CHARGE, 3.0F, 1.0F);
		boolean tracking = true;
		for (int i = 0; i < booms; i++) {
			int fireAt = 36 + i * 14;
			if (this.attackTick == fireAt - 10) this.locks[i] = t.getBoundingBox().getCenter();
			if (this.attackTick > fireAt - 10 && this.attackTick <= fireAt) tracking = false;
			if (this.attackTick > fireAt - 10 && this.attackTick < fireAt && this.attackTick % 2 == 0 && this.locks[i] != null) {
				Vec3 o = mouth();
				Vec3 dir = this.locks[i].subtract(o).normalize();
				for (double s = 2.0; s < 30.0; s += 2.0) {
					sendParticles(ParticleTypes.SCULK_SOUL, o.x + dir.x * s, o.y + dir.y * s, o.z + dir.z * s, 1, 0, 0, 0, 0);
				}
			}
			if (this.attackTick == fireAt && this.locks[i] != null) fireSonic(sl, this.locks[i]);
		}
		if (tracking) faceTarget(t, 18.0F);
		if (this.attackTick > 36 + (booms - 1) * 14 + 14) endAttack();
	}

	private void fireSonic(ServerLevel sl, Vec3 aim) {
		Vec3 o = mouth();
		Vec3 dir = aim.subtract(o).normalize();
		final double len = 36.0;
		sfx(ModSounds.SONIC_BOOM, 3.0F, 1.0F);
		for (double s = 1.0; s < len; s += 1.0) {
			sendParticles(ParticleTypes.SONIC_BOOM, o.x + dir.x * s, o.y + dir.y * s, o.z + dir.z * s, 1, 0, 0, 0, 0);
		}
		for (LivingEntity v : victims(sl, o, len + 2.0)) {
			Vec3 rel = v.getBoundingBox().getCenter().subtract(o);
			double proj = rel.dot(dir);
			if (proj < 0.0 || proj > len) continue;
			if (rel.subtract(dir.scale(proj)).length() < 1.7) {
				hit(sl, v, damageSources().sonicBoom(this), 30.0F, 2.0);
			}
		}
	}

	// ----------------------------- 5. ROAR + SUMMON -----------------------------
	private void tickRoar(ServerLevel sl, LivingEntity t) {
		faceTarget(t, 10.0F);
		if (this.attackTick == 1) sfx(ModSounds.ROAR, 3.0F, 0.9F);
		if (this.attackTick == 14) {
			ring(sl, ParticleTypes.SQUID_INK, position(), 3.0, 30, getY() + 1.0, 0.0);
			for (int i = 0; i < 6; i++) {
				final int step = i;
				schedule(i * 2, () -> ring(sl, ParticleTypes.CRIMSON_SPORE, position(), 3.0 + step * 2.5, 36, getY() + 0.6, 0.0));
			}
			for (LivingEntity v : victims(sl, position(), 14.0)) {
				hit(sl, v, damageSources().mobAttack(this), 6.0F, 1.4);
			}
		}
		if (this.attackTick == 30) {
			int want = Math.min(this.phase * 2 + 1, MINION_CAP[this.phase - 1] - minionCount(sl));
			for (int i = 0; i < want; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = 5.0 + this.random.nextDouble() * 4.0;
				Vec3 p = position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
				spawnMinion(sl, p, t);
			}
		}
		if (this.attackTick >= 70) endAttack();
	}

	private int minionCount(ServerLevel sl) {
		return sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(90.0), m -> m.getTags().contains(TAG_MINION)).size();
	}

	private void spawnMinion(ServerLevel sl, Vec3 p, LivingEntity target) {
		int roll = this.random.nextInt(this.phase == 1 ? 2 : (this.phase == 2 ? 3 : 4));
		EntityType<? extends Mob> type;
		String name;
		double hp;
		double dmg;
		switch (roll) {
			case 0 -> { type = EntityType.HUSK; name = "Rotted Husk"; hp = 160.0; dmg = 16.0; }
			case 1 -> { type = EntityType.WITHER_SKELETON; name = "Hollow Knight"; hp = 220.0; dmg = 20.0; }
			case 2 -> { type = EntityType.VEX; name = "Wraith"; hp = 90.0; dmg = 18.0; }
			default -> { type = EntityType.BLAZE; name = "Cinder Wraith"; hp = 180.0; dmg = 14.0; }
		}
		Mob m = type.create(sl, EntitySpawnReason.EVENT);
		if (m == null) return;
		m.setPos(p.x, p.y, p.z);
		m.setYRot(this.random.nextFloat() * 360.0F);
		m.finalizeSpawn(sl, sl.getCurrentDifficultyAt(BlockPos.containing(p)), EntitySpawnReason.EVENT, null);
		setBase(m, Attributes.MAX_HEALTH, hp);
		m.setHealth((float) hp);
		setBase(m, Attributes.ATTACK_DAMAGE, dmg);
		setBase(m, Attributes.FOLLOW_RANGE, 64.0);
		m.addTag(TAG_MINION);
		m.setPersistenceRequired();
		m.setCustomName(Component.literal(name).withStyle(ChatFormatting.DARK_RED));
		m.setTarget(target);
		sl.addFreshEntity(m);
		visualBolt(sl, p);
		sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 1.0, p.z, 30, 0.5, 1.0, 0.5, 0.08);
	}

	private static void setBase(Mob m, Holder<Attribute> attr, double value) {
		AttributeInstance inst = m.getAttribute(attr);
		if (inst != null) inst.setBaseValue(value);
	}

	// ----------------------------- 6. METEORS (explosive rain; phase 2+) -----------------------------
	private void tickMeteor(ServerLevel sl, LivingEntity t) {
		faceTarget(t, 10.0F);
		if (this.attackTick == 1) {
			sfx(ModSounds.ROAR, 3.0F, 0.8F);
			int n = this.phase == 2 ? 7 : 12;
			this.strikes.add(new Strike(t.position(), 34));
			for (int i = 1; i < n; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = 2.0 + this.random.nextDouble() * 10.0;
				this.strikes.add(new Strike(t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r), 30 + i * 3));
			}
		}
		if (this.attackTick == 20) this.strikes.add(new Strike(t.position(), 50));
		runStrikes(sl, A_METEOR);
	}

	// ----------------------------- 7. LEAP SLAM (phase 2+) -----------------------------
	private void tickLeap(ServerLevel sl, LivingEntity t) {
		final int jumpAt = 20;
		if (this.attackTick == 1) sfx(ModSounds.GROWL, 2.5F, 0.9F);
		if (this.attackTick < jumpAt) {
			this.freeze = true;
			faceTarget(t, 25.0F);
			if (this.attackTick == jumpAt - 2) {
				Vec3 d = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
				if (d.length() > 30.0) d = d.normalize().scale(30.0);
				this.lock = new Vec3(getX() + d.x, t.getY(), getZ() + d.z);
				this.leapVel = new Vec3(d.x / 22.0, 0, d.z / 22.0);
			}
			if (this.attackTick >= jumpAt - 2) ring(sl, ParticleTypes.FLAME, this.lock, 7.0, 28, this.lock.y + 0.2, 0.0);
			return;
		}
		if (this.attackTick == jumpAt) {
			sfx(ModSounds.ROAR, 3.0F, 1.2F);
			setDeltaMovement(this.leapVel.x, 0.95, this.leapVel.z);
		}
		this.leapTicks++;
		Vec3 m = getDeltaMovement();
		setDeltaMovement(this.leapVel.x, m.y, this.leapVel.z);
		if (this.attackTick % 2 == 0) ring(sl, ParticleTypes.FLAME, this.lock, 7.0, 28, this.lock.y + 0.2, 0.0);
		sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 4, 1.5, 1.0, 1.5, 0.02);

		if ((this.leapTicks > 6 && onGround()) || this.leapTicks > 45) {
			sfx(ModSounds.STOMP, 3.0F, 0.7F);
			sfx(ModSounds.EXPLOSION, 3.0F, 0.6F);
			sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
			craterAt(sl, getX(), getY(), getZ(), 4.0F);
			for (LivingEntity v : victims(sl, position(), 9.0)) {
				float falloff = (float) Math.max(0.35, 1.0 - distanceTo(v) / 12.0);
				hit(sl, v, damageSources().mobAttack(this), 34.0F * falloff, 1.8);
			}
			setDeltaMovement(0, 0, 0);
			endAttack();
		}
	}

	// ----------------------------- 8. ROT ERUPTION (geysers; phase 2+) -----------------------------
	private void tickErupt(ServerLevel sl, LivingEntity t) {
		faceTarget(t, 10.0F);
		if (this.attackTick == 1) {
			sfx(ModSounds.STOMP, 3.0F, 0.6F);
			int n = this.phase == 2 ? 14 : 22;
			double base = this.random.nextDouble() * Math.PI * 2.0;
			for (int i = 0; i < n; i++) {
				double a = base + i * 0.75;
				double r = 4.0 + i * (18.0 / n);
				this.strikes.add(new Strike(position().add(Math.cos(a) * r, 0, Math.sin(a) * r), 30 + i * 2));
			}
		}
		if (this.attackTick == 10) {
			for (int i = 0; i < 3 + this.phase; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0;
				double r = this.random.nextDouble() * 4.0;
				this.strikes.add(new Strike(t.position().add(Math.cos(a) * r, 0, Math.sin(a) * r), 44 + i * 3));
			}
		}
		runStrikes(sl, A_ERUPT);
	}

	// ----------------------------- 9. CLAW / BITE (close range cone) -----------------------------
	private void tickClaw(ServerLevel sl, LivingEntity t) {
		if (this.attackTick < 14) faceTarget(t, 40.0F);
		if (this.attackTick == 1) sfx(ModSounds.GROWL, 2.0F, 1.3F);
		if (this.attackTick == 16) {
			Vec3 center = position().add(Vec3.directionFromRotation(0, getYRot()).scale(3.4));
			sfx(ModSounds.STOMP, 2.5F, 1.2F);
			sendParticles(ParticleTypes.EXPLOSION, center.x, getY() + 1.5, center.z, 3, 1.0, 0.8, 1.0, 0.0);
			float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
			for (LivingEntity v : victims(sl, center, 4.2)) {
				hit(sl, v, damageSources().mobAttack(this), dmg, 1.8);
			}
		}
		if (this.attackTick >= 28) endAttack();
	}

	// ====================================================================================
	// Strike scheduler shared by lightning / meteors / eruption
	// ====================================================================================
	private void runStrikes(ServerLevel sl, int kind) {
		int maxAt = 0;
		for (Strike s : this.strikes) {
			maxAt = Math.max(maxAt, s.at());
			int dt = s.at() - this.attackTick;
			if (dt > 0 && dt <= 36 && this.attackTick % 2 == 0) telegraph(sl, kind, s.pos(), dt);
			if (kind == A_METEOR && dt >= 0 && dt < 10) {
				double y = s.pos().y + dt * 2.5;
				sendParticles(ParticleTypes.FLAME, s.pos().x, y, s.pos().z, 8, 0.6, 0.6, 0.6, 0.02);
				sendParticles(ParticleTypes.LARGE_SMOKE, s.pos().x, y + 1.0, s.pos().z, 4, 0.5, 0.8, 0.5, 0.02);
			}
			if (dt == 0) detonate(sl, kind, s.pos());
		}
		if (this.attackTick > maxAt + 12) endAttack();
	}

	private void telegraph(ServerLevel sl, int kind, Vec3 pos, int dt) {
		double r = kind == A_ERUPT ? 2.4 : 3.4;
		ParticleOptions a = kind == A_LIGHTNING ? ParticleTypes.ELECTRIC_SPARK : (kind == A_METEOR ? ParticleTypes.FLAME : ParticleTypes.SQUID_INK);
		ParticleOptions b = kind == A_LIGHTNING ? ParticleTypes.END_ROD : ParticleTypes.CRIMSON_SPORE;
		ring(sl, a, pos, r, 14, pos.y + 0.15, 0.0);
		ring(sl, b, pos, r * 0.55, 8, pos.y + 0.15, 0.0);
		if (dt <= 8) sendParticles(b, pos.x, pos.y + 0.3, pos.z, 6, 0.3, 0.3, 0.3, 0.05);
	}

	private void detonate(ServerLevel sl, int kind, Vec3 pos) {
		if (kind == A_LIGHTNING) {
			visualBolt(sl, pos);
			sl.playSound(null, pos.x, pos.y, pos.z, ModSounds.THUNDER, SoundSource.HOSTILE, 3.0F, 1.0F);
			sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y + 0.5, pos.z, 40, 1.5, 0.8, 1.5, 0.4);
			for (LivingEntity v : victims(sl, pos, 3.6)) {
				if (Math.hypot(v.getX() - pos.x, v.getZ() - pos.z) <= 3.4) hit(sl, v, damageSources().lightningBolt(), 22.0F, 0.9);
			}
		} else if (kind == A_METEOR) {
			craterAt(sl, pos.x, pos.y + 0.5, pos.z, 3.2F);
		} else if (kind == A_ERUPT) {
			sl.playSound(null, pos.x, pos.y, pos.z, ModSounds.EXPLOSION, SoundSource.HOSTILE, 1.2F, 0.7F);
			for (int y = 0; y < 9; y++) {
				sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + y, pos.z, 4, 0.5, 0.2, 0.5, 0.02);
				sendParticles(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y + y, pos.z, 6, 0.5, 0.2, 0.5, 0.05);
				sendParticles(ParticleTypes.FLAME, pos.x, pos.y + y, pos.z, 3, 0.4, 0.2, 0.4, 0.04);
			}
			for (LivingEntity v : victims(sl, pos, 2.6)) {
				if (Math.hypot(v.getX() - pos.x, v.getZ() - pos.z) <= 2.4 && Math.abs(v.getY() - pos.y) < 3.0) {
					v.setDeltaMovement(v.getDeltaMovement().add(0.0, 1.05, 0.0));
					v.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
					hit(sl, v, damageSources().indirectMagic(this, this), 18.0F, 0.0);
				}
			}
		}
	}

	// ====================================================================================
	// Awakening (spawn), phase transitions, passive aura, death
	// ====================================================================================
	private void tickAwakening(ServerLevel sl) {
		int t = this.entityData.get(DATA_AWAKEN);
		if (t == 0) onAwakenStart(sl);
		t++;
		this.entityData.set(DATA_AWAKEN, t);
		getNavigation().stop();

		if (t % 2 == 0) {
			sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 8, 2.5, 0.2, 2.5, 0.03);
			sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 0.3, getZ(), 6, 2.5, 0.3, 2.5, 0.05);
		}
		if (t % 10 == 0) {
			double a = this.random.nextDouble() * Math.PI * 2.0, r = 8.0 + this.random.nextDouble() * 8.0;
			visualBolt(sl, position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
			sl.playSound(null, getX(), getY(), getZ(), ModSounds.THUNDER, SoundSource.HOSTILE, 3.0F, 0.9F);
		}
		if (t == 50) sfx(ModSounds.SPAWN, 4.0F, 1.0F);
		if (t == 100) {
			sfx(ModSounds.ROAR, 4.0F, 0.8F);
			for (int i = 0; i < 6; i++) {
				final int step = i;
				schedule(i * 2, () -> ring(sl, ParticleTypes.SQUID_INK, position(), 3.0 + step * 3.0, 40, getY() + 0.5, 0.0));
			}
		}
		if (t >= AWAKEN_TICKS) {
			this.awake = true;
			addTag(TAG_AWAKE);
			this.cooldown = 40;
			applyPhaseStats();
			announce(sl, "umbraxis.message.awake");
		}
	}

	/** Wither-style spawn blast + Warden darkness/blindness for the first 2 seconds only. */
	private void onAwakenStart(ServerLevel sl) {
		for (ServerPlayer p : sl.players()) {
			double d2 = p.distanceToSqr(this);
			if (d2 < 48.0 * 48.0) {
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
			}
			if (d2 < 18.0 * 18.0) { // mercy: the summoner survives the blast
				p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100, 3, false, true));
			}
		}
		sfx(ModSounds.PHASE_CHANGE, 5.0F, 0.9F);
		sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.5, getZ(), 3, 2.0, 1.0, 2.0, 0.0);
		sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 1.5, getZ(), 80, 3.0, 2.0, 3.0, 0.3);
		sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 1.5, getZ(), 80, 3.0, 2.0, 3.0, 0.3);
		sl.explode(this, getX(), getY() + 1.5, getZ(), 7.0F, Level.ExplosionInteraction.MOB);
	}

	private void beginTransition(ServerLevel sl, int newPhase) {
		this.phase = newPhase;
		this.entityData.set(DATA_PHASE, newPhase);
		this.transition = TRANSITION_TICKS;
		this.scheduled.clear();
		if (this.attack != A_NONE) endAttack();
		this.cooldown = 30;
		applyPhaseStats();
		announce(sl, newPhase == 2 ? "umbraxis.message.phase2" : "umbraxis.message.phase3");
	}

	private void applyPhaseStats() {
		setBase(this, Attributes.MOVEMENT_SPEED, new double[] {0.26, 0.31, 0.36}[this.phase - 1]);
		this.bossEvent.setColor(this.phase == 1 ? BossEvent.BossBarColor.PURPLE : (this.phase == 2 ? BossEvent.BossBarColor.PINK : BossEvent.BossBarColor.RED));
		this.bossEvent.setName(Component.translatable("entity.umbraxis.umbraxis").append(Component.literal("  - Phase " + "I".repeat(this.phase))));
	}

	private void tickTransition(ServerLevel sl) {
		this.transition--;
		int t = TRANSITION_TICKS - this.transition;
		getNavigation().stop();
		sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 3.0, getZ(), 14, 2.0, 2.5, 2.0, 0.08);
		sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 3.0, getZ(), 14, 2.0, 2.5, 2.0, 0.08);
		if (t == 1) { sfx(ModSounds.PHASE_CHANGE, 5.0F, 0.9F); sfx(ModSounds.ROAR, 4.0F, 0.7F); }
		if (t == 14 || t == 40 || t == 66) {
			sfx(ModSounds.STOMP, 3.0F, 0.7F);
			for (int i = 0; i < 6; i++) {
				final int step = i;
				schedule(i * 2, () -> ring(sl, ParticleTypes.EXPLOSION, position(), 3.0 + step * 3.0, 24, getY() + 0.5, 0.0));
			}
			for (LivingEntity v : victims(sl, position(), 16.0)) {
				hit(sl, v, damageSources().mobAttack(this), 8.0F, 2.2);
			}
			for (int i = 0; i < 4; i++) {
				double a = i * (Math.PI / 2.0) + t;
				craterAt(sl, getX() + Math.cos(a) * 8.0, getY(), getZ() + Math.sin(a) * 8.0, 2.0F);
			}
		}
		if (t % 12 == 0) {
			double a = this.random.nextDouble() * Math.PI * 2.0, r = 6.0 + this.random.nextDouble() * 12.0;
			visualBolt(sl, position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
			sl.playSound(null, getX(), getY(), getZ(), ModSounds.THUNDER, SoundSource.HOSTILE, 3.0F, 0.8F);
		}
		if (t == 45) {
			LivingEntity target = getTarget();
			int want = Math.min(this.phase + 2, MINION_CAP[this.phase - 1] - minionCount(sl));
			for (int i = 0; i < want; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0, r = 6.0 + this.random.nextDouble() * 4.0;
				spawnMinion(sl, position().add(Math.cos(a) * r, 0, Math.sin(a) * r), target);
			}
		}
	}

	private void aura(ServerLevel sl) {
		if (this.tickCount % 2 != 0) return;
		double x = getX(), y = getY(), z = getZ();
		double a = this.tickCount * 0.27;
		for (int k = 0; k < 2; k++) {
			double ang = a + k * Math.PI;
			double h = (this.tickCount * 0.12 + k * 3.0) % 6.0;
			sendParticles(k == 0 ? ParticleTypes.CRIMSON_SPORE : ParticleTypes.SQUID_INK,
					x + Math.cos(ang) * 2.9, y + h, z + Math.sin(ang) * 2.9, 2, 0.1, 0.1, 0.1, 0.0);
		}
		sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 3.0, z, 1 + this.phase, 1.7, 1.8, 1.7, 0.005);
		sendParticles(ParticleTypes.ASH, x, y + 5.0, z, 3, 2.0, 1.5, 2.0, 0.0);
		if (this.tickCount % 8 == 0) sendParticles(ParticleTypes.SCULK_SOUL, x, y + 1.0, z, 1, 1.5, 1.0, 1.5, 0.02);
		if (this.phase >= 3) sendParticles(ParticleTypes.FLAME, x, y + 2.0, z, 2, 1.8, 1.5, 1.8, 0.02);
	}

	/** Decay aura: from phase 2 the rot withers anyone who stands too close. */
	private void decayAura(ServerLevel sl) {
		if (this.phase < 2 || this.tickCount % 20 != 0) return;
		double r = this.phase == 2 ? 9.0 : 13.0;
		for (LivingEntity v : victims(sl, position(), r)) {
			if (distanceTo(v) <= r) v.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, this.phase - 2));
		}
	}

	// ----------------------------- death sequence (screams, fades into ash, light beams) -----------------------------
	private void startDying(ServerLevel sl) {
		this.pool = 0.0F;
		setHealth(1.0F);
		this.scheduled.clear();
		this.strikes.clear();
		this.attack = A_NONE;
		this.entityData.set(DATA_ATTACK, 0);
		this.entityData.set(DATA_DEATH, 1);
		setTarget(null);
		this.bossEvent.setName(Component.literal("Umbraxis is dying..."));
		this.bossEvent.setColor(BossEvent.BossBarColor.WHITE);
		for (Mob m : sl.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(120.0), mm -> mm.getTags().contains(TAG_MINION))) {
			sendParticles(ParticleTypes.SOUL_FIRE_FLAME, m.getX(), m.getY() + 1.0, m.getZ(), 25, 0.4, 0.8, 0.4, 0.06);
			m.discard();
		}
	}

	private void tickDying(ServerLevel sl) {
		int t = getDeathTick() + 1;
		this.entityData.set(DATA_DEATH, t);
		float p = t / (float) DEATH_TICKS;
		getNavigation().stop();

		if (t == 1) sfx(ModSounds.DEATH_SCREAM, 5.0F, 1.0F);
		if (t % 50 == 0 && t < DEATH_TICKS - 10) sfx(ModSounds.DEATH_SCREAM, 4.0F, 0.9F + p * 0.5F);
		if (t % 20 == 0) sfx(ModSounds.HEARTBEAT, 3.0F, 0.6F + p);

		if (t % 4 == 0) {
			double x = getX() + (this.random.nextDouble() - 0.5) * 3.5;
			double y = getY() + this.random.nextDouble() * getBbHeight();
			double z = getZ() + (this.random.nextDouble() - 0.5) * 3.5;
			sendParticles(ParticleTypes.EXPLOSION, x, y, z, 1, 0, 0, 0, 0);
			if (t % 12 == 0) sl.playSound(null, x, y, z, ModSounds.EXPLOSION, SoundSource.HOSTILE, 2.0F, 0.8F + this.random.nextFloat() * 0.4F);
		}
		sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2.0, getZ(), 3, 1.6, 1.8, 1.6, 0.05);
		sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 2.0, getZ(), 5, 1.8, 2.0, 1.8, 0.1);
		sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 2.0, getZ(), 5, 1.8, 2.0, 1.8, 0.1);
		sendParticles(ParticleTypes.ASH, getX(), getY() + 3.0, getZ(), 8, 2.0, 2.0, 2.0, 0.0);

		if (t % 2 == 0) { // dragon-style light beams rising from the dying body
			int beams = 1 + (int) (p * 6.0F);
			for (int i = 0; i < beams; i++) {
				double a = this.random.nextDouble() * Math.PI * 2.0, r = this.random.nextDouble() * 2.5;
				for (int y = 0; y < 40; y += 3) {
					sendParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + y, getZ() + Math.sin(a) * r, 1, 0.05, 0.0, 0.05, 0.0);
				}
			}
		}
		if (t % 20 == 0 && t > 60) {
			double a = this.random.nextDouble() * Math.PI * 2.0, r = 5.0 + this.random.nextDouble() * 10.0;
			visualBolt(sl, position().add(Math.cos(a) * r, 0, Math.sin(a) * r));
		}

		if (t >= DEATH_TICKS) {
			sfx(ModSounds.DEATH_FINAL, 5.0F, 0.8F);
			sfx(ModSounds.EXPLOSION, 5.0F, 0.6F);
			for (int i = 0; i < 6; i++) {
				sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + i * 1.2, getZ(), 1, 1.5, 0.5, 1.5, 0.0);
			}
			sendParticles(ParticleTypes.END_ROD, getX(), getY() + 3.0, getZ(), 300, 3.0, 4.0, 3.0, 0.5);
			sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 3.0, getZ(), 120, 3.0, 4.0, 3.0, 0.3);
			ExperienceOrb.award(sl, position(), 12000);
			announce(sl, "umbraxis.message.defeated");
			// Real vanilla death: this drops data/umbraxis/loot_table/entities/umbraxis.json
			super.hurtServer(sl, damageSources().genericKill(), Float.MAX_VALUE);
		}
	}

	// ====================================================================================
	// Helpers
	// ====================================================================================
	private void schedule(int delay, Runnable r) { this.scheduled.add(new Scheduled(Math.max(1, delay), r)); }

	private Vec3 mouth() {
		return position().add(Vec3.directionFromRotation(0, getYRot()).scale(3.4)).add(0, 3.9, 0);
	}

	private void faceTarget(LivingEntity t, float maxTurn) {
		double dx = t.getX() - getX(), dz = t.getZ() - getZ();
		float want = (float) (Mth.atan2(dz, dx) * 57.29577951308232) - 90.0F;
		float y = Mth.approachDegrees(getYRot(), want, maxTurn);
		setYRot(y);
		setYBodyRot(y);
		setYHeadRot(y);
	}

	private float dmgMult(ServerLevel sl) {
		float diff = sl.getDifficulty() == Difficulty.EASY ? 0.75F : (sl.getDifficulty() == Difficulty.HARD ? 1.25F : 1.0F);
		return diff * (1.0F + 0.15F * (this.phase - 1));
	}

	private void hit(ServerLevel sl, LivingEntity v, DamageSource src, float dmg, double knockback) {
		if (knockback > 0.0) v.knockback(knockback, getX() - v.getX(), getZ() - v.getZ());
		v.hurtServer(sl, src, dmg * dmgMult(sl));
	}

	private List<LivingEntity> victims(ServerLevel sl, Vec3 c, double radius) {
		return sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius), e ->
				e != this && e.isAlive() && !e.isSpectator() && !e.getTags().contains(TAG_MINION)
						&& !(e instanceof Player p && p.isCreative()));
	}

	private void craterAt(ServerLevel sl, double x, double y, double z, float power) {
		sl.explode(this, x, y, z, power, Level.ExplosionInteraction.MOB);
	}

	private void visualBolt(ServerLevel sl, Vec3 pos) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl, EntitySpawnReason.EVENT);
		if (bolt == null) return;
		bolt.setPos(pos.x, pos.y, pos.z);
		bolt.setVisualOnly(true);
		sl.addFreshEntity(bolt);
	}

	private void sfx(SoundEvent sound, float volume, float pitch) {
		this.level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, volume, pitch);
	}

	private void sendParticles(ParticleOptions type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		if (this.level() instanceof ServerLevel sl) sl.sendParticles(type, x, y, z, count, dx, dy, dz, speed);
	}

	private void ring(ServerLevel sl, ParticleOptions type, Vec3 c, double r, int points, double y, double speed) {
		for (int i = 0; i < points; i++) {
			double a = (Math.PI * 2.0 * i) / points;
			sl.sendParticles(type, c.x + Math.cos(a) * r, y, c.z + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, speed);
		}
	}

	private void announce(ServerLevel sl, String key) {
		for (ServerPlayer p : sl.players()) {
			if (p.distanceToSqr(this) < 96.0 * 96.0) {
				p.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
			}
		}
	}
}
