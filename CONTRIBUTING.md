# Contributing to Whisly

Thanks for your interest in Whisly. This project is evidence-gated: capabilities are only
enabled when there is a measured result supporting them. Please read this file before opening
a pull request.

## Ground rules

- Never invent a confidence score, accuracy figure, language capability, or feature.
- Do not add a cloud endpoint, API key, paid dependency, or upload path without an explicit
  discussion in an issue first.
- Do not bundle a new model without an issue that records its source URL, pinned revision,
  license, hashes, and measured evaluation results. See `whisly/docs/MODELS.md`.
- Keep fail-closed behaviour intact: unknown or low-confidence input must never produce a
  canned or guessed answer.

## Development setup

See [`docs/ENVIRONMENT.md`](./docs/ENVIRONMENT.md) for the JDK, Android SDK and Gradle setup.

```bash
cd whisly
./gradlew.bat clean assembleDebug testDebugUnitTest lint
```

A change is expected to keep the unit tests passing and lint at zero errors. Record any new
measurement in `whisly/docs/` with the date, command, and raw output.

## Commit style

Use `feat:`, `fix:`, `docs:`, `chore:`, `test:` or `refactor:` prefixes, for example:

```text
feat: add measured language tier for a new locale
docs: record evaluation harness results
chore: refresh Gradle wrapper
```

## Pull requests

1. Open an issue first for anything beyond a small fix.
2. Keep the PR focused and describe exactly what was measured and what remains unmeasured.
3. Do not commit APKs, AABs, keystores, `.env` files, model binaries outside the bundled set,
   or large generated reports. Release artifacts are attached to GitHub Releases instead.

## Reporting a problem

Include your device architecture (Whisly currently ships arm64-v8a only), Android version,
the exact steps, the on-screen error text, and whether an internet connection was available.
Do not paste private conversation content into an issue.
