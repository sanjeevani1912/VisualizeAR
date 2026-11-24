"""Lists the accounts: username, role, rank, appointment, active, last sign-in. Never shows passwords or tokens.

    python manage.py listusers
    python manage.py listusers --role admin
"""

import time

from django.core.management.base import BaseCommand

from api.models import ROLES, User


def _when(ms) -> str:
    return time.strftime("%Y-%m-%d %H:%M", time.gmtime(ms / 1000)) + " UTC" if ms else "never"


class Command(BaseCommand):
    help = "List DhruvAR users (no passwords)."

    def add_arguments(self, parser):
        parser.add_argument("--role", choices=[r for r, _ in ROLES])

    def handle(self, *args, **opts):
        users = User.objects.order_by("username")
        if opts["role"]:
            users = users.filter(role=opts["role"])
        rows = [
            (u.username, u.role, u.rank, u.appointment or "-", "yes" if u.active else "NO", _when(u.last_login_at))
            for u in users
        ]
        header = ("username", "role", "rank", "appointment", "active", "last sign-in")
        widths = [max(len(str(r[i])) for r in [header, *rows]) for i in range(len(header))]
        for r in [header, *rows]:
            self.stdout.write("  ".join(str(c).ljust(w) for c, w in zip(r, widths)).rstrip())
        self.stdout.write(f"{len(rows)} user(s)")
