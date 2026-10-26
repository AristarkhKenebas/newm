# When something goes wrong

**English** · [Русский](../ru/troubleshooting.md)

- **A card at the top says the configuration has a mistake.** It names the file and the
  line. Fix the line and save; the card goes away. Until then newm keeps the settings that
  worked.
- **The log**: `~/.local/state/newm/newm.log`; the start before is in `newm.log.old`. For
  more detail, start newm with `CAULIS_LOG=3 newm`.
- **Stuck?** Logo+Shift+Escape logs out. From another console (Ctrl+Alt+F3), `pkill newm`
  ends it.
- **A program does not open, or closes at once.** It may be an X11 program: is
  `xorg-xwayland` installed? Start it from a terminal inside newm to see what it says.
- **Screen sharing or the file picker does not work.** Install `xdg-desktop-portal` and
  `xdg-desktop-portal-gtk` (or `-wlr`). newm gives them its environment by itself.
- **newm is not in the login screen.** Run `newm install-session`.
- **Text in the terminal is tiny.** Your terminal decides its own font size. For foot, set
  it in `~/.config/foot/foot.ini` (`font=monospace:size=11`); Ctrl+= changes the size of
  one window only. On a sharp screen, `output("eDP-1") { scale = 1.25 }` makes everything
  bigger.
- **A bar program covers windows.** It probably does not reserve its room (an exclusive
  zone). In waybar this is `"exclusive": true`, which is its default.
- **Something else?** Open an issue with the log attached:
  https://github.com/AristarkhKenebas/newm/issues
