# Native dependency audit

FFmpeg and libvpx are pinned submodules under `app/jni/third_party/`.
Initialize them with the other recursive submodules; generated objects and
local source-tree build outputs stay out of commits. The ARM64 wrapper
delegates to the upstream Gradle tasks and keeps generated objects under
`build/generated/tgx`, preventing stale cross-ABI objects.

Telegram X 1817 uses NDK r30 for Android 5+ and r23 for legacy builds.
TDLib and OpenSSL binaries are Git LFS assets selected by NDK revision,
Android API, and ABI. Fetch the actual LFS objects before compiling; a
pointer file is not a usable native library. Upstream's build validation
checks that submodules and native artifacts are available.

FFmpeg is configured with `--disable-everything` and currently retains the
codecs used by the client: H.264, MPEG-4, GIF, ALAC, AAC and libvpx VP9;
MOV, Matroska and GIF demuxers; file protocol, scale and overlay filters.
Re-run `scripts/setup-arm64-dev.sh` after changing either native checkout and
record resulting shared-library sizes in the release notes.
