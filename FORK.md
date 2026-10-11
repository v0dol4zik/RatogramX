# Project lineage

Ratogram X is hosted in the moeGramX GitHub fork network. Its active `main`
branch is based on the Telegram X Android source tree and incorporates selected
moeGramX code as a source integration.

The current Telegram X base is
[`v0.29.1.1817`](https://github.com/TGX-Android/Telegram-X/releases/tag/v0.29.1.1817).
Integration decisions are recorded in [docs/UPSTREAM_MERGE.md](docs/UPSTREAM_MERGE.md).

## Upstream projects

1. [Telegram X](https://github.com/TGX-Android/Telegram-X) provides the main
   Android application, Telegram X interface, build system, and TDLib-based
   architecture.
2. [moeGramX](https://github.com/moeCrafters/moeGramX) provides an extended
   GPL-3.0 feature layer. Ratogram X incorporates and adapts selected moeGramX
   settings, media actions, profile features, interface options, proxy/VPN
   behavior, and chat quality-of-life improvements.
3. Ratogram X adds its own branding, package identity, launcher icon system,
   search interface, profile-management flows, low-end device defaults,
   notification artwork, theme work, and further integrations.

The Ratogram X work began from Telegram X `v0.28.11.1806`. The incorporation
of moeGramX functionality on `main` was performed as a source integration
rather than by merging moeGramX's Git ancestry. Consequently, Ratogram X is a
GitHub fork of moeGramX whose active development branch preserves Telegram X
ancestry while clearly crediting the moeGramX code it adapts.

## Recommended remotes

Maintainers should use distinct remotes so the provenance is unambiguous:

```text
origin      Ratogram X public repository
upstream    https://github.com/TGX-Android/Telegram-X.git
moegramx    https://github.com/moeCrafters/moeGramX.git
```

Upstream updates should be reviewed and integrated deliberately. Telegram X
and moeGramX may target different source revisions, so blind merges can break
TDLib assumptions, resources, generated code, or the Telegram X interaction
model.

## Ratogram X-specific areas

The main fork-owned areas currently include:

- `app/src/main/java/moe/kirao/mgx/` — incorporated and adapted moeGramX
  functionality;
- `app/src/main/java/org/thunderdog/challegram/util/AppIconManager.java` and
  related icon resources;
- `SearchTabsView` and search integration;
- `EditProfileController` and `ProfileChannelController`;
- `LowEndPerformanceProfile.kt`;
- Ratogram X strings, identifiers, themes, launcher artwork, and notification
  artwork;
- ARM64-focused development setup scripts.

This list is descriptive, not a replacement for Git history or per-file
copyright notices.

## Compatibility policy

Ratogram X aims to preserve:

- Telegram X navigation and visual conventions;
- TDLib as the authoritative Telegram data and networking layer;
- compatibility with upstream-generated sources and submodules;
- GPL-3.0 license obligations and third-party notices.

Features originating in another project should keep meaningful attribution in
commit messages, documentation, and source headers where appropriate.
