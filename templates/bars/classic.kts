// newm's classic bar: the one of the original newm (as on its README's picture).
// Paste it into ~/.config/newm/config.kts in place of the panels { topBar { ... } } there.
panels {
    topBar {
        enabled = true
        height = 28
        fontSize = 12
        background = "#ECEFF4B0"            // see-through: the wallpaper shows, blurred
        foreground = "#2E3440"
        blur = true
        texts { listOf(wifi(), cpu(), memory(), temperature(), battery(), date(), time()) }
    }
    bottomBar {
        enabled = true
        height = 28
        fontSize = 12
        background = "#ECEFF4B0"
        foreground = "#2E3440"
        blur = true
        texts { listOf(user(), "newm") }
    }
}
