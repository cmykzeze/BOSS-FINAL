# Umbraxis, the Decayed Wyrm — Fabric boss mod for Minecraft 26.3

Java 25 · Fabric Loader ≥0.19 · Fabric API 0.162.0+26.3 · Loom 1.17 (non-remapping, no mappings line)

## Run it (no local setup)
1. Create a GitHub repo, upload everything in this folder (keep `.github/workflows/main.yml`), push to `main`.
2. Actions tab → **Build & smoke-test Umbraxis**. Job `build` compiles; job `smoke-test` boots a real 26.3 server and fails if registries/data are broken.
3. Download the `umbraxis-mod` artifact → put the jar in `mods/` with Fabric API 0.162.0+26.3.
4. If a job fails, download the `build-log` / `server-log` artifact and send it to me.

Locally: `gradle build` (Gradle 9.6+, JDK 25), or `gradle runClient`. (No `gradlew` is included — run `gradle wrapper --gradle-version 9.6.0` once if you want one.)

## Summoning
Shapeless craft (any order): Beacon, Dragon Egg, 2× Netherite Block, Dragon Head, Dragon's Breath bottle, Elytra, Golden Apple, Totem of Undying → **Sigil of Umbraxis**. Right-click the ground. Test shortcut: `/summon umbraxis:umbraxis`, `/give @s umbraxis:sigil_of_umbraxis`, `/recipe give @s umbraxis:sigil_of_umbraxis`. Don't test on Peaceful.

## Fight overview
- **10,000 HP** pool (vanilla caps at 1024, so it's tracked internally), boss bar, 400 max damage per hit, immune to explosions/fall/fire.
- **Phases** at 66% and 33%: each is invulnerable for 4.5 s while it roars, shatters ground, strikes lightning and summons minions; faster, more attacks, bigger numbers; bar turns purple → pink → red.
- **Spawn:** Wither-size blast, 2 s Blindness+Darkness for players within 48 blocks, rises from the ground for 7.5 s with lightning. Players within 18 blocks get 5 s Resistance IV so the summoner survives.
- **Attacks (all telegraphed and dodgeable):** Stomp shockwave rings (jump them; 1/2/3 rings) · Lightning (leave marked circles; some re-aim) · Dragon Beam (locked then sweeps; sidestep) · Warden Sonic Boom (break line after the lock; 1–3 booms) · Roar + minion summon · Meteors (P2+) · Leap Slam (P2+, ring shows landing) · Rot Eruption geysers (P2+) · Claw bite (close range). Ground-breaking attacks use mob-explosions (respect mobGriefing).
- **Decay aura:** from phase 2, Wither on anyone close. Red/black aura particles always.
- **Minions** (tagged, capped 8/12/16): Rotted Husk 160 HP, Hollow Knight 220 HP, Wraith (Vex) 90 HP, Cinder Wraith (Blaze) 180 HP — far above vanilla HP/damage. They vanish when the boss starts dying.
- **Death:** 13-second sequence — scream, explosions, souls and ash, rising light beams, lightning; the body sinks and shrinks to nothing, final blast, 12,000 XP, then loot.
- **Loot:** Fang of Umbraxis (sword, enchanted), 2–3 Hearts of Umbraxis (right-click: +20 max HP, absorption, regen), 12–20 Umbral Scales, 3–4 Nether Stars, 12–24 Netherite Ingots, 3–5 Enchanted Golden Apples, a maxed Elytra, plus 2 random maxed netherite armor/tools. Custom enchantments (not in the table, loot only): **Rotbite** (Wither on hit), **Wyrm Might** (+damage), **Wyrmhide** (+HP/toughness).

## Honest notes
- Sounds are custom events (`umbraxis:boss.*`) that map to Warden / Ender Dragon / Wither audio and are layered in code; no new audio files. Textures and the model are procedurally generated (`tools/generate_assets.py`, rerun to tweak); nothing was downloaded.
- The death "fade" is shrink + sink into ash and light, not the dragon's dissolve shader.
- Eyes are painted bright, not truly emissive.
- Tuning constants are at the top of `UmbraxisBoss.java`.
- Written against 26.3 APIs from documentation but not compiled here (no network in my sandbox): the first CI run may report a compile error or two. Send me the log.
