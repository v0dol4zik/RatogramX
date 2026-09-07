# Native dependency audit

Ratogram X provisions FFmpeg and libvpx source trees locally under
`app/jni/third_party/`; these directories are intentionally ignored by Git.
The ARM64 wrapper delegates to the upstream Gradle tasks and keeps generated
objects under `build/generated/tgx`, preventing stale cross-ABI objects.

FFmpeg is configured with `--disable-everything` and currently retains the
codecs used by the client: H.264, MPEG-4, GIF, ALAC, AAC and libvpx VP9;
MOV, Matroska and GIF demuxers; file protocol, scale and overlay filters.
Re-run `scripts/setup-arm64-dev.sh` after changing either native checkout and
record resulting shared-library sizes in the release notes.
