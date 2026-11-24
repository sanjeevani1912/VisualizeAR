"""Tiny helpers that behave exactly like JavaScript.

Why this file exists: the Node server fingerprints a plan with `sha256(JSON.stringify(layout))`. Python's own
`json.dumps` writes some things differently (`10.0` instead of `10`, `1e-07` instead of `1e-7`, other
escapes for odd characters), and one different character gives a completely different fingerprint. So the
hash can only match if we reproduce JavaScript's text output exactly. The same goes for the length of a
string (JavaScript counts UTF-16 units, Python counts characters), `trim()` and the `%` operator.
"""

import math
import re

# JavaScript's String.prototype.trim() removes exactly these characters (Python's strip() differs a little).
JS_WHITESPACE = (
    "\t\n\x0b\x0c\r \xa0            "
    "    　﻿"
)


def js_trim(text: str) -> str:
    return text.strip(JS_WHITESPACE)


def js_len(text: str) -> int:
    """String length as JavaScript counts it: UTF-16 code units (an emoji counts as 2)."""
    return len(text.encode("utf-16-le", "surrogatepass")) // 2


def js_slice(text: str, count: int) -> str:
    """text.slice(0, count) in UTF-16 units. May leave half an emoji (a lone surrogate), as JavaScript does."""
    if js_len(text) <= count:
        return text
    raw = text.encode("utf-16-le", "surrogatepass")[: count * 2]
    return raw.decode("utf-16-le", "surrogatepass")


def js_mod(a: float, b: float) -> float:
    """JavaScript's `a % b` (the result takes the sign of `a`), unlike Python's `%`."""
    return math.fmod(a, b)


_LONE_SURROGATE = re.compile("[\ud800-\udfff]")


def db_text(text: str) -> str:
    """Replaces lone surrogates (broken half-emojis that only odd JSON input can contain) with U+FFFD.

    A database cannot store them. Replacing them once, at the door, keeps the stored text, the audit hash
    and what is returned to clients all identical.
    """
    return _LONE_SURROGATE.sub("�", text)


# ---------- numbers ----------
def js_number_text(value) -> str:
    """Number -> text the way JavaScript prints it (ECMAScript Number::toString)."""
    value = float(value)
    if value != value:
        return "NaN"
    if value == 0:
        return "0"  # also for -0
    if math.isinf(value):
        return "Infinity" if value > 0 else "-Infinity"
    sign = "-" if value < 0 else ""
    mantissa, _, exponent = repr(abs(value)).partition("e")
    whole, _, fraction = mantissa.partition(".")
    digits = whole + fraction
    n = len(whole) + (int(exponent) if exponent else 0)  # value = 0.<digits> x 10^n
    stripped = digits.lstrip("0")
    n -= len(digits) - len(stripped)
    digits = stripped.rstrip("0")
    k = len(digits)
    if k <= n <= 21:
        return sign + digits + "0" * (n - k)
    if 0 < n <= 21:
        return sign + digits[:n] + "." + digits[n:]
    if -6 < n <= 0:
        return sign + "0." + "0" * (-n) + digits
    e = n - 1
    exp_text = ("+" if e >= 0 else "-") + str(abs(e))
    if k == 1:
        return sign + digits + "e" + exp_text
    return sign + digits[0] + "." + digits[1:] + "e" + exp_text


# ---------- JSON.stringify ----------
_ESCAPES = {'"': '\\"', "\\": "\\\\", "\b": "\\b", "\f": "\\f", "\n": "\\n", "\r": "\\r", "\t": "\\t"}
_NEEDS_ESCAPE = re.compile('[\x00-\x1f"\\\\\ud800-\udfff]')


def _escape_char(match) -> str:
    ch = match.group(0)
    return _ESCAPES.get(ch) or "\\u%04x" % ord(ch)


def js_string_text(text: str) -> str:
    return '"' + _NEEDS_ESCAPE.sub(_escape_char, text) + '"'


def js_stringify(value) -> str:
    """JSON.stringify(value): no spaces, JavaScript number format, JavaScript string escapes."""
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    if isinstance(value, (int, float)):
        return js_number_text(value) if math.isfinite(value) else "null"
    if isinstance(value, str):
        return js_string_text(value)
    if isinstance(value, (list, tuple)):
        return "[" + ",".join(js_stringify(v) for v in value) + "]"
    if isinstance(value, dict):
        return "{" + ",".join(js_string_text(str(k)) + ":" + js_stringify(v) for k, v in value.items()) + "}"
    raise TypeError(f"cannot serialise {type(value).__name__}")
