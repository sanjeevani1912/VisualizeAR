# Parallel sessions: how to use these prompts

Written 2026-11-20. Four jobs can run in separate chats at the same time because they touch different folders.
Each prompt file is complete on its own: paste **`00-SHARED-CONTEXT.md` first**, then the job file.

| File | Job | Works in | Needs the others? |
|---|---|---|---|
| `A-MAP-SERVER-AND-NGINX.md` | Run the copied map server on this PC, put Nginx in front, one address for dashboard + API + map | `E:\map\map-server`, new folder `deploy/` in a new worktree | No |
| `B-DJANGO-BACKEND.md` | Finish the Django API (plans, images, tests against Node), then PostgreSQL | `DhruvAR-django/server-django` (existing worktree) | No |
| `C-DASHBOARD.md` | Asset icons, cleaner plan page, dashboard map that can use the map server | new worktree `DhruvAR-web`, folder `web/` only | Basemap testing needs A (not the rest) |
| `D-ANDROID-APP.md` | App fixes and the optional phone snapshot (later) | `app/` only | Start after A and C |

## Rules that keep the sessions from breaking each other

1. **One session per folder.** Never let two chats edit the same files.
2. **Every session works in its own git worktree** (a second checkout of the same repo on its own branch). Two chats
   sharing one folder would switch each other's branch. The prompts contain the exact commands.
3. **Ports are assigned** so servers never collide:

   | Who | Port | What |
   |---|---|---|
   | main folder / session C | 4000 | Node API (the dashboard's dev proxy is fixed to 4000) |
   | session C | 5173 | dashboard dev server |
   | session A | 8088 | map server (Caddy) |
   | session A | 8000 | Nginx single address |
   | session B | 4001 | Django |
   | session B | 4100 / 4101 | throwaway Node / Django for side-by-side tests |

   Only one chat runs the Node API on 4000 at a time. If both A and C need it, A uses its own copy on another port.
4. **The API contract is frozen** (`/api`, JSON field names, status codes, `{ "error": "..." }`). A session that
   thinks it needs a change must stop and tell the owner, who relays it. Nobody changes it alone.
5. **Nobody commits or pushes unless the owner says so.** Nobody pushes to `army/main`.
6. **Real data is never touched:** `DhruvAR/server/data/` (the demo database) is off limits to every session. Use a
   temporary `DATA_DIR`.
7. **Every session writes its own short document** in its own folder describing what it did, so the owner can read it later.
8. **At the end of each step a session stops and reports** (what changed, how it was checked, what was not checked,
   what comes next) and waits.

## How the sessions get combined later

Each session ends with a branch of its own. The owner (with the coordinator chat) merges them one by one:
`infra/nginx-map` (A), `django-backend` (B), `web/dashboard-polish` (C). They touch different folders, so conflicts
are unlikely; the order is C, then A, then B.

## How to open them

New chat, working folder = `C:\Users\itcell\AndroidStudioProjects\DhruvAR` (for A, C, D) or
`C:\Users\itcell\AndroidStudioProjects\DhruvAR-django` (for B). Paste the shared context, then the job prompt.
Alternatively ask the coordinator chat to start them; they then show up as linked sessions and report back automatically.
