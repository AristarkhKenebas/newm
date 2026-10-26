# newm's View as a config.py sees one (in view.rules): app_id, title, role, pid, panel, parent,
# up_state; its methods are newm on Caulis's.
from __future__ import annotations
from typing import Any, Optional


class _Up:
    def __init__(self, d: dict[str, Any]) -> None:
        self.__dict__.update(d)
        self.size = tuple(d.get("size", (0, 0)))
        self.offset = tuple(d.get("offset", (0, 0)))
        self.size_constraints = list(d.get("size_constraints", [0, 0, 0, 0]))


class View:
    def __init__(self, data: Any) -> None:
        if isinstance(data, dict):
            self._handle = data.get("handle", -1)
            self.app_id = data.get("app_id", "")
            self.title = data.get("title", "")
            self.role = data.get("role", "toplevel")
            self.pid = data.get("pid", 0)
            self.panel = data.get("panel")
            self.is_xwayland = data.get("xwayland", False)
            parent = data.get("parent")
            self.parent: Optional[View] = View(parent) if parent is not None else None
            up = data.get("up_state")
            self.up_state = _Up(up) if up is not None else None
        else:
            self._handle = data
            self.app_id = ""
            self.title = ""
            self.role = "toplevel"
            self.pid = 0
            self.panel = None
            self.is_xwayland = False
            self.parent = None
            self.up_state = None

    def __getattr__(self, name: str) -> Any:
        if name.startswith("_"):
            raise AttributeError(name)

        def method(*args: Any, **kwargs: Any) -> Any:
            import host
            return host.request("view", handle=self._handle, method=name, args=host.encode(list(args)), kwargs=host.encode(kwargs))

        return method

    def __eq__(self, other: object) -> bool:
        return isinstance(other, View) and other._handle == self._handle

    def __hash__(self) -> int:
        return hash(self._handle)

    def __str__(self) -> str:
        return "<View %d: %s, %s>" % (self._handle, self.title, self.app_id)
