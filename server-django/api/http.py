"""Small helpers shared by every view: errors, JSON bodies, validation and the endpoint decorator.

Why this exists instead of Django REST Framework: the Android app and the web dashboard expect the exact
JSON shapes and error bodies the Node server produces. Errors are always `{ "error": "message" }`.
"""

import functools
import json
import re

from django.http import JsonResponse

JSON_CONTENT_TYPE = re.compile(r"\bapplication/json\b", re.I)
MISSING = object()  # a field that was not sent at all (JavaScript's "undefined")


class ApiError(Exception):
    """Raised anywhere in a view; the middleware turns it into `{ "error": message }` with the status."""

    def __init__(self, status: int, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


def error_response(status: int, message: str) -> JsonResponse:
    return JsonResponse({"error": message}, status=status)


def js_number(value: float):
    """JSON number the way JavaScript prints it: 60, not 60.0."""
    return int(value) if float(value).is_integer() else value


# ---------- request body ----------
def json_body(request):
    """The parsed JSON body, or MISSING when there is none.

    Matches Express: only `application/json` bodies are parsed, an empty body parses to nothing,
    and invalid JSON is a 400 "Malformed JSON".
    """
    if not JSON_CONTENT_TYPE.search(request.META.get("CONTENT_TYPE", "")):
        return MISSING
    raw = request.body
    if not raw or not raw.strip():
        return MISSING
    try:
        value = json.loads(raw)
    except (ValueError, UnicodeDecodeError):
        raise ApiError(400, "Malformed JSON")
    if not isinstance(value, (dict, list)):  # Express's strict mode accepts only objects and arrays
        raise ApiError(400, "Malformed JSON")
    return value


# ---------- validation (replaces zod; messages copied from zod 4.6, which the Node server uses) ----------
def js_type(value) -> str:
    if value is MISSING:
        return "undefined"
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    return "object"


class StrField:
    def __init__(self, name, *, trim=False, min_len=None, max_len=None, pattern=None, pattern_message="", optional=False):
        self.name, self.trim, self.min_len, self.max_len = name, trim, min_len, max_len
        self.pattern, self.pattern_message, self.optional = pattern, pattern_message, optional

    def check(self, value):
        """Returns (cleaned value, error message or None)."""
        if value is MISSING and self.optional:
            return MISSING, None
        if not isinstance(value, str):
            return value, f"Invalid input: expected string, received {js_type(value)}"
        text = value.strip() if self.trim else value
        if self.min_len is not None and len(text) < self.min_len:
            return text, f"Too small: expected string to have >={self.min_len} characters"
        if self.max_len is not None and len(text) > self.max_len:
            return text, f"Too big: expected string to have <={self.max_len} characters"
        if self.pattern is not None and not re.fullmatch(self.pattern, text):
            return text, self.pattern_message
        return text, None


class EnumField:
    def __init__(self, name, options, *, optional=False):
        self.name, self.options, self.optional = name, tuple(options), optional

    def check(self, value):
        if value is MISSING and self.optional:
            return MISSING, None
        if not (isinstance(value, str) and value in self.options):
            listed = "|".join(f'"{o}"' for o in self.options)
            return value, f"Invalid option: expected one of {listed}"
        return value, None


class BoolField:
    def __init__(self, name, *, optional=False):
        self.name, self.optional = name, optional

    def check(self, value):
        if value is MISSING and self.optional:
            return MISSING, None
        if not isinstance(value, bool):
            return value, f"Invalid input: expected boolean, received {js_type(value)}"
        return value, None


def validate(body, fields):
    """Checks `body` against `fields` in order. Returns (data dict, None) or (None, "field: message").

    The error text is the first problem found, formatted like the Node server does: "field: message".
    Fields that were optional and not sent are left out of the result.
    """
    if not isinstance(body, dict):
        return None, f": Invalid input: expected object, received {js_type(body)}"
    data = {}
    first_error = None
    for field in fields:
        value, error = field.check(body.get(field.name, MISSING))
        if error and first_error is None:
            first_error = f"{field.name}: {error}"
        if value is not MISSING:
            data[field.name] = value
    return (None, first_error) if first_error else (data, None)


# ---------- the endpoint decorator ----------
def endpoint(*methods, auth=False, roles=None):
    """Wraps a view with the checks every Node route had.

    * a request with another method is treated as "no such route" (see `not_found`);
    * `auth=True` needs a signed-in user (401 "Not signed in");
    * `roles` needs one of those roles (403).
    """
    allowed = set(methods) | ({"HEAD"} if "GET" in methods else set())

    def decorate(view):
        @functools.wraps(view)
        def wrapper(request, *args, **kwargs):
            if request.method not in allowed:
                return not_found(request)
            if (auth or roles) and request.api_user is None:
                raise ApiError(401, "Not signed in")
            if roles and request.api_user.role not in roles:
                raise ApiError(403, "Your role is not allowed to do this")
            return view(request, *args, **kwargs)

        return wrapper

    return decorate


def not_found(request, *args, **kwargs):
    """Unknown API path. Like Express, a caller who is not signed in is told that first, and anything
    under /api/admin needs the admin role before it can even be reported missing."""
    if request.api_user is None:
        raise ApiError(401, "Not signed in")
    if request.path_info.startswith("/api/admin/") and request.api_user.role != "admin":
        raise ApiError(403, "Your role is not allowed to do this")
    raise ApiError(404, "Not found")
