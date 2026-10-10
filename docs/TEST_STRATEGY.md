# Test Strategy

## Unit tests
- Question IDs are unique.
- Answer keys point to valid options.
- Graph edges refer to existing concepts.
- Required prerequisite subgraph has no cycles.
- Planner never recommends a concept before mandatory prerequisites are satisfied or explicitly diagnosed.
- Mastery estimate does not become certain after one correct answer.
- Missing evidence is not treated as failure.
- Imported content rejects invalid schemas.

## Integration tests
- New learner can complete onboarding and diagnostic.
- App can be restarted without losing persisted progress once persistence is implemented.
- Content pack version update preserves or deliberately migrates learner records.
- Core flow works in airplane mode.

## Manual review
- Arabic RTL rendering on small Android screens.
- Accessibility labels and text scaling.
- App startup, navigation, process recreation, and error states.
