# syntax=docker/dockerfile:1
#
# Reproducible Android build image for BSfreeTV (mirrors quantifyme-v2-android).
# JDK 21 + Android SDK (API 37 / Android 17) + Gradle 9.8.0.
#
FROM eclipse-temurin:21-jdk AS android-sdk

ARG CMDLINE_TOOLS_VERSION=16111833
ARG ANDROID_PLATFORM=android-37.0
ARG BUILD_TOOLS_VERSION=37.0.0
ARG GRADLE_VERSION=9.8.0

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/opt/gradle-home \
    DEBIAN_FRONTEND=noninteractive

# Base tooling
RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Android command-line tools
RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Accept licenses and install SDK packages
RUN yes | sdkmanager --licenses >/dev/null \
    && sdkmanager --install \
        "platform-tools" \
        "platforms;${ANDROID_PLATFORM}" \
        "build-tools;${BUILD_TOOLS_VERSION}"

# Gradle (used directly; there is no wrapper)
RUN curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && ln -s "/opt/gradle-${GRADLE_VERSION}/bin/gradle" /usr/local/bin/gradle \
    && rm /tmp/gradle.zip

# Gradle caches live on a named volume here (off the bind mount).
RUN mkdir -p "${GRADLE_USER_HOME}"

# We run as root so the container can always write to the bind-mounted project, regardless of the
# host user's uid. docker/run-gradle.sh restores host ownership of generated files afterwards, so you
# never end up with root-owned build artifacts.
WORKDIR /src/bsfreetv

CMD ["bash", "docker/run-gradle.sh", ":app:assembleDebug"]
