# Learning Engine Design

## Core concepts
- Goal: measurable capability the learner wants to acquire.
- Knowledge concept: stable node in a versioned graph.
- Prerequisite relation: typed edge; distinguish required from supportive knowledge.
- Assessment item: task linked to one or more concepts and evidence types.
- Attempt: a learner response plus scoring outcome and timestamp.
- Mastery estimate: uncertain estimate supported by multiple pieces of evidence.
- Learning action: teach, practice, assess, review, or request clarification.
- Decision record: candidates considered, selected action, and reason.

## Evidence types
Recognition, unaided recall, application, transfer, explanation, and creation.

## Initial deterministic planner
1. Compute concepts relevant to the goal.
2. Identify unresolved concepts.
3. Find concepts whose required prerequisites are sufficiently supported.
4. Generate available actions for those concepts.
5. Score actions using learning value, uncertainty reduction, prerequisite importance, estimated effort, and recent performance.
6. Select the highest-scoring valid action.
7. Store a concise reason for the decision.

## Safety against false confidence
- A single correct multiple-choice answer is weak evidence.
- Guessing should not be interpreted as reliable mastery.
- Missing evidence is unknown, not zero mastery.
- Conflicting evidence should increase uncertainty and may trigger another assessment.
- Thresholds must be tested and calibrated with actual learner data.
