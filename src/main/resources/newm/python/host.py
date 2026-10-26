# Runs a newm config.py (Python, as newm had it) for newm on Caulis, and talks to it over
# stdin/stdout: one JSON message per line, both ways, calls nesting (a key binding's lambda
# calls layout.move, which newm on Caulis answers while the binding waits).
#
#   newm -> here:  {"id": n, "op": "load", "path": ...} | {"id": n, "op": "call", "fn": id, "args": [...]}
#                  | {"id": n, "op": "env", "env": {...}}
#   here -> newm:  {"id": n, "result": ...} | {"id": n, "error": "..."}
#                  | {"rid": n, "op": "layout"|"view", ...}   answered with {"rid": n, "result"/"error": ...}
import importlib
import json
import logging
import os
import pathlib
import sys
import traceback
import types

# (The shims import "host": this very module, not a second copy of it.)
sys.modules.setdefault("host", sys.modules[__name__])

# The protocol keeps the real stdout; print() in a config goes to stderr (newm's log).
_proto_out = os.fdopen(os.dup(1), "w", buffering=1)
os.dup2(2, 1)
sys.stdout = sys.stderr
_proto_in = sys.stdin

logging.basicConfig(stream=sys.stderr, level=logging.INFO, format="[%(levelname)s] %(name)s: %(message)s")
logger = logging.getLogger("newm.config")

_callables: dict = {}
_ids: dict = {}
_next_rid = 0


def _ref(fn) -> int:
    key = id(fn)
    if key not in _ids:
        cid = len(_callables) + 1
        _callables[cid] = fn
        _ids[key] = cid
    return _ids[key]


def encode(v, depth=0):
    from newm.layout import Layout
    from newm.view import View
    if depth > 32:
        return None
    if v is None or isinstance(v, (bool, int, float, str)):
        return v
    if isinstance(v, Layout):
        return {"__layout__": True}
    if isinstance(v, View):
        return {"__view__": v._handle}
    if isinstance(v, (list, tuple)):
        return [encode(x, depth + 1) for x in v]
    if isinstance(v, dict):
        return {str(k): encode(x, depth + 1) for k, x in v.items()}
    if callable(v) and not isinstance(v, type):
        return {"__callable__": _ref(v)}
    return None


def decode(v):
    from newm import layout as layout_module
    from newm.view import View
    if isinstance(v, list):
        return [decode(x) for x in v]
    if isinstance(v, dict):
        if "__layout__" in v:
            return layout_module.the_layout
        if "__view__" in v:
            return View(v["__view__"])
        if "__callable__" in v:
            return _callables.get(v["__callable__"])
        return {k: decode(x) for k, x in v.items()}
    return v


def _send(msg) -> None:
    _proto_out.write(json.dumps(msg) + "\n")


def _read():
    line = _proto_in.readline()
    if not line:
        sys.exit(0)
    return json.loads(line)


def request(op: str, **fields):
    """Asks newm on Caulis (a layout or view method); answers its calls meanwhile."""
    global _next_rid
    _next_rid += 1
    rid = _next_rid
    _send({"rid": rid, "op": op, **fields})
    while True:
        msg = _read()
        if msg.get("rid") == rid:
            if "error" in msg:
                raise RuntimeError(msg["error"])
            return decode(msg.get("result"))
        _handle(msg)


def _load(path: str):
    p = pathlib.Path(path)
    sys.modules.pop(p.stem, None)
    if str(p.parent) not in sys.path:
        sys.path.insert(0, str(p.parent))
    module = importlib.import_module(p.stem)
    logger.info("Loaded config at %s", path)
    values = {}
    for k, v in module.__dict__.items():
        if k.startswith("_") or isinstance(v, (types.ModuleType, type)):
            continue
        e = encode(v)
        if e is not None or v is None:
            values[k] = e
    return values


def _describe(e: BaseException) -> str:
    """The mistake and where it is: "NameError: name 'brightness' is not defined (config.py, line 254)"."""
    if isinstance(e, SyntaxError) and e.lineno is not None:
        return "%s: %s (%s, line %d)" % (type(e).__name__, e.msg, os.path.basename(e.filename or "config.py"), e.lineno)
    text = "%s: %s" % (type(e).__name__, e)
    # The innermost frame of the user's own files (not this host's).
    own = [f for f in traceback.extract_tb(e.__traceback__) if os.path.basename(f.filename) != "host.py"]
    if own:
        text += " (%s, line %d)" % (os.path.basename(own[-1].filename), own[-1].lineno)
    return text


def _handle(msg) -> None:
    op = msg.get("op")
    mid = msg.get("id")
    try:
        if op == "load":
            result = _load(msg["path"])
        elif op == "call":
            fn = _callables[msg["fn"]]
            result = encode(fn(*decode(msg.get("args", []))))
        elif op == "env":
            os.environ.update(msg["env"])
            result = None
        else:
            raise RuntimeError("unknown op %s" % op)
        _send({"id": mid, "result": result})
    except BaseException as e:
        if isinstance(e, SystemExit):
            raise
        logger.error("%s failed:\n%s", op, traceback.format_exc())
        _send({"id": mid, "error": _describe(e)})


if __name__ == "__main__":
    while True:
        _handle(_read())
