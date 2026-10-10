# Architecture

## Layers
1. UI: Compose screens, state presentation, and user interactions.
2. Domain: learning goals, concept graph, evidence, mastery estimation, diagnosis, and planning.
3. Data: local database and versioned content packs.
4. Optional language-model adapter: disabled by default and never required for the core flow.

## Dependency direction
UI may call domain use cases. Domain must not depend on Android UI classes or network services. Data implements domain-facing repositories.

## Current status
The current prototype has a Compose UI and a bundled demo question list. The domain graph, mastery estimator, adaptive planner, and persistent repository are not implemented yet. `LearningStep` currently references a Compose Color and should be moved to UI if the map model becomes part of the real domain layer.
