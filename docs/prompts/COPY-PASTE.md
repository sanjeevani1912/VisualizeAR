# Copy-paste cheat sheet

## Every chat

1. Open new agent chat in Cursor or Antigravity  
2. Attach or `@` these files if the tool supports file mentions:
   - `docs/prompts/00-SHARED-CONTEXT.md`
   - `docs/DhruvAR-Project-Proposal.md`
3. Paste the phase prompt body (`01` … `06`)  
4. Add this closing line:

```text
Follow the acceptance criteria checklist at the end. Do not start the next phase.
When finished, list changed files, tests run, and manual verification steps.
```

## Order

```text
01-GEO-DATA-MODEL
→ 02-GEO-MATH
→ 03-GNSS-ORIGIN
→ 04-MAPLIBRE-PLANNING
→ 05-AR-GEO-WORKFLOW
→ 06-HARDENING-QA
```

## Optional first message

```text
Read docs/prompts/00-SHARED-CONTEXT.md and docs/DhruvAR-Project-Proposal.md,
then execute docs/prompts/0N-....md exactly. Stay within that phase’s scope.
```

Replace `0N-....md` with the phase file name.
