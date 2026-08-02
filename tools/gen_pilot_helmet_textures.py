#!/usr/bin/env python3
"""Generate pilot helmet textures: item icon (16x16) and armor layer UV (64x32).

Run:  python tools/gen_pilot_helmet_textures.py
"""

import os
from PIL import Image, ImageDraw, ImageOps

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "aero_reformation", "textures", "item")
EQUIP_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                         "aero_reformation", "textures", "models", "equipment")

# Palette (steampunk leather + copper goggles)
LEATHER   = (66, 41, 24, 255)   # main leather
LEATHER_D = (46, 28, 16, 255)   # shadow leather
LEATHER_H = (100, 66, 40, 255)  # highlight leather
COPPER    = (198, 127, 47, 255)
COPPER_D  = (142, 87, 31, 255)
COPPER_H  = (240, 180, 110, 255)
LENS      = (46, 96, 100, 255)  # goggle lens
LENS_H    = (122, 178, 176, 255)
LENS_D    = (28, 60, 64, 255)
DARK      = (24, 16, 10, 255)

SS = 8  # supersampling factor


def _face(painter, size=8, mirror=False):
    """Render one 8x8 face with SS supersampling."""
    px = size * SS
    img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    painter(ImageDraw.Draw(img), px)
    if mirror:
        img = ImageOps.mirror(img)
    return img


def paint_top(d, s):
    d.rectangle([0, 0, s, s], fill=LEATHER)
    w = max(1, int(s * 0.05))
    d.rectangle([0, 0, s, w], fill=LEATHER_D)          # rim shadow
    d.rectangle([0, 0, w, s], fill=LEATHER_D)
    d.rectangle([s - w, 0, s, s], fill=LEATHER_D)
    d.rectangle([s // 2 - w // 2, 0, s // 2 + w // 2, s], fill=LEATHER_D)  # seam
    for cx, cy in ((0.25, 0.28), (0.75, 0.28), (0.25, 0.72), (0.75, 0.72)):
        r = int(s * 0.085)
        d.ellipse([cx * s - r, cy * s - r, cx * s + r, cy * s + r], fill=COPPER)
        d.ellipse([cx * s - r, cy * s - r, cx * s - r * 0.4, cy * s - r * 0.4],
                  fill=COPPER_H)


def paint_bottom(d, s):
    d.rectangle([0, 0, s, s], fill=DARK)


def paint_front(d, s):
    d.rectangle([0, 0, s, s], fill=LEATHER)
    # forehead copper band
    y0 = int(s * 0.16)
    bh = int(s * 0.13)
    d.rectangle([0, y0, s, y0 + bh], fill=COPPER_D)
    d.rectangle([0, y0, s, y0 + bh // 2], fill=COPPER)
    # goggles: copper frame + dark lens + highlight
    gy0, gy1 = int(s * 0.32), int(s * 0.80)
    d.rounded_rectangle([int(s * 0.06), gy0, int(s * 0.94), gy1],
                        radius=int(s * 0.14), fill=COPPER_D)
    d.rounded_rectangle([int(s * 0.13), gy0 + int(s * 0.05), int(s * 0.87), gy1 - int(s * 0.05)],
                        radius=int(s * 0.11), fill=LENS_D)
    d.rounded_rectangle([int(s * 0.18), gy0 + int(s * 0.09), int(s * 0.82), gy1 - int(s * 0.09)],
                        radius=int(s * 0.09), fill=LENS)
    d.rounded_rectangle([int(s * 0.24), gy0 + int(s * 0.13), int(s * 0.72), gy1 - int(s * 0.20)],
                        radius=int(s * 0.07), fill=LENS_H)
    # chin / lower leather
    d.rectangle([0, int(s * 0.86), s, s], fill=LEATHER_D)


def paint_side(d, s):
    d.rectangle([0, 0, s, s], fill=LEATHER)
    # vertical rivet strap
    sx = int(s * 0.28)
    w = max(1, int(s * 0.06))
    d.rectangle([sx - w // 2, int(s * 0.10), sx + w // 2, int(s * 0.90)], fill=LEATHER_D)
    for y in (0.28, 0.5, 0.72):
        r = int(s * 0.08)
        d.ellipse([sx - r, y * s - r, sx + r, y * s + r], fill=COPPER)
        d.ellipse([sx - r, y * s - r, sx - r * 0.4, y * s - r * 0.4], fill=COPPER_H)
    # goggle side ring (strap attachment)
    gx = int(s * 0.74)
    gr = int(s * 0.17)
    d.ellipse([gx - gr, int(s * 0.42), gx + gr, int(s * 0.42) + 2 * gr], fill=COPPER_D)
    d.ellipse([gx - int(gr * 0.55), int(s * 0.46), gx + int(gr * 0.55),
               int(s * 0.46) + int(2 * gr * 0.55)], fill=DARK)


def paint_back(d, s):
    d.rectangle([0, 0, s, s], fill=LEATHER)
    # horizontal adjustment strap
    y = int(s * 0.44)
    h = int(s * 0.10)
    d.rectangle([0, y - h // 2, s, y + h // 2], fill=LEATHER_D)
    # copper buckle
    bw = int(s * 0.30)
    bh = int(s * 0.26)
    d.rectangle([s // 2 - bw // 2, y - bh // 2, s // 2 + bw // 2, y + bh // 2], fill=COPPER_D)
    d.rectangle([s // 2 - bw // 2 + int(s * 0.03), y - bh // 2 + int(s * 0.03),
                 s // 2 + bw // 2 - int(s * 0.03), y + bh // 2 - int(s * 0.03)], fill=DARK)


def build_layer_1():
    """64x32 armor layer texture; helmet box faces (8x8 each)."""
    W, H = 64 * SS, 32 * SS
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    faces = {
        "left":   (0, 0),
        "back":   (1, 0),
        "top":    (4, 0),
        "bottom": (5, 0),
        "right":  (6, 0),
        "front":  (7, 0),
    }
    painters = {
        "left":   lambda d, s: paint_side(d, s),
        "back":   paint_back,
        "top":    paint_top,
        "bottom": paint_bottom,
        "right":  lambda d, s: paint_side(d, s),
        "front":  paint_front,
    }
    for name, (bx, by) in faces.items():
        mirror = name == "left"
        face_img = _face(painters[name], mirror=mirror)
        canvas.paste(face_img, (bx * 8 * SS, by * 8 * SS))
    return canvas.resize((64, 32), Image.LANCZOS)


def build_item():
    """16x16 item icon: front view of the pilot helmet."""
    s = 16 * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # leather dome
    d.ellipse([int(0.18 * s), int(0.14 * s), int(0.82 * s), int(0.80 * s)], fill=LEATHER)
    # ear flaps
    for x0 in (int(0.06 * s), int(0.74 * s)):
        d.rounded_rectangle([x0, int(0.46 * s), x0 + int(0.20 * s), int(0.86 * s)],
                            radius=int(0.07 * s), fill=LEATHER_D)
    # forehead copper band
    d.rectangle([int(0.20 * s), int(0.30 * s), int(0.80 * s), int(0.40 * s)], fill=COPPER)
    d.rectangle([int(0.20 * s), int(0.30 * s), int(0.80 * s), int(0.335 * s)], fill=COPPER_H)
    # goggles: two lenses (copper frame + dark lens + highlight)
    for cx in (0.35, 0.65):
        r = int(0.17 * s)
        cy = int(0.55 * s)
        d.ellipse([int(cx * s) - r, cy - r, int(cx * s) + r, cy + r], fill=COPPER_D)
        d.ellipse([int(cx * s) - r + int(0.035 * s), cy - r + int(0.035 * s),
                   int(cx * s) + r - int(0.035 * s), cy + r - int(0.035 * s)], fill=LENS)
        d.ellipse([int(cx * s) - r + int(0.08 * s), cy - r + int(0.06 * s),
                   int(cx * s) - int(0.02 * s), cy - int(0.01 * s)], fill=LENS_H)
    # bridge between lenses
    d.rectangle([int(0.46 * s), int(0.50 * s), int(0.54 * s), int(0.60 * s)], fill=COPPER)
    # lower leather + shadow under goggles
    d.rounded_rectangle([int(0.18 * s), int(0.64 * s), int(0.82 * s), int(0.86 * s)],
                        radius=int(0.09 * s), fill=LEATHER)
    d.rectangle([int(0.18 * s), int(0.64 * s), int(0.82 * s), int(0.68 * s)], fill=LEATHER_D)
    return img.resize((16, 16), Image.LANCZOS)


def main():
    os.makedirs(ITEM_DIR, exist_ok=True)
    os.makedirs(EQUIP_DIR, exist_ok=True)
    build_item().save(os.path.join(ITEM_DIR, "pilot_helmet.png"))
    build_layer_1().save(os.path.join(EQUIP_DIR, "pilot_helmet_layer_1.png"))
    print("Saved textures: item/pilot_helmet.png, models/equipment/pilot_helmet_layer_1.png")


if __name__ == "__main__":
    main()
