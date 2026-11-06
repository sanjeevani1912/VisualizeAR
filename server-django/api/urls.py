from django.urls import path

from . import views

# Routes are added phase by phase; see PLAN.md for the full parity checklist.
urlpatterns = [
    path("health", views.health),
]
