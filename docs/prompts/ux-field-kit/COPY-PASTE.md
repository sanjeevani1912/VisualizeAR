# Copy-paste cheat sheet — Field Kit UX series

## Every chat

1. Open a **new** agent chat (Cursor / other AI IDE)
2. Attach or `@`:
   - `docs/prompts/00-SHARED-CONTEXT.md`
   - `docs/prompts/ux-field-kit/00-UX-SHARED.md`
   - the phase file for this step only
3. Paste this closer:

```text
Execute only this UX phase. Do not start the next phase.
Follow the acceptance criteria checklist at the end.
When finished: list changed files, tests run, and manual verification steps.
```

## Order (do not skip)

```text
01-THEME-FIELD-KIT
→ 02-CONTEXTUAL-HUD
→ 03-LOCATION-SEARCH
→ 04-REGION-CORNER-DRAG
→ 05-ORIGIN-HYBRID-CHIP
→ 06-AR-COLLAPSIBLE-BRIEFING
```

## Optional first message template

```text
Read docs/prompts/00-SHARED-CONTEXT.md and docs/prompts/ux-field-kit/00-UX-SHARED.md,
then execute docs/prompts/ux-field-kit/0N-….md exactly.
Stay within that phase’s scope. Do not implement later UX phases.
```

Replace `0N-….md` with the phase file name.

## After each phase

1. Manual QA from the phase checklist on a device/emulator
2. Commit if you want a clean checkpoint (ask the agent only if you want a commit)
3. New chat → next phase
