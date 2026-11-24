# Users and roles: how to run them (for the person who looks after the server)

Written 2026-11-24. Plain words. All commands are PowerShell, run from the `deploy` folder of this project
(`cd ...\DhruvAR-integration\deploy`). Background and reasons are in `docs/USERS-AND-ROLES-PLAN.md`.
**Never write a password in chat, in a document or in Git.** None of the commands below prints one.

## 1. The four roles, and the super admin

| Role | What it is for |
|---|---|
| `planner` | Makes tactical plans in the phone app and submits them. Sees only their own plans. |
| `reviewer` | Reviews and approves, rejects or asks for changes (never on their own plan). |
| `auditor` | Reads everything, including the audit log. Cannot comment or decide. |
| `admin` | Manages ordinary accounts (planner, reviewer, auditor): create, edit, disable, reset passwords. Also reads everything. |
| `superadmin` | Everything an admin can do, **and** the only one who may create, edit, disable or reset **admins**. |

- The role decides what a person may do. **Rank and appointment are only labels** (for example "Major", "OC Coy"). An appointment such as CO
  gives no extra rights; if a CO must approve plans, give that account the `reviewer` role.
- A super admin can be made **only** on the command line (section 3). The dashboard cannot make one.
- The system always keeps at least one active admin-level account and at least one active super admin.
- Nobody can change their own role or disable themselves.

## 2. Day-to-day work on the Users page

Sign in as an admin or super admin at `http://localhost:8000`, menu **Users**.

| Task | How |
|---|---|
| Add a person | "Add a user" form: username, **rank (dropdown)**, name, optional **appointment** (up to 50 characters), role, then **Generate** for a temporary password, then **Create account**. |
| Hand over the password | The page shows it **once** at the top. Give it to the person face to face or by phone, one person at a time. Not by chat, e-mail or Git. |
| First sign-in | The person is forced to choose their own password before anything else (the "Choose your own password" screen). The Users page shows "Must change password" until they do. |
| Change rank, name, appointment, role | Edit in the table. Name and appointment save when you click out of the box. A role change signs that person out at once. |
| Switch a person off | **Disable** (never delete: their plans, comments and audit lines must keep a real author). **Enable** brings them back. |
| Forgotten password | **Reset password**: shows a new temporary password once; the person must change it at the next sign-in. |

Admins see other admins' rows greyed out; only a super admin can change them.

Rank dropdown: Lieutenant, Captain, Major, Lieutenant Colonel, Colonel, Brigadier, Major General, Lieutenant General, General;
Naib Subedar, Subedar, Subedar Major; Sepoy, Lance Naik, Naik, Havildar. To add a rank, add one line to `server-django/api/ranks.py`, rebuild (section 6).
An older account whose rank is not in the list shows "(not in list)" and keeps working until you pick a rank for it.

## 3. Commands inside Docker

```powershell
docker compose exec django python manage.py listusers                       # who exists (never shows passwords)
docker compose exec django python manage.py listusers --role admin          # only one role
docker compose exec django python manage.py createuser --username iyer --name "S. Iyer" --rank "Lieutenant Colonel" --appointment "CO" --role admin
docker compose exec django python manage.py resetpassword --username iyer   # asks for the password twice, nothing is shown
docker compose exec django python manage.py setrole --username iyer --role superadmin
```
- `createuser` and `resetpassword` ask for the password at a hidden prompt. The person is forced to change it at first sign-in.
  (For scripts only: the variable `DHRUVAR_PASSWORD` can supply it; do not leave it in a file.)
- `createuser` also makes the first super admin: `--role superadmin`.
- `setrole` changes the role of an existing account (this is how an admin becomes the first super admin). It ends their sessions.
- All of them follow the same password rules as the dashboard and write an audit line (shown with an empty actor, because no signed-in person did it).

Password rules: at least 10 characters, with upper case, lower case and a digit; it must not contain the username; a new password must differ from the old one.
Sign-in lock-out: 5 wrong tries for the same username and device lock it for 15 minutes. A session ends after 60 minutes idle or 12 hours.

## 4. If the only admin forgets the password

Use the command, no other admin needed:
```powershell
docker compose exec django python manage.py resetpassword --username <name>
```
Then sign in with it and choose a new password. Do **not** use `seed --reset` (it deletes everything).

## 5. Going from the demo to real officers

The demo accounts and plans are fictional (`seed`). To add real people next to them: first make sure `stackadmin` (the super admin) can sign in, then add
each officer on the Users page, hand over the temporary passwords as in section 2, and **Disable** the demo accounts you no longer want
(their plans and history stay). For a completely clean start: take a backup (section 7), then `docker compose down -v`, `docker compose up -d --build`,
and create the first super admin with `createuser --role superadmin`; **never run `seed`** afterwards. `docker compose down -v` deletes all data.

**Must never go to GitHub:** real names, ranks and usernames of officers; any password; `deploy/.env`; `deploy/*credentials*.txt`; `seed-credentials.txt`;
database dumps (`*.dump`) and image archives; screenshots showing real people; real plans or field images. Backups contain real accounts: keep them on a
protected disk.

## 6. After changing the code

```powershell
.\build-dashboard.ps1                 # rebuilds web\dist (the dashboard)
docker compose up -d --build django   # rebuilds Django and applies new database migrations
```
Tests (run in a throw-away container, they do not touch the live data):
```powershell
docker run --rm --entrypoint python -v "${PWD}\..\server-django:/app" -w /app -e DATA_DIR=/tmp/d dhruvar-django:latest manage.py test
```

## 7. Backup before anything risky
```powershell
bash .\backup-stack.sh
```
(database dump + uploaded images into `server-django\backups`, which Git ignores).

## 8. What was built and what was not

Built (Phases 1-4, November 2026): rank dropdown, optional appointment, `listusers`, `resetpassword`, `setrole`, forced password change (server + dashboard),
super admin, tests for all of it (253 server tests).

**Not built (future work):**
- A change-password screen in the **phone app**. A person told to change their password who signs in only on the phone sees "Password change required" and must
  change it once on the dashboard first.
- HTTPS (needed before any device other than this PC and the USB-connected phone is used), and enforcing `ALLOWED_HOSTS`.
- Bulk creation of users from a CSV file.
- Units/formations ("a reviewer sees only their unit's plans"): today every reviewer, admin and auditor sees every plan; planners see only their own.
- Password expiry, a list of too-common passwords, two-factor sign-in.
- A screen to list and end other people's sessions; sign-in history.
- Re-assigning a plan to another planner when its owner leaves.
- Audit lines for name/rank/appointment edits and for failed sign-ins (role, active, password changes and sign-ins are audited today).
- Scheduled backups.
