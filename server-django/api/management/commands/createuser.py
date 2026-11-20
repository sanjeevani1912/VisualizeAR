"""Creates a user from the command line. Needed once to make the first administrator, because the
API's own "create user" route is for administrators only.

    python manage.py createuser --username iyer --name "S. Iyer" --rank "Lt Col" --role admin

The password is asked for twice without echo, or read from the DHRUVAR_PASSWORD environment variable
(for scripts). It follows the same rules as the API and is never printed.
"""

import os
import re
from getpass import getpass

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from api.audit import audit
from api.auth import hash_password
from api.models import ROLES, User, now_ms
from api.rules import password_error


class Command(BaseCommand):
    help = "Create a DhruvAR user (use it once to create the first admin)."

    def add_arguments(self, parser):
        parser.add_argument("--username", required=True)
        parser.add_argument("--name", required=True)
        parser.add_argument("--rank", required=True)
        parser.add_argument("--role", required=True, choices=[r for r, _ in ROLES])

    def handle(self, *args, **opts):
        username, name, rank, role = opts["username"].strip(), opts["name"].strip(), opts["rank"].strip(), opts["role"]
        if not 3 <= len(username) <= 40 or not re.fullmatch(r"[A-Za-z0-9._-]+", username):
            raise CommandError("username: 3 to 40 characters, letters, digits, dot, dash or underscore only")
        if not 1 <= len(name) <= 80 or not 1 <= len(rank) <= 40:
            raise CommandError("name must be 1-80 characters and rank 1-40 characters")
        if User.objects.filter(username__iexact=username).exists():
            raise CommandError("That username is already taken")

        password = os.environ.get("DHRUVAR_PASSWORD")
        if password is None:
            password = getpass("Password: ")
            if password != getpass("Repeat password: "):
                raise CommandError("The two passwords differ")
        problem = password_error(password, username)
        if problem:
            raise CommandError(problem)

        with transaction.atomic():
            user = User.objects.create(
                username=username, name=name, rank=rank, role=role, password=hash_password(password), active=True, created_at=now_ms()
            )
            audit(None, "User created", None, f"{username} created as {role}")
        self.stdout.write(self.style.SUCCESS(f"Created {role} {username} ({user.id})"))
