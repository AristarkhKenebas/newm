# The showcase configuration

`templates/showcase/config.kts` is newm with everything set up to the max, in its own
[Jadefire look](brand.md) — a lively starting point to copy and adjust. newm writes it to
`~/.config/newm/showcase/config.kts` the first time it starts (with Russian notes on a Russian
system). To use it, keep your own configuration and put this one in its place:

```sh
cd ~/.config/newm
mv config.kts config.kts.bak
cp showcase/config.kts config.kts
```

Saved, it applies at once. Every line has a note saying what it does and what else it can be.

![The showcase: Jadefire, the dragon-eye wallpaper, see-through terminals](../assets/desktop.png)

## What it turns on

- **`theme = "jadefire"`**: jade and amber on near-black green — the bar's islands, its menus,
  the notification cards, the lock screen and the launcher in the same colours.
- **A wallpaper that watches you**: `background { shader = "dragon-eye" }` — a breathing iris
  whose slit pupil follows the pointer and narrows when it moves fast. It moves and zooms with
  the view like a picture, is drawn 30 times a second only while it is seen (15 on battery),
  and stands still after two minutes without input.
- **A glowing focus border**: the shader `"jadefire"`, jade flowing into amber with a spark
  running round; an urgent window pulses ember.
- **Animations with curves and springs**: a Bézier curve of its own (`curve("jade", ...)`), a
  spring (`spring("ember", ...)`), windows springing into place, the view gliding, menus popping in.
- **Blur and round corners**: see-through terminals with what is behind them blurred, rounded
  windows and screen corners, windows without the focus a touch see-through.
- **A special workspace for music**: the player opens hidden above the wall; Super+M shows it
  over a dimmed, blurred wall (Super+Ctrl+M also starts it — Kotlin code in a key).
- **Screens** of two windows each, with a cycle of layouts on Super+Space.
- **A fuller bar**: the launcher's button, a small map of your wall, the window, the clock, and a
  module of its own (the processor's load; a click opens btop).
- **A start in style**: dark until newm is ready, a slow fade, a sound; programs started with
  the session (`autostart { }`).

| The overview (Logo alone) | The launcher (Super+D) |
|---|---|
| ![Every window over the eye](../assets/overview.png) | ![The launcher in Jadefire](../assets/launcher.png) |

## The wallpapers

Change `shader` to any of newm's own, or to a `.glsl` file of yours next to the configuration
(read again when saved). Each answers the pointer; `speed`, `intensity`, `shaderValues` and
`shaderColors` tune it.

![The dragon eye following the pointer](../assets/wallpapers/dragon-eye.webp)

| `dragon-eye` | `ember-sky` |
|---|---|
| ![dragon-eye](../assets/wallpapers/dragon-eye.jpg) | ![ember-sky](../assets/wallpapers/ember-sky.jpg) |
| **`scales`** — ripples from clicks | **`deep-forest`** |
| ![scales](../assets/wallpapers/scales.jpg) | ![deep-forest](../assets/wallpapers/deep-forest.jpg) |
| **`molten`** | |
| ![molten](../assets/wallpapers/molten.jpg) | |

They cost little: at 30 frames a second a 1080p screen keeps an integrated GPU (AMD Renoir)
busy for a few percent, and a screen above 2560×1600 draws them at half the pixels each way
(`resolution`) by itself. Under a fullscreen window, on the lock screen and after
`pauseAfter` seconds without input nothing is drawn for them at all.

## Make it yours

The notes in the file say what each line can be. A few first steps:

- a calmer wallpaper: `shader = "deep-forest"`; a still one: `fps = 1` or a picture (`path`);
- your own colours on top of the look: `focus { color = "rgba(3ddc97ff) rgba(f2b33dff) 45deg" }`,
  `colors { source = "wallpaper" }`;
- fewer animations: `animations { preset = "none" }`;
- the free wall instead of screens: `screens { enabled = false }`.

Every setting: [settings.md](settings.md).
