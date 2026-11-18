#!/usr/bin/env python3
"""Generates the boss texture, item textures and the boss model geometry (Java).
Run from the repo root:  python3 tools/generate_assets.py   (needs: pip install pillow)
The boss model boxes are defined ONCE here, so texture UVs and Java geometry always match."""
import math, os, random
from PIL import Image

random.seed(1337)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_W = TEX_H = 256

# (name, parent, pivot(x,y,z) [absolute model coords for top-level parts, parent-relative otherwise],
#  rotation (x,y,z), [boxes: (material, x,y,z, w,h,d)])
# Ground is y=24, the front of the creature is -z.
P = []
def part(name, parent, pivot, rot, boxes): P.append((name, parent, pivot, rot, boxes))

part("body", "base", (0, 0, 0), (0, 0, 0), [
    ("scale", -9, -8, -14, 18, 18, 28),
    ("bone", -1, -13, -10, 2, 5, 3), ("bone", -1, -14, -4, 2, 6, 3),
    ("bone", -1, -14, 2, 2, 6, 3), ("bone", -1, -13, 8, 2, 5, 3),
    ("bone", -12, -9, -12, 4, 6, 10), ("bone", 8, -9, -12, 4, 6, 10),
    ("rot", -9.5, -2, -6, 1, 8, 12), ("rot", 8.5, -2, -6, 1, 8, 12),
])
for nm, x, z in (("legFL", -8, -9), ("legFR", 8, -9), ("legBL", -8, 9), ("legBR", 8, 9)):
    part(nm, "base", (x, 10, z), (0, 0, 0), [
        ("scale", -3, 0, -3, 6, 12, 6), ("bone", -3.5, 12, -7, 7, 2, 10),
        ("bone", -3.5, 12, -9, 2, 2, 2), ("bone", -1, 12, -9, 2, 2, 2), ("bone", 1.5, 12, -9, 2, 2, 2)])
part("neck1", "body", (0, -3, -13), (-0.55, 0, 0), [("scale", -4, -4, -10, 8, 8, 10), ("bone", -1, -7, -8, 2, 3, 3)])
part("neck2", "neck1", (0, 0, -10), (-0.35, 0, 0), [("rot", -3.5, -3.5, -9, 7, 7, 9), ("bone", -1, -6.5, -6, 2, 3, 3)])
part("head", "neck2", (0, 0, -9), (0.75, 0, 0), [
    ("head", -5, -4, -14, 10, 5, 14), ("bone", -6, -6, -6, 12, 2, 6), ("scale", -3, -3, -16, 6, 3, 2),
    ("bone", -4.5, 1, -13, 1, 2, 1), ("bone", -4.5, 1, -8, 1, 2, 1),
    ("bone", 3.5, 1, -13, 1, 2, 1), ("bone", 3.5, 1, -8, 1, 2, 1)])
part("jaw", "head", (0, 1, -2), (0, 0, 0), [
    ("scale", -4.5, 0, -12, 9, 2, 12), ("bone", -4, -2, -12, 1, 2, 1), ("bone", 3, -2, -12, 1, 2, 1),
    ("bone", -4, -2, -7, 1, 2, 1), ("bone", 3, -2, -7, 1, 2, 1)])
part("hornL", "head", (-4, -5, -2), (0.5, -0.35, 0), [("bone", -1.5, -1.5, 0, 3, 3, 10)])
part("hornR", "head", (4, -5, -2), (0.5, 0.35, 0), [("bone", -1.5, -1.5, 0, 3, 3, 10)])
part("tail1", "body", (0, -2, 13), (0, 0, 0), [("scale", -5, -4, 0, 10, 8, 12), ("bone", -1, -7, 3, 2, 3, 3)])
part("tail2", "tail1", (0, 0, 12), (0, 0, 0), [("scale", -4, -3, 0, 8, 6, 12), ("bone", -1, -6, 3, 2, 3, 3)])
part("tail3", "tail2", (0, 0, 12), (0, 0, 0), [("rot", -3, -2.5, 0, 6, 5, 12)])
part("tail4", "tail3", (0, 0, 12), (0, 0, 0), [("scale", -1.5, -1.5, 0, 3, 3, 12), ("bone", -1, -5, 3, 2, 10, 8)])
for side, s in (("R", 1), ("L", -1)):
    def X(x, w, s=s): return x if s == 1 else -x - w
    part("wing" + side, "body", (7 * s, -6, -7), (0, 0, -0.9 * s), [
        ("bone", X(0, 14), -2, -2, 14, 4, 4),
        ("membrane", X(1, 4), 0, 2, 4, 1, 14), ("membrane", X(6, 4), 0, 2, 4, 1, 10), ("membrane", X(11, 3), 0, 2, 3, 1, 12),
        ("bone", X(4, 1), -0.5, 2, 1, 1, 16), ("bone", X(9, 1), -0.5, 2, 1, 1, 12)])
    part("wing" + side + "Tip", "wing" + side, (14 * s, 0, 0), (0, 0, 0.5 * s), [
        ("bone", X(0, 16), -1.5, -1.5, 16, 3, 3),
        ("membrane", X(1, 5), 0, 2, 5, 1, 12), ("membrane", X(7, 5), 0, 2, 5, 1, 8), ("membrane", X(12, 4), 0, 2, 4, 1, 10),
        ("bone", X(16, 3), -1, -1, 3, 2, 2)])

# ---------- UV packing ----------
rects = []
for pi, p in enumerate(P):
    for bi, b in enumerate(p[4]):
        mat, x, y, z, w, h, d = b
        assert all(float(v).is_integer() for v in (w, h, d))
        rects.append((pi, bi, int(2 * d + 2 * w), int(d + h)))
rects.sort(key=lambda r: -r[3])
pos = {}; cx = cy = 0; rowh = 0
for pi, bi, rw, rh in rects:
    if cx + rw > TEX_W: cx = 0; cy += rowh; rowh = 0
    pos[(pi, bi)] = (cx, cy); cx += rw; rowh = max(rowh, rh)
assert cy + rowh <= TEX_H, "texture atlas overflow"

# ---------- painting ----------
img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
px = img.load()
def clamp(c): return tuple(max(0, min(255, int(v))) for v in c)
def mix(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
PAL = {
    "scale": ((34, 27, 40), (66, 52, 78)), "bone": ((205, 196, 172), (128, 114, 96)),
    "rot": ((30, 38, 30), (70, 22, 28)), "membrane": ((26, 16, 30), (96, 22, 40)), "head": ((30, 24, 36), (70, 56, 82)),
}
def paint_face(rect, mat, seed):
    x0, y0, w, h = rect
    r = random.Random(seed); a, b = PAL[mat]
    for yy in range(y0, y0 + h):
        for xx in range(x0, x0 + w):
            n = r.random()
            c = mix(a, b, n * 0.55)
            if mat == "bone" and r.random() < 0.12: c = mix(c, (60, 45, 38), 0.7)   # stains
            if mat == "rot" and r.random() < 0.10: c = (150, 28, 34)                 # glowing rot
            if mat == "membrane" and r.random() < 0.07: c = (150, 30, 52)           # veins
            if xx in (x0, x0 + w - 1) or yy in (y0, y0 + h - 1): c = mix(c, (8, 5, 10), 0.35)
            px[xx, yy] = clamp(c) + (255,)
    if mat in ("scale", "head") and w > 3 and h > 3:                                 # glowing cracks
        for _ in range(max(1, (w * h) // 90)):
            cx_, cy_ = r.randrange(x0, x0 + w), r.randrange(y0, y0 + h)
            for _ in range(r.randrange(3, 9)):
                px[cx_, cy_] = (205, 34, 34, 255)
                for ddx, ddy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    gx, gy = cx_ + ddx, cy_ + ddy
                    if x0 <= gx < x0 + w and y0 <= gy < y0 + h and px[gx, gy][0] < 120:
                        px[gx, gy] = (95, 22, 28, 255)
                cx_ = max(x0, min(x0 + w - 1, cx_ + r.choice((-1, 0, 1)))); cy_ = max(y0, min(y0 + h - 1, cy_ + r.choice((0, 1))))
seed = 0
for (pi, bi), (u, v) in pos.items():
    mat, x, y, z, w, h, d = P[pi][4][bi]; w, h, d = int(w), int(h), int(d)
    faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
             "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}
    for f_, rc in faces.items():
        seed += 1; paint_face(rc, mat, seed)
    if mat == "head" and w == 10:  # burning eyes (next to the front-adjacent edge of each side face)
        for fx in (faces["right"][0] + d - 5, faces["left"][0] + 2):
            for ex in range(3):
                for ey in range(2):
                    px[fx + ex, v + d + 1 + ey] = (255, 70 + ey * 120, 20, 255)
        for ex in (u + d + 2, u + d + w - 4):
            for ey in range(2):
                for exx in range(2): px[ex + exx, v + 5 + ey] = (255, 60, 20, 255)
img.save(f"{ROOT}/src/main/resources/assets/umbraxis/textures/entity/umbraxis.png")

# ---------- Java geometry ----------
def f(v): return "%sF" % ("%g" % v)
lines = ["// GENERATED by tools/generate_assets.py - do not edit by hand.", "package com.umbraxis.client;", "",
         "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
         "import net.minecraft.client.model.geom.builders.LayerDefinition;",
         "import net.minecraft.client.model.geom.builders.MeshDefinition;",
         "import net.minecraft.client.model.geom.builders.PartDefinition;",
         "import net.minecraft.client.model.geom.PartPose;", "",
         "public final class UmbraxisGeometry {",
         "    private UmbraxisGeometry() {}", "",
         "    public static LayerDefinition createLayer() {",
         "        MeshDefinition mesh = new MeshDefinition();",
         "        PartDefinition root = mesh.getRoot();",
         "        PartDefinition base = root.addOrReplaceChild(\"base\", CubeListBuilder.create(), PartPose.offset(0F, 24F, 0F));"]
for pi, (name, parent, pv, rot, boxes) in enumerate(P):
    b = "CubeListBuilder.create()"
    for bi, (mat, x, y, z, w, h, d) in enumerate(boxes):
        u, v = pos[(pi, bi)]
        b += f"\n                .texOffs({u}, {v}).addBox({f(x)}, {f(y)}, {f(z)}, {f(w)}, {f(h)}, {f(d)})"
    py = pv[1] - 24 if parent == "base" else pv[1]
    pose = f"PartPose.offsetAndRotation({f(pv[0])}, {f(py)}, {f(pv[2])}, {f(rot[0])}, {f(rot[1])}, {f(rot[2])})"
    lines.append(f"        PartDefinition {name} = {parent}.addOrReplaceChild(\"{name}\",\n                {b},\n                {pose});")
lines += ["        return LayerDefinition.create(mesh, %d, %d);" % (TEX_W, TEX_H), "    }", "}", ""]
open(f"{ROOT}/src/client/java/com/umbraxis/client/UmbraxisGeometry.java", "w").write("\n".join(lines))

# ---------- item textures ----------
def new16(): return Image.new("RGBA", (16, 16), (0, 0, 0, 0))
def save16(im, n): im.save(f"{ROOT}/src/main/resources/assets/umbraxis/textures/item/{n}.png")
im = new16(); p = im.load()                       # Sigil
for y in range(16):
    for x in range(16):
        dd = math.hypot(x - 7.5, y - 7.5)
        if dd <= 7.2: p[x, y] = (28, 16, 38, 255)
        if 6.0 <= dd <= 7.2: p[x, y] = (150, 20, 32, 255)
for i in range(3, 13):
    p[i, 7] = (200, 30, 40, 255); p[7, i] = (200, 30, 40, 255)
for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)): p[7 + dx, 7 + dy] = (255, 150, 30, 255)
for a, b in ((4, 4), (11, 4), (4, 11), (11, 11)): p[a, b] = (120, 20, 90, 255)
save16(im, "sigil_of_umbraxis")
im = new16(); p = im.load()                       # Fang (sword)
for x in range(6, 15): p[x, 15 - x] = (80, 56, 104, 255)
for x in range(6, 14): p[x, 14 - x] = (210, 42, 52, 255); p[x + 1, 15 - x] = (36, 22, 48, 255)
p[15, 0] = (240, 120, 120, 255)
for t in (-2, -1, 0, 1, 2): p[5 + t, 10 + t] = (190, 150, 40, 255)
for x, y in ((4, 11), (3, 12), (2, 13)): p[x, y] = (205, 196, 172, 255)
p[1, 14] = (140, 30, 40, 255)
save16(im, "fang_of_umbraxis")
im = new16(); p = im.load()                       # Heart
for y in range(16):
    for x in range(16):
        X = (x - 7.5) / 6.2; Y = -(y - 7.0) / 6.2
        if (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3 <= 0:
            p[x, y] = clamp(mix((200, 28, 44), (60, 10, 40), y / 15)) + (255,)
            if (x + y) % 5 == 0: p[x, y] = (30, 10, 36, 255)
p[5, 4] = (255, 160, 170, 255); p[6, 3] = (255, 160, 170, 255)
save16(im, "heart_of_umbraxis")
im = new16(); p = im.load()                       # Scale
for y in range(16):
    for x in range(16):
        dd = abs(x - 7.5) * 0.9 + abs(y - 7.5) * 0.7
        if dd < 6.3: p[x, y] = clamp(mix((70, 52, 92), (24, 16, 34), dd / 6.3)) + (255,)
        if 5.2 < dd < 6.3: p[x, y] = (170, 30, 44, 255)
for x, y in ((7, 6), (8, 7), (7, 8), (8, 9)): p[x, y] = (210, 50, 60, 255)
save16(im, "umbral_scale")
print("OK - texture atlas used up to y =", cy + rowh, "of", TEX_H)
