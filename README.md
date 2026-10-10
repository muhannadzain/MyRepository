# مِرقاة | MIRQA

**أنت تحدد الوجهة، ومرقاة تكتشف الطريق.**

MIRQA is an offline-first Android prototype for an adaptive learning engine. This repository is the first runnable product skeleton, not the finished learning engine.

## Current prototype

- Arabic RTL Android interface using Jetpack Compose.
- Local, bundled sample questions; no network permission and no API key.
- A goal screen, short diagnostic flow, example lesson, illustrative knowledge map, and session progress view.
- Small unit tests for the bundled question pack.
- GitHub Actions workflow to build a debug APK.

## Important limitations

This first build is a UI and content-flow prototype. It does **not** yet implement persistent progress, a validated knowledge graph, a production mastery model, adaptive question selection, AI inference, or a scientifically validated relativity curriculum. The current sample lesson and map are illustrative.

## Build on GitHub

1. Create a new GitHub repository.
2. Upload the contents of this folder to the repository root.
3. Open **Actions** and run the workflow named **Build MIRQA Android APK**, or push a commit to `main`.
4. Wait for the workflow to complete and download the `MIRQA-debug-apk` artifact.
5. Install the APK on an Android device. Android may ask you to allow installation from the source you used.

The workflow uses Java 17 and Gradle setup to build the project. The generated debug APK is for testing, not a signed Play Store release.

## Build locally

Install Android Studio with Android SDK Platform 35 and JDK 17, open this folder, let Gradle sync, then run the `app` configuration.

## Project structure

- `app/src/main/java/com/mirqa/` — Android application.
- `engine/` — sample question pack and basic engine-facing models.
- `docs/` — product, architecture, content, and test specifications.
- `.github/workflows/` — CI build workflow.

## Product principle

The deterministic learning engine should own prerequisite analysis, evidence tracking, and next-step selection. A language model may later assist with explanations or open-ended feedback, but it must not be a required dependency for the core offline flow.
