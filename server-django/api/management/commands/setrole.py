"""Changes the role of an existing user from the command line. This is how an administrator becomes the first
super administrator (the dashboard cannot make one):

    python manage.py setrole --username iyer --role superadmin

The user's sessions are ended so they sign in again with the new role, and the change is audited.
It never removes the last active administrator or the last active super administrator.
"""

from django.core.management.base import BaseCommand, CommandError
from django.db import transaction

from api.audit import audit
from api.auth import active_admin_count, active_superadmin_count, destroy_user_sessions
from api.models import ROLES, User
from api.rules import ADMIN_ROLES


class Command(BaseCommand):
    help = "Change a DhruvAR user's role (ends their sessions)."

    def add_arguments(self, parser):
        parser.add_argument("--username", required=True)
        parser.add_argument("--role", required=True, choices=[r for r, _ in ROLES])

    def handle(self, *args, **opts):
        user = User.objects.filter(username__iexact=opts["username"].strip()).first()
        if user is None:
            raise CommandError("No such user")
        new_role = opts["role"]
        if new_role == user.role:
            raise CommandError(f"{user.username} already has the role {new_role}")
        if user.active and user.role in ADMIN_ROLES and new_role not in ADMIN_ROLES and active_admin_count() <= 1:
            raise CommandError("There must always be at least one active administrator")
        if user.active and user.role == "superadmin" and active_superadmin_count() <= 1:
            raise CommandError("There must always be at least one active super administrator")
        with transaction.atomic():
            old_role = user.role
            User.objects.filter(pk=user.pk).update(role=new_role)
            audit(None, "User updated", None, f"{user.username}: role {old_role} -> {new_role} (command line)")
        destroy_user_sessions(user.id)
        self.stdout.write(self.style.SUCCESS(f"{user.username} is now {new_role}. Their sessions were ended."))
