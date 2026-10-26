# pywm's names a newm config.py imports (pywm itself is not used: newm on Caulis draws with Caulis).
PYWM_MOD_SHIFT = 1
PYWM_MOD_CAPS = 2
PYWM_MOD_CTRL = 4
PYWM_MOD_ALT = 8
PYWM_MOD_MOD2 = 16
PYWM_MOD_MOD3 = 32
PYWM_MOD_LOGO = 64
PYWM_MOD_MOD5 = 128

PYWM_RELEASED = 0
PYWM_PRESSED = 1

PYWM_TRANSFORM_NORMAL = 0
PYWM_TRANSFORM_90 = 1
PYWM_TRANSFORM_180 = 2
PYWM_TRANSFORM_270 = 3
PYWM_TRANSFORM_FLIPPED = 4
PYWM_TRANSFORM_FLIPPED_90 = 5
PYWM_TRANSFORM_FLIPPED_180 = 6
PYWM_TRANSFORM_FLIPPED_270 = 7


class PyWMModifiers:
    def __init__(self, bitmask: int = 0) -> None:
        self.shift = bool(bitmask & PYWM_MOD_SHIFT)
        self.ctrl = bool(bitmask & PYWM_MOD_CTRL)
        self.alt = bool(bitmask & PYWM_MOD_ALT)
        self.logo = bool(bitmask & PYWM_MOD_LOGO)
        self.bitmask = bitmask

    def has(self, mod) -> bool:
        if mod is None:
            return True
        return {"S": self.shift, "C": self.ctrl, "A": self.alt, "L": self.logo}.get(mod, False)
