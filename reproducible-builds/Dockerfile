FROM ubuntu:24.04
ARG DEBIAN_FRONTEND=noninteractive
ARG CMDLINE_TOOLS=15859902
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/emulator:$PATH
RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jdk unzip ca-certificates git wget && rm -rf /var/lib/apt/lists/*
RUN mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" \
  && wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS}_latest.zip" -O /tmp/cmdline-tools.zip \
  && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools" \
  && mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest" \
  && rm /tmp/cmdline-tools.zip \
  && yes | sdkmanager --licenses >/dev/null
RUN sdkmanager "platforms;android-37" "build-tools;37.0.0" "cmake;3.22.1" "ndk;23.2.8568313"
WORKDIR /src
CMD ["bash", "-lc", "if [[ ! -f local.properties ]]; then cp local.properties.sample local.properties; fi; sed -i \"s|^sdk.dir=.*|sdk.dir=$ANDROID_SDK_ROOT|\" local.properties; sed -i \"s|^telegram.api_id=.*|telegram.api_id=${TELEGRAM_API_ID:-12345}|\" local.properties; sed -i \"s|^telegram.api_hash=.*|telegram.api_hash=${TELEGRAM_API_HASH:-00000000000000000000000000000000}|\" local.properties; ./gradlew :app:assembleLatestArm64Release --no-daemon --stacktrace"]
