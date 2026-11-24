"""Sets a new password for an existing user from the command line. For the person who runs the server, for example
when the only administrator has forgotten their password (the dashboard's Reset button needs an administrator).

    python manage.py resetpassword --username iyer

The password is asked for twice without echo, or read from the DHRUVAR_PASSWORD environment variable (for scripts).
It follows the same rules as the API and is never printed. The user's sessions are ended and the change is audited.
"""

import os
from getpass import getpass

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from api.audit import audit
from api.auth import destroy_user_sessions, hash_password
from api.models import User
from api.rules import password_error


class Command(BaseCommand):
    help = "Set a new password for an existing DhruvAR user (ends their sessions)."

    def add_arguments(self, parser):
        parser.add_argument("--username", required=True)

    def handle(self, *args, **opts):
        username = opts["username"].strip()
        user = User.objects.filter(username__iexact=username).first()
        if user is None:
            raise CommandError("No such user")

        password = os.environ.get("DHRUVAR_PASSWORD")
        if password is None:
            password = getpass("New password: ")
            if password != getpass("Repeat new password: "):
                raise CommandError("The two passwords differ")
        problem = password_error(password, user.username)
        if problem:
            raise CommandError(problem)

        with transaction.atomic():
            User.objects.filter(pk=user.pk).update(password=hash_password(password), must_change_password=True)
            audit(None, "Password reset", None, f"Password reset for {user.username} (command line)")
        destroy_user_sessions(user.id)
        note = "" if user.active else " The account is disabled: enable it on the Users page before they sign in."
        self.stdout.write(self.style.SUCCESS(f"Password changed for {user.username}. Their sessions were ended.{note}"))
