"""Test helper for api-test.ts: edits audit entry 3 in the Django database of the DATA_DIR given in the environment,
so the test can check that the chain verification notices.   DATA_DIR=<throwaway folder> python tamper_audit_django.py"""

import os
import sys

if not os.environ.get("DATA_DIR"):
    sys.exit("DATA_DIR must name a throwaway data folder")
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

import django  # noqa: E402

django.setup()
from api.models import AuditEntry  # noqa: E402

AuditEntry.objects.filter(seq=3).update(detail="edited")
