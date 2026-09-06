# Updating Telegram X

Use a dedicated integration branch and commit application changes first.
Inspect dirty submodules individually and preserve local native outputs.

1. Fetch upstream and select a reviewed release tag or commit.
2. Run bash scripts/upstream-diff.sh with that reference.
3. Create merge/upstream-VERSION and merge with --no-ff --no-commit and
   submodule.recurse=false.
4. Review conflicts and automatic merges using the checklist below.
5. Update only submodule revisions changed in the upstream range, after
   preserving locally modified sources and build outputs.
6. Run repository hygiene checks, Java compilation, JVM tests and ARM64 dev
   assemble before committing the reviewed integration.

## Hook checklist

- Tdlib.java, TdlibAccount.java, TdlibUi.java: local Premium versus server
  permissions, account isolation and explicit user actions.
- MessagesController.java, MessageView.java, MessagesLoader.java: topics,
  partial quotes and channel bottom actions.
- ChatsController.java, TdlibChatListSlice.java, TGChat.java: proxy sponsor
  filtering, local pins and unread counts.
- SearchManager.java, TdlibFilesManager.java, FileProgressComponent.java:
  download indexing, fallback completeness and technical-file exclusion.
- DrawerController.java, DrawerHeaderView.java: configurable entries.
- Settings.java, SettingsController.java, SettingsMoexController.java:
  global and individual experiment flags and version toast.
- Lang.java and moex_strings.xml: selected app language and EN/RU parity.
- app/build.gradle.kts, JNI and buildSrc: dev package, signing, source links
  and native task dependencies.

## Integration: 1807

Target: v0.28.11.1807 (95dc291d). TDLib revision is unchanged from 1806.
Upstream moves AndroidX Media into app/thirdparty, updates FFmpeg/libvpx/Opus
and replaces native shell scripts with Gradle tasks. The Ratogram ARM64
setup wrapper must be adapted. Conflicts and validation will be recorded
here during integration.
