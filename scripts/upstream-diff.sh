#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
reference="${1:-upstream/main}"
git rev-parse --verify "${reference}^{commit}" >/dev/null
paths=(
  app/src/main/java/org/thunderdog/challegram/telegram/{Tdlib,TdlibAccount,TdlibUi,TdlibChatListSlice,TdlibFilesManager}.java
  app/src/main/java/org/thunderdog/challegram/ui/{MessagesController,ChatsController,SettingsController}.java
  app/src/main/java/org/thunderdog/challegram/component/chat/{MessageView,MessagesLoader}.java
  app/src/main/java/org/thunderdog/challegram/component/dialogs/SearchManager.java
  app/src/main/java/org/thunderdog/challegram/navigation/{DrawerController,DrawerHeaderView}.java
  app/src/main/java/org/thunderdog/challegram/unsorted/Settings.java
  app/src/main/java/org/thunderdog/challegram/core/Lang.java
  app/src/main/java/moe/kirao/mgx
  app/src/main/res/values{,-ru}/moex_strings.xml
  app/build.gradle.kts app/jni/CMakeLists.txt buildSrc
)
git diff "$reference" -- "${paths[@]}"
