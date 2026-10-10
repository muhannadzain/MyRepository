# Knowledge Pack Schema (proposal)

Each pack should have:
- `schemaVersion`
- `packId`
- `packVersion`
- `language`
- `title`
- `sources`
- `concepts`
- `questions`
- `resources`
- `goals`

Every concept and question has a stable unique ID. Every graph edge points to an existing concept. Required-prerequisite cycles should be rejected unless a carefully defined cyclic model is supported. Every scored question has a valid answer key or explicit grading rule. Content must be source-attributed and reviewed before release.

The exact JSON schema and validator are future milestones; do not treat this proposal as implemented validation.
