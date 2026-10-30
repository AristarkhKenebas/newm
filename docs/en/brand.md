# The Jadefire look

newm's own look: a glowing reptile's eye in the dark — deep emerald melting into jade, gold-amber
round the slit, a rim of ember fire, all of it on near-black green. One line turns it on:

```kotlin
theme = "jadefire"
```

It sets, wherever your configuration says nothing itself:

- **the colours** of the bar, its menus, the notification cards, the compositor's notices and the
  lock screen — dark and light (`colors { source = "jadefire" }` alone sets only these; the
  colours menu offers it too);
- **the focus border**: the shader `"jadefire"` — jade flowing into amber along the border, a
  spark going round, an urgent window pulsing ember (`focus { shader = "jadefire" }`);
- **the wallpaper**: `"dragon-eye"`, a breathing iris whose slit pupil follows the pointer
  (`background { shader = "dragon-eye" }`). A picture of your own (`background { path }`) keeps
  its place.

What you set yourself always wins: `theme = "jadefire"` and `focus { width = 2.0 }` is Jadefire
with a thinner border.

## The colours

| Token | Dark | | Token | Light |
|---|---|---|---|---|
| `abyss` | `#060908` | the deepest dark, behind everything | `paper` | `#f2f5ef` |
| `night` | `#0b110e` | backgrounds | `mist` | `#e3ebe4` |
| `basalt` | `#111a15` | surfaces: islands, cards, menus | `stone` | `#cbd8cf` |
| `moss` | `#18241d` | raised surfaces | | |
| `slate` | `#22322a` | lines and edges | | |
| `emerald_deep` | `#0b5d3b` | the iris's rim | `emerald_deep` | `#0b5d3b` |
| `emerald` | `#13995f` | | `emerald` | `#0e8a57` |
| `jade` | `#3ddc97` | **the accent** | `jade` | `#2fb37a` |
| `jade_pale` | `#a6f4cf` | | | |
| `amber` | `#f2b33d` | **the highlight**, warnings | `amber` | `#b7791f` |
| `gold` | `#ffd772` | the glow round the slit | | |
| `ember` | `#e8552d` | **alerts**, urgent windows | `ember` | `#c2410c` |
| `ember_deep` | `#b3261e` | errors | | |
| `bone` | `#e8f0ea` | text | `ink` | `#10201a` |
| `lichen` | `#a3b5aa` | quieter text | `lichen` | `#4a5d52` |
| `fog` | `#66786e` | hints | | |

With Jadefire on, newm writes them to `~/.cache/newm/colors.json` and `colors.css` (and the
`-light` ones) — `@define-color jade #3ddc97;` — for waybar, GTK and your scripts; also by role:
`background`, `surface`, `text`, `accent`, `highlight`, `alert`.

Programs that go well with it: a terminal with these colours (foot: `background=0b110e`,
`foreground=e8f0ea`, `regular2=3ddc97`, `regular3=f2b33d`, `regular1=e8552d`), and a cursor theme
such as Bibata Modern Classic (`cursor { theme = "Bibata-Modern-Classic"; size = 24 }`).

## The wallpapers

newm's moving wallpapers are drawn in these colours (each takes `shaderColors` of your own too):

| Name | What |
|---|---|
| `dragon-eye` | a breathing reptile iris; the slit pupil follows the pointer and narrows when it moves fast; blinks now and then |
| `ember-sky` | jade and amber curtains of light over a night sky; embers rise and gather round the pointer |
| `scales` | iridescent dragon scales in the dark; ripples run out from each click |
| `deep-forest` | a calm, misty dark green; glowing spores drift along slow currents and step aside for the pointer |
| `molten` | slow lava-lamp blobs of amber and ember with a jade glow; the pointer is a warm blob of its own |

See [every setting](settings.md#background--) for their settings.
