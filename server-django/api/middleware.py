"""Runs around every /api request, the same job as the app.use(...) lines in server/src/index.ts.

Order matters and matches Node: identify the user from the token, then the CSRF header check, then the
view. Every API answer is `Cache-Control: no-store`, and every error is `{ "error": message }`.
"""

import logging

from .auth import csrf_guard, load_user
from .http import ApiError, error_response

log = logging.getLogger("dhruvar.api")


def _is_api(request) -> bool:
    return request.path_info.startswith("/api/")


class ApiMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        if not _is_api(request):
            return self.get_response(request)
        request.api_user = None
        request.via_bearer = False
        try:
            load_user(request)
            csrf_guard(request)
            response = self.get_response(request)
        except ApiError as e:
            response = error_response(e.status, e.message)
        if "Cache-Control" not in response:  # an image sets its own (private, max-age=300)
            response["Cache-Control"] = "no-store"
        return response

    def process_exception(self, request, exception):
        """Errors raised inside a view. Anything unexpected is logged and reported without details."""
        if not _is_api(request):
            return None
        if isinstance(exception, ApiError):
            return error_response(exception.status, exception.message)
        log.exception("Unhandled error in %s %s", request.method, request.path_info)
        return error_response(500, "Something went wrong on the server")
