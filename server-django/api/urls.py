from django.urls import path, re_path

from . import views, views_auth, views_users
from .http import not_found

# Routes are added phase by phase; see PLAN.md for the full parity checklist.
# Paths have no trailing slash, exactly like the Node server's.
urlpatterns = [
    path("health", views.health),
    # phase 1: sign-in and users
    path("auth/login", views_auth.login),
    path("auth/token", views_auth.token),
    path("auth/logout", views_auth.logout),
    path("auth/me", views_auth.me),
    path("auth/change-password", views_auth.change_password),
    path("users", views_users.user_names),
    path("admin/users", views_users.admin_users),
    path("admin/users/<str:user_id>", views_users.admin_user),
    path("admin/users/<str:user_id>/password", views_users.admin_user_password),
    # anything else under /api: JSON "Not found" (or "Not signed in") instead of Django's HTML page
    re_path(r"^.*$", not_found),
]
