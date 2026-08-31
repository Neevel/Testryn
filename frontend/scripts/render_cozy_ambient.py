"""Render the Cozy Studio ambient overlay as a transparent animated WebP.

The overlay shares the source artwork dimensions so CSS can layer it over the
static background without position drift. Rain is drawn only inside the four
glass panes; lantern light is animated locally around the painted flames.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "public" / "themes" / "cozy-studio-rainy.png"
OUTPUT = ROOT / "public" / "themes" / "cozy-studio-ambient-loop.webp"

FRAME_COUNT = 30
FRAME_DURATION_MS = 120
LOOP_SECONDS = FRAME_COUNT * FRAME_DURATION_MS / 1000


@dataclass(frozen=True)
class Pane:
    left: int
    top: int
    right: int
    bottom: int
    drop_count: int
    opacity: float = 1.0


@dataclass(frozen=True)
class Drop:
    pane: Pane
    x: float
    start: float
    cycles: int
    length: float
    alpha: int
    width: int
    drift: float


PANES = (
    Pane(0, 0, 195, 223, 30),
    Pane(0, 261, 195, 589, 42),
    Pane(218, 0, 284, 258, 12, 0.58),
    Pane(218, 289, 284, 584, 14, 0.52),
)


def build_drops() -> list[Drop]:
    rng = random.Random(8426)
    drops: list[Drop] = []
    for pane in PANES:
        for _ in range(pane.drop_count):
            drops.append(
                Drop(
                    pane=pane,
                    x=rng.uniform(pane.left + 3, pane.right - 3),
                    start=rng.random(),
                    cycles=rng.choice((1, 1, 1, 2)),
                    length=rng.uniform(5, 19),
                    alpha=int(rng.uniform(28, 84) * pane.opacity),
                    width=rng.choice((1, 1, 1, 2)),
                    drift=rng.uniform(-2.4, 1.2),
                )
            )
    return drops


def add_rain(frame: Image.Image, drops: list[Drop], phase: float) -> None:
    rain = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(rain)
    for drop in drops:
        pane_height = drop.pane.bottom - drop.pane.top
        progress = (drop.start + drop.cycles * phase) % 1.0
        y = drop.pane.top + progress * pane_height
        x = drop.x + drop.drift * math.sin(math.tau * phase)
        length = min(drop.length, drop.pane.bottom - y)
        if length <= 1:
            continue
        draw.line(
            (x, y, x - 1.8, y + length),
            fill=(176, 215, 238, drop.alpha),
            width=drop.width,
        )
        if drop.width == 2:
            draw.ellipse(
                (x - 1.2, y + length - 1.2, x + 1.2, y + length + 1.2),
                fill=(201, 229, 244, min(drop.alpha + 12, 105)),
            )
    rain = rain.filter(ImageFilter.GaussianBlur(0.32))
    frame.alpha_composite(rain)


def add_lantern(frame: Image.Image, center: tuple[int, int], phase: float, offset: float, strength: float) -> None:
    x, y = center
    flicker = (
        0.72
        + 0.17 * math.sin(math.tau * phase + offset)
        + 0.07 * math.sin(math.tau * 3 * phase + offset * 1.7)
        + 0.04 * math.sin(math.tau * 7 * phase + offset * 0.4)
    )
    flicker = max(0.5, min(1.0, flicker))

    glow = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse(
        (x - 17, y - 18, x + 17, y + 18),
        fill=(255, 137, 35, int(105 * flicker * strength)),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(42 * strength))
    frame.alpha_composite(glow)

    flame = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    flame_draw = ImageDraw.Draw(flame)
    flame_height = 7.0 + 5.5 * flicker * strength
    lean = 1.3 * math.sin(math.tau * 2 * phase + offset)
    flame_draw.polygon(
        (
            (x - 3, y + 5),
            (x - 2 + lean, y - flame_height * 0.42),
            (x + lean, y - flame_height),
            (x + 3 + lean, y - flame_height * 0.3),
            (x + 3, y + 5),
        ),
        fill=(255, 179, 65, int(205 * flicker)),
    )
    flame_draw.ellipse(
        (x - 1.7, y - flame_height * 0.42, x + 1.7, y + 3),
        fill=(255, 245, 190, int(225 * flicker)),
    )
    flame = flame.filter(ImageFilter.GaussianBlur(0.55))
    frame.alpha_composite(flame)


def render() -> None:
    with Image.open(SOURCE) as source:
        size = source.size

    drops = build_drops()
    frames: list[Image.Image] = []
    for index in range(FRAME_COUNT):
        phase = index / FRAME_COUNT
        frame = Image.new("RGBA", size, (0, 0, 0, 0))
        add_rain(frame, drops, phase)
        add_lantern(frame, (320, 566), phase, 0.35, 1.0)
        add_lantern(frame, (1502, 359), phase, 2.1, 0.62)
        frames.append(frame)

    frames[0].save(
        OUTPUT,
        save_all=True,
        append_images=frames[1:],
        duration=FRAME_DURATION_MS,
        loop=0,
        format="WEBP",
        lossless=False,
        quality=80,
        method=3,
        minimize_size=True,
    )
    print(f"Rendered {OUTPUT} ({LOOP_SECONDS:.1f}s loop, {FRAME_COUNT} frames)")


if __name__ == "__main__":
    render()
