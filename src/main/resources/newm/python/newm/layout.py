# newm's Layout as a config.py uses it: every method (layout.move(-1, 0), layout.toggle_overview(...))
# is newm on Caulis's, called over the config host's channel.
from __future__ import annotations
from typing import Any


class Layout:
    def __getattr__(self, name: str) -> Any:
        if name.startswith("_"):
            raise AttributeError(name)

        def method(*args: Any, **kwargs: Any) -> Any:
            import host
            return host.request("layout", method=name, args=host.encode(list(args)), kwargs=host.encode(kwargs))

        method.__name__ = name
        return method

    def __repr__(self) -> str:
        return "<Layout (newm on Caulis)>"


the_layout = Layout()
