# Copy-paste cheat sheet — Guided Setup series

## Every chat

1. Open a **new** agent chat (Cursor / other AI IDE)
2. Attach or `@`:
   - `docs/prompts/00-SHARED-CONTEXT.md`
   - `docs/prompts/ux-field-kit/00-UX-SHARED.md`
   - `docs/prompts/guided-setup/00-SETUP-SHARED.md`
   - the phase file for this step only
3. Paste this closer:

```text
Execute only this guided-setup phase. Do not start the next phase.
Follow the acceptance criteria checklist at the end.
When finished: list changed files, tests run, and manual verification steps.
```

## Order (do not skip)

```text
01-SETUP-STEP-MACHINE
→ 02-FIND-LOCATION
→ 03-SET-REGION
→ 04-REGION-WORKSPACE-GRID
→ 05-SET-ORIGIN
→ 06-PLACE-ASSETS
→ 07-RESUME-HARDENING
```

## Optional first-message template

```text
Read docs/prompts/00-SHARED-CONTEXT.md,
docs/prompts/ux-field-kit/00-UX-SHARED.md,
and docs/prompts/guided-setup/00-SETUP-SHARED.md,
then execute docs/prompts/guided-setup/0N-….md exactly.
Stay within that phase’s scope. Do not implement later guided-setup phases.
```

Replace `0N-….md` with the phase file name.

## After each phase

1. Manual QA from the phase checklist on a device/emulator if possible
2. Commit if you want a clean checkpoint (ask the agent only if you want a commit)
3. New chat → next phase
