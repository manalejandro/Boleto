#!/usr/bin/env python3
"""
Generates the legacy launcher icons (API < 26) as PNG files that match the
adaptive icon artwork: deep violet gradient, golden ball with a "7" and two
sparkles.

Usage: python3 tools/make_icons.py
"""

import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

BG_TOP = (59, 42, 140)
BG_BOTTOM = (20, 15, 56)
BALL_LIGHT = (255, 243, 196)
BALL_MID = (246, 194, 68)
BALL_DARK = (221, 138, 0)
INK = (42, 35, 80)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def diagonal_gradient(size):
    img = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        for x in range(0, size, 4):
            t = (x + y) / (2 * size)
            draw.rectangle([x, y, x + 3, y], fill=lerp(BG_TOP, BG_BOTTOM, t))
    return img


def radial_ball(size):
    ball = Image.new("RGB", (size, size), BALL_DARK)
    draw = ImageDraw.Draw(ball)
    steps = 60
    for step in range(steps, 0, -1):
        t = step / steps
        radius = t
        color = lerp(BALL_LIGHT, BALL_DARK, t ** 0.85)
        # offset centre for a glossy look (highlight towards the top-left)
        cx = 0.5 - 0.16 * (1 - t)
        cy = 0.5 - 0.12 * (1 - t)
        box = [
            (cx - radius / 2) * size,
            (cy - radius / 2) * size,
            (cx + radius / 2) * size,
            (cy + radius / 2) * size,
        ]
        draw.ellipse(box, fill=color)
    return ball


def sparkle(draw, cx, cy, r, fill):
    waist = r * 0.22
    points = [
        (cx, cy - r),
        (cx + waist, cy - waist),
        (cx + r, cy),
        (cx + waist, cy + waist),
        (cx, cy + r),
        (cx - waist, cy + waist),
        (cx - r, cy),
        (cx - waist, cy - waist),
    ]
    draw.polygon(points, fill=fill)


def make_icon(size, round_shape):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gradient = diagonal_gradient(size)

    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    if round_shape:
        mask_draw.ellipse([0, 0, size - 1, size - 1], fill=255)
    else:
        radius = int(size * 0.22)
        mask_draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    img.paste(gradient, (0, 0), mask)

    # golden ball
    ball_size = int(size * 0.62)
    ball = radial_ball(ball_size)
    ball_mask = Image.new("L", (ball_size, ball_size), 0)
    ImageDraw.Draw(ball_mask).ellipse([0, 0, ball_size - 1, ball_size - 1], fill=255)
    ball_x = int(size * 0.19)
    ball_y = int(size * 0.22)
    img.paste(ball, (ball_x, ball_y), ball_mask)

    draw = ImageDraw.Draw(img)

    # glossy highlight
    hl = int(size * 0.10)
    draw.ellipse(
        [int(size * 0.33), int(size * 0.31), int(size * 0.33) + hl, int(size * 0.31) + hl],
        fill=(255, 255, 255, 90),
    )

    # number 7
    stroke = max(2, int(size * 0.055))
    draw.line(
        [int(size * 0.42), int(size * 0.42), int(size * 0.62), int(size * 0.42)],
        fill=INK, width=stroke,
    )
    draw.line(
        [int(size * 0.60), int(size * 0.44), int(size * 0.45), int(size * 0.64)],
        fill=INK, width=stroke,
    )

    # sparkles
    sparkle(draw, int(size * 0.74), int(size * 0.30), size * 0.09, (255, 255, 255, 255))
    sparkle(draw, int(size * 0.28), int(size * 0.74), size * 0.06, (242, 183, 5, 230))
    return img


def main():
    for folder, size in DENSITIES.items():
        target = os.path.join(ROOT, folder)
        os.makedirs(target, exist_ok=True)
        make_icon(size, round_shape=False).save(os.path.join(target, "ic_launcher.png"))
        make_icon(size, round_shape=True).save(os.path.join(target, "ic_launcher_round.png"))
        print(f"{folder}: {size}x{size} written")


if __name__ == "__main__":
    main()
