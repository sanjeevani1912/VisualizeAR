from django.http import JsonResponse


def health(_request):
    response = JsonResponse({"ok": True})
    response["Cache-Control"] = "no-store"
    return response
