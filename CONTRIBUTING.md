# Contributing to OpenDisplay Android

Thanks for considering a contribution. This is a one-person side project maintained evenings and
weekends — response times will vary, but bug reports and pull requests are genuinely welcome.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating, you're
expected to uphold it.

## Reporting bugs / requesting features

Open an issue using the [bug report](.github/ISSUE_TEMPLATE/bug_report.yml) or
[feature request](.github/ISSUE_TEMPLATE/feature_request.yml) template. A few things that make a
report much faster to act on:

- **Proof.** A screenshot, `adb logcat` output, or a command + its output showing the actual
  behavior. Mask any IP address or personal data before pasting.
- **Device and Android version**, if it's a bug.
- Check existing issues first — it may already be tracked.

## Development setup

```sh
./gradlew assembleDebug          # build
./gradlew installDebug           # install on a connected device/emulator
./gradlew testDebugUnitTest      # run unit tests
adb logcat -s OpenDisplay:*      # app logs
```

Requires the Android SDK installed, with `ANDROID_HOME`/`local.properties` pointing to it. See
[`README.md`](README.md) for the full build/usage docs and
[`BUILD.md`](BUILD.md) for keystore/release details (not needed for regular contributions).

## Code style

- **Simplest solution that solves the real need.** No speculative abstractions, no
  premature optimization, no unused flexibility "for later."
- **Comments**: no `//` comments scattered in the middle of code. Class/function documentation
  goes in KDoc (`/** ... */`). The non-obvious *why* behind a line or block belongs in
  [`RATIONALE.md`](RATIONALE.md), not inline.
- Kotlin + Jetpack Compose, following the structure already in `app/src/main/java/`.

## Protocol compatibility

This client talks to the unmodified OpenDisplay Mac app, so **no change may break the wire
protocol — security hardening included.** The upstream
[`PROTOCOL.md`](https://github.com/peetzweg/opendisplay/blob/main/PROTOCOL.md) is the reference,
and its MUST / MUST NOT rules apply here.

Before touching networking, the handshake, framing or session handling:

- Read the relevant `PROTOCOL.md` section and check it against your change.
- Unknown message types and fields are ignored, never fatal. Unparseable control payloads are
  ignored too.
- Hardening (rejecting a connection, truncating or validating a field, capping a size, dropping
  a message) must not drop a legitimate session or change what goes on the wire. When in doubt,
  degrade instead of reject.
- If a change deliberately diverges from the spec, say so in the PR, explain why in
  [`RATIONALE.md`](RATIONALE.md), and test it against the real Mac app (network switch,
  reconnect, rotation, sleep/wake).

## Tests and coverage

Pure logic (parsers, protocol constants, non-Android-framework classes) needs unit tests keeping
**line coverage at or above 95%** (enforced by `jacocoCoverageVerification` in CI). UI, services,
and anything requiring `MediaCodec`/sockets/`NsdManager` are excluded from that gate — see
`coverageExcludes` in `app/build.gradle.kts` for the exact list.

```sh
./gradlew testDebugUnitTest
./gradlew jacocoCoverageVerification
```

## Commit messages

Write them like you're explaining the change to a person, not a changelog generator — describe
*why*, not just *what*. Avoid generic prefixes like `chore: ...` with no substance behind them.

## Pull requests

1. Branch from `main`.
2. Keep the change focused — one thing per PR is easier to review and merge.
3. Make sure `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass locally
   (`./gradlew jacocoCoverageVerification` too, if you touched pure logic).
4. Open the PR against `main` — the template will ask for a short test plan.
5. CI (`tests.yml`) runs the same checks; a red check needs to go green before merge.

## License

By contributing, you agree your contribution is licensed under this project's
[GPL-3.0 license](LICENSE).
