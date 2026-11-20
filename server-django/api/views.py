from .http import JsResponse


def health(_request):
    response = JsResponse({"ok": True})
    response["Cache-Control"] = "no-store"
    return response
