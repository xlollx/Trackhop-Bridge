# Contributing to Trackhop Bridge

Thanks for helping. Issues and pull requests are welcome, from anyone.

- **A connector that stopped working:** open a [bug report](https://github.com/xlollx/Trackhop-Bridge/issues/new/choose)
  with Trackhop's technical details report.
- **A change to a connector (`android/bridge`, `android/ytm`):** make it in
  [Trackhop](https://github.com/xlollx/Trackhop) (see its CONTRIBUTING). These two folders are copies,
  synced here by `scripts/sync-from-trackhop.sh` at every Trackhop release, so the Google Play build
  of Trackhop gets exactly what the GitHub/F-Droid build has. A pull request that edits them here is
  moved to Trackhop.
- **The app around them (`android/app`), the build, the README:** a pull request here.
- **A question or an idea:** [Trackhop Discussions](https://github.com/xlollx/Trackhop/discussions).
- **A security problem:** never in a public issue. See [SECURITY.md](SECURITY.md).

Before a pull request: `./gradlew testDebugUnitTest :ytm:test assembleDebug` from `android/` passes,
no keys, tokens, cookies or traffic captures are in the commits, and you agree your change is released
under AGPL-3.0-only. Pull requests are merged with squash.
