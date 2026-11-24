"""The fixed list of ranks offered when an account is created or edited (decision D2, 2026-11-24).

Stored in the database as plain text, so an older account whose rank is not in this list keeps working
until an administrator edits it. To add a rank, add one line here: the server check and the dashboard
dropdown (which reads GET /api/ranks) both follow.
"""

RANK_GROUPS = [
    ("Officers", [
        "Lieutenant", "Captain", "Major", "Lieutenant Colonel", "Colonel",
        "Brigadier", "Major General", "Lieutenant General", "General",
    ]),
    ("JCOs", ["Naib Subedar", "Subedar", "Subedar Major"]),
    ("Other Ranks", ["Sepoy", "Lance Naik", "Naik", "Havildar"]),
]

RANKS = [rank for _, ranks in RANK_GROUPS for rank in ranks]
APPOINTMENT_MAX = 50  # free text: CO, OC, Adjt, ...
