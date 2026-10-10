# Instructions for AI coding agents

Before editing, read `README.md` and the relevant files in `docs/`.

## Non-negotiable engineering rules

1. Keep the core learning logic separate from Compose UI.
2. Preserve offline operation. Do not add network permissions, remote APIs, analytics, or telemetry without explicit approval.
3. Do not claim the engine is adaptive until the selection logic and tests actually implement that behavior.
4. Make focused changes; do not rewrite unrelated files.
5. Add or update unit tests for every important deterministic rule.
6. Never silently change persistent-data schemas. If persistence is added, include migrations or a documented reset policy.
7. Use stable IDs for concepts, questions, content packs, and learning records.
8. Validate imported content before the app consumes it.
9. Run tests and build checks when tools are available; report failures honestly.
10. Keep Arabic RTL layout and accessibility in mind.
11. Do not invent scientific claims or mark mastery based on a single correct answer.
12. Update `CHANGELOG.md` and relevant documentation when behavior changes.

## Definition of done

- The change matches an explicit requirement.
- Tests cover the important behavior.
- Existing tests still pass.
- No unrelated feature was modified.
- The README/docs accurately describe what is implemented and what remains incomplete.
