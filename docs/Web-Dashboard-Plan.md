# DhruvAR Web Dashboard — Draft Plan

Status: **draft for discussion**. Builds on `docs/DhruvAR-Project-Proposal.md` (server + web + share/approve) and the frozen layout schema in `docs/layout-schema-v2.md`. Nothing here is built yet; the Android app today is single-user and offline with no login, backend or sync.

## 1. Purpose

Give senior officials one place to:

1. See every tactical plan submitted by field planners, with a map view of the layout (no AR needed).
2. Filter and search plans by status, planner and date. (Grouping by army unit comes later, see section 3.)
3. **Approve, reject (with reason) or request changes**, with a permanent record of who decided what and when.
4. Track status across all plans at a glance (what is waiting, overdue, approved, rejected).

Non-goals for v1: live multi-user AR, editing layouts on the web, terrain/3D on the web, chat.

## 2. Users and roles

| Role | Can do |
|---|---|
| Planner (Android) | Create plans, submit for approval, withdraw, resubmit after rejection, view own plans |
| Reviewer / Senior | View all submitted plans (per-unit scoping later), approve / reject / request changes, comment |
| Unit commander (later phase) | Everything a reviewer can in their unit, plus assign reviewers |
| Admin | Manage users, role assignments (units later), view audit log. **Cannot** approve unless also a reviewer (separation of duties) |
| Auditor (read-only, optional) | View plans and the audit trail |

Rule to decide early: can a planner approve their own plan? Recommended: **never**.

## 3. Clusters (army units) - deferred

A cluster means a different unit of the army. Grouping by unit is **a later phase**, not part of the first release. For now:

- v1 treats all plans as one flat pool visible to every reviewer. No cluster filter, no cluster cards, no per-unit access rules.
- Keep the door open cheaply: give the Plan record a nullable `unit_id` column and the user record a nullable `unit_id`, and write permission checks in one place on the server, so unit-based segregation and access control can be switched on later without redesign.
- When clusters are built (Phase 6), they become an admin-managed hierarchy (formation > unit), each plan is assigned to one unit, and reviewers see only their units (section 7 rules then apply per unit).

## 4. Plan lifecycle (state machine)

```
DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED
                         |  \-> CHANGES_REQUESTED -> (planner edits) -> SUBMITTED (new version)
                         \----> REJECTED (reason mandatory)
SUBMITTED/UNDER_REVIEW -> WITHDRAWN (by planner)
APPROVED -> SUPERSEDED (a newer approved version exists) / REVOKED (by authorised senior, reason mandatory)
```

Rules:
- Every submission creates an **immutable version** (a snapshot of the layout JSON). Approval applies to a specific version, never to "the plan" in general.
- Editing after submission creates a new version and returns the plan to SUBMITTED; the old approval does not carry over.
- Reject and request-changes need a written reason; approve may carry an optional remark.
- Optional: multi-level approval (e.g. unit commander then higher HQ). Design the decision table so a plan can have several required approvals, even if v1 uses one.
- Time limits: show "waiting N days"; optional escalation reminders.

## 5. Dashboard features

**v1 (the professional dashboard - the current focus)**
- Login; role-aware home.
- **Overview page**: status tiles (pending / approved / rejected / changes requested), "needs your decision" queue sorted by oldest, recent activity.
- **Plan list** with filters: status, planner, date range, asset types; search by name; sortable table.
- **Plan detail**: map of the region with grid, origin and asset footprints (MapLibre, same tiles as the app), asset table (type, name, x/z meters, rotation, size), region size, origin lat/lng and GPS accuracy, version history, comments, decision buttons.
- **Decision panel**: Approve / Reject / Request changes + reason; confirmation step; shows exactly which version is being decided.
- **Audit trail** per plan (who viewed? who decided, when, from where).
- Notifications: email or in-app for new submissions and for decisions (planner is told the outcome and the reason).

**v2**
- Compare two versions (assets added/moved/removed).
- Dashboard charts: approval turnaround time, rejection reasons, load per reviewer.
- Export an approved plan as a PDF briefing sheet (map + asset table + approval stamp).
- Bulk actions for reviewers; delegate/out-of-office.
- Read-only 3D or isometric preview.

## 6. Architecture

```
Android app (planner)  --HTTPS-->  API server  --> Database (plans, versions, users, decisions, audit; units added in Phase 6)
Web dashboard (React)  --HTTPS-->  API server  --> Object store (optional: AR photos, PDFs)
                                       |
                              Map tile server (existing India map server, behind TLS)
```

- **Backend**: pick one stack the team can maintain and accredit (FastAPI, Django or Spring Boot were listed in the proposal). Stack-agnostic REST + JSON; OpenAPI spec written first so Android and web are built against the same contract.
- **Database**: PostgreSQL (relational: users, plans, versions, decisions, audit). Store each version's layout JSON as a JSON column plus extracted fields (region bounds, asset counts) for filtering.
- **Web**: React + TypeScript; MapLibre GL JS for the map; reuse the Android palette (Field Kit) for consistency.
- **Deployment**: on the organisation's own network/servers. Decide up front whether it must run fully air-gapped or on an intranet only; that rules out public cloud services, CDNs, Google fonts, public map tiles and third-party analytics.

### Minimum API surface

```
POST /auth/login            POST /auth/refresh        POST /auth/logout
GET  /dashboard/summary     (status counts, my decision queue)
GET  /plans?status=&q=      GET /plans/{id}      GET /plans/{id}/versions/{v}
POST /plans                 (create/submit from Android; body = layout JSON v2)
POST /plans/{id}/submit     POST /plans/{id}/withdraw
POST /plans/{id}/versions/{v}/decision   { decision: approve|reject|changes, reason }
GET  /plans/{id}/audit      POST /plans/{id}/comments
```

## 7. Security and access control (the part to get right)

These plans are sensitive, so treat security as a requirement, not a later phase.

- **Authentication**: real accounts with strong passwords/MFA or integration with the organisation's identity system (LDAP/AD/SSO). The proposal's "static accounts" are acceptable only as a stopgap; never ship shared logins because the audit trail becomes meaningless.
- **Authorisation**: enforce on the **server** on every request: role (and, from Phase 6, unit membership). A user must not reach a plan they are not entitled to by guessing an id. Deny by default.
- **Transport**: TLS everywhere, including the map server (it is plain HTTP today) and API calls from the Android app. Certificate pinning on the app.
- **Data at rest**: encrypt database and backups; encrypt the Android local layout files too (today plain JSON, and Android backup is enabled).
- **Audit log**: append-only, records logins, views of a plan, submissions, decisions, role changes, exports. Not editable by admins.
- **Integrity of approvals**: store a hash of the exact version approved; optionally digitally sign decisions so a stored approval cannot be altered silently.
- **Sessions**: short-lived tokens, idle timeout, lockout after repeated failures, IP/device policy if required.
- **Classification and data handling**: confirm the required marking/classification, retention period, and whether layout data may leave the network. Add screen banners ("RESTRICTED" etc.) and watermark exports with user and time.
- **Separation of duties**: planner cannot approve own plan; admin role is not automatically a reviewer.
- **Hardening the Android release build** that talks to the server: real application id, signing, minification, remove cleartext exception, disable the public fallback map style.
- Get the deployment **security-reviewed/accredited** by the competent authority before real use.

## 8. Changes needed in the Android app

1. Login screen and secure token storage (Android Keystore).
2. **Submit for approval** action (current schema has no status or unit; keep these as server-side metadata so the layout JSON v2 stays frozen, or bump to v3 deliberately).
3. Plan status shown on Home cards (draft / submitted / approved / rejected / changes requested) and the reviewer's reason on rejection.
4. Sync layer: upload on submit, download decision status; handle offline (queue submissions) and conflicts (server version is authoritative once submitted).
5. Lock editing of a submitted version; "edit" creates a new draft version.
6. Optional: show an "Approved" badge/stamp in AR and on captured images.

## 9. Data model sketch

```
User(id, name, role, unit_id NULL, active, ...)
# Phase 6 adds: Unit(id, name, parent_id), Membership(user_id, unit_id, role)
Plan(id, unit_id NULL, owner_id, current_version, status, created_at, updated_at)
PlanVersion(plan_id, version, layout_json, content_hash, submitted_at, submitted_by)
Decision(id, plan_id, version, reviewer_id, decision, reason, decided_at, signature?)
Comment(id, plan_id, version, author_id, text, created_at)
AuditEvent(id, actor_id, action, target, ip, at, details)
```

## 10. Phased delivery

| Phase | Scope | Result |
|---|---|---|
| 0. Decisions | Roles, approval levels, classification, hosting, identity system, retention | Signed-off requirements |
| 1. Dashboard first | Web app (React) against a mock/seed API: overview, plan list, plan detail with map, decision panel, version history, audit view; professional visual design, responsive, accessible | Clickable, realistic dashboard to demo to seniors and refine |
| 2. Backend core | Auth, users, plan + version storage, state machine, audit log, OpenAPI contract; replace the mock | Dashboard runs on real data |
| 3. Android integration | Login, submit, status, sync, edit-lock | End-to-end loop planner -> senior -> planner |
| 4. Hardening | TLS, encryption, pen-test, backup/restore drill, load test | Ready for pilot |
| 5. Pilot and v2 | Version compare, PDF export, charts, notifications | Feedback-driven iteration |
| 6. Clusters / units | Unit hierarchy, plan-to-unit assignment, unit overview, per-unit access control | Segregation by unit |

Building the dashboard first against agreed sample data lets seniors react to a real screen early, and fixes the API contract before the backend exists.

## 11. Other things to keep in mind

- **Version drift**: web and Android must both read the same schema; keep contract tests with sample layouts (including v1 files).
- **Map on the web**: same tile server, over TLS, restricted to the internal network; show attribution (OSM, Survey of India via DataMeet, CC BY 4.0).
- **Border/map-data sensitivity**: India map boundaries must be the officially correct ones; the current server uses Survey of India boundaries, keep it that way.
- **Accuracy disclaimer** on every plan view ("planning visualization, not survey grade; GPS accuracy +/- X m") so approvers do not over-trust positions.
- **Reviewer usability**: officials are time-poor; the first screen must answer "what needs my decision?" in one glance; decisions should be possible in two clicks plus a reason.
- **Mobile/tablet web**: seniors may review on tablets; design responsive and test on low bandwidth.
- **Offline field reality**: planners may submit later; show "queued" state and never lose a plan.
- **Notifications** must not leak plan contents (send "a plan awaits you", not the details).
- **Backup, disaster recovery and retention**: tested restore, retention policy, and what happens to rejected/withdrawn drafts.
- **Localisation**: Hindi/English labels if required; place search already handles local-script names.
- **Testing**: role-permission matrix tests, state-machine tests, audit completeness tests, plus the existing Android unit tests as a model.
- **Team and effort**: a small team (1 backend, 1 web, 1 Android, part-time security/DevOps/QA) is realistic; effort depends mostly on how heavy the identity and accreditation requirements turn out to be, not on the dashboard UI.

## 12. Open questions for stakeholders

1. (When Phase 6 starts) how are army units structured and who maintains that list?
2. How many approval levels, and who may approve at each?
3. Must it run air-gapped? Which identity system must be used?
4. Classification/marking, retention and export (PDF/print) rules?
5. Expected scale: number of planners, reviewers, plans per week?
6. Should approved plans be pushed back to planners' phones as read-only references for AR use on site?
7. Is anything beyond status (e.g. comments on specific assets) needed in v1?
