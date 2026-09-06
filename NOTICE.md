# Notices and attribution

Ratogram X is free software distributed under the GNU General Public License
version 3. See [`LICENSE`](LICENSE).

## Telegram X

Ratogram X is based on the Telegram X Android source code:

- Project: <https://github.com/TGX-Android/Telegram-X>
- License: GNU GPL version 3

Copyright and license notices present in the Telegram X source tree are
retained. Ratogram X is not an official Telegram X build.

## moeGramX

Ratogram X incorporates and adapts selected source code and features from
moeGramX:

- Project: <https://github.com/moeCrafters/moeGramX>
- License: GNU GPL version 3

The `moe.kirao.mgx` package name is retained in parts of the source tree to
make provenance visible and to reduce unnecessary churn.

## AyuGram4A

The experimental local Premium compatibility layer was independently
implemented using AyuGram4A's Local Telegram Premium feature as a public
design reference:

- Project: <https://github.com/AyuGram/AyuGram4A>
- Reference revision: `7013145676d36d82ee13c02a89f72097b7490dcd`
- Feature author credited by the referenced source: Radolyn
- Reference project license: GNU GPL version 2

No AyuGram4A source code is copied into this implementation. The resulting
feature is client-only and does not grant or emulate Telegram server
permissions.

## TDLib and Telegram

Telegram communication is implemented through TDLib and inherited Telegram X
integration. TDLib and other Telegram components retain their respective
notices and licenses.

Ratogram X is independent and is not affiliated with Telegram Messenger Inc.
Telegram names and marks belong to their respective owners.

## Third-party software

This repository contains submodules and vendored components under multiple
open-source licenses. Their notices remain in the corresponding directories.
The inherited overview is available in
[`docs/THIRDPARTY.md`](docs/THIRDPARTY.md).

Nothing in this notice changes or replaces the terms of an individual
component's license.
