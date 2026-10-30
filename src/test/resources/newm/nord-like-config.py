# A newm config.py of before, shaped as the newm_nord one people used (owl_dots): the test
# runs it instead of a real user's file. Nothing here runs a program while it loads; the
# key bindings that would only do so when pressed.
from __future__ import annotations
from typing import Callable, Any

import os
import logging

from newm.layout import Layout

from pywm import (
    PYWM_MOD_LOGO,
    PYWM_MOD_ALT,
    PYWM_TRANSFORM_90,
    PYWM_TRANSFORM_FLIPPED,
)

logger = logging.getLogger(__name__)

mod = PYWM_MOD_LOGO

background = {
    'path': "/nonexistent/wallpaper.png",
    'time_scale': 0.125,
    'anim': True,
}

outputs = [
    { 'name': 'eDP-1', 'scale': 1.0, 'width': 1920, 'height': 1080,
      'mHz': 60, 'pos_x': 0, 'pos_y': 0, 'anim': True },
]

corner_radius = 0
anim_time = 0.30
blend_time = 1.0

pywm = {
    'xkb_model': "",
    'xkb_layout': "us,ru",
    'xkb_variant': "",
    'xkb_options': "grp:alt_shift_toggle",
    'enable_xwayland': True,
    'xcursor_size': 16,
    'tap_to_click': True,
    'natural_scroll': False,
    'focus_follows_mouse': True,
    'encourage_csd': False,
}

view = {
    'padding': 10,
    'fullscreen_padding': 0,
    'send_fullscreen': False,
    'accept_fullscreen': False,
}

gesture_bindings = {
    'launcher': (None, "swipe-5"),
    'move_resize': ("L", "move-1", "swipe-2"),
    'swipe': (None, "swipe-3"),
    'swipe_to_zoom': (None, "swipe-4"),
}

energy = {
    'idle_callback': lambda event: "idle",
    'idle_times': [120, 300, 600],
    'suspend_command': "systemctl suspend",
}

terminal = 'alacritty'
menu = 'wofi --show drun'
wlogout = 'wlogout'
screenshot = 'grim'

def key_bindings(layout: Layout) -> list[tuple[str, Callable[[], Any]]]:
    return [
        ("L-n", lambda: os.system("thunar &")),
        ("L-f", lambda: os.system("firefox &")),
        ("L-d", lambda: os.system(f"{menu} &")),
        ("L-x", lambda: os.system(f"{wlogout} &")),
        ("L-Return", lambda: os.system(f"{terminal} &")),

        ("L-Left", lambda: layout.move(-1, 0)),
        ("L-Down", lambda: layout.move(0, 1)),
        ("L-Up", lambda: layout.move(0, -1)),
        ("L-Right", lambda: layout.move(1, 0)),

        ("L-s", lambda: layout.move_in_stack(1)),
        ("L-space", lambda: layout.toggle_fullscreen()),
        ("L-S-space", lambda: layout.toggle_focused_view_floating()),

        ("L-equal", lambda: layout.basic_scale(1)),
        ("L-minus", lambda: layout.basic_scale(-1)),

        ("L-S-Left", lambda: layout.move_focused_view(-1, 0)),
        ("L-S-Down", lambda: layout.move_focused_view(0, 1)),
        ("L-S-Up", lambda: layout.move_focused_view(0, -1)),
        ("L-S-Right", lambda: layout.move_focused_view(1, 0)),

        ("L-C-Left", lambda: layout.resize_focused_view(-1, 0)),
        ("L-C-Down", lambda: layout.resize_focused_view(0, 1)),
        ("L-C-Up", lambda: layout.resize_focused_view(0, -1)),
        ("L-C-Right", lambda: layout.resize_focused_view(1, 0)),

        ("L-", lambda: layout.toggle_overview(only_active_workspace=True)),
        ("L-c", lambda: layout.close_focused_view()),
        ("L-C", lambda: layout.update_config()),
        ("L-Q", lambda: layout.terminate()),
        ("C-A-Delete", lambda: layout.terminate()),
        ("C-A-l", lambda: layout.ensure_locked(dim=True)),

        # A mistake kept on purpose (as in newm_nord): brightness is not defined here.
        ("XF86MonBrightnessUp", lambda: os.system(f"{brightness} --inc &")),
        ("XF86MonBrightnessDown", lambda: os.system(f"{brightness} --dec &")),
        ("XF86AudioRaiseVolume", lambda: os.system(f"{volume} --inc &")),
        ("XF86AudioLowerVolume", lambda: os.system(f"{volume} --dec &")),
        ("XF86AudioMute", lambda: os.system(f"{volume} --toggle &")),
        ("Print", lambda: os.system(f"{screenshot} &")),
    ]
