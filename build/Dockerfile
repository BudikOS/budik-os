# SPDX-License-Identifier: GPL-3.0-only
# Budik OS GSI build environment (Ubuntu 24.04, same packages as the RestlessOS CI).
# Build with the host user's ids so files in BUDIK_ROOT stay owned by that user:
#   docker build --build-arg UID="$(id -u)" --build-arg GID="$(id -g)" -t budikos-build:24.04 build/
FROM ubuntu:24.04

ARG UID=1000
ARG GID=1000
ENV DEBIAN_FRONTEND=noninteractive LANG=C.UTF-8 LC_ALL=C.UTF-8

RUN apt-get update && apt-get install -y --no-install-recommends \
      bc bison build-essential ca-certificates ccache curl file flex fontconfig \
      g++-multilib gcc-multilib git git-lfs gnupg gperf imagemagick jq less \
      lib32readline-dev lib32z1-dev libncurses6 libssl-dev libxml2 libxml2-utils lz4 \
      lzop openjdk-11-jdk openssl pngcrush procps python-is-python3 python3 rsync \
      schedtool squashfs-tools unzip wget xsltproc xz-utils zip zlib1g-dev zsync \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL -o /usr/local/bin/repo https://storage.googleapis.com/git-repo-downloads/repo \
    && chmod 755 /usr/local/bin/repo

# ubuntu:24.04 ships the user "ubuntu" (1000:1000); give it the host ids
RUN groupmod -o -g "$GID" ubuntu && usermod -o -u "$UID" -g "$GID" ubuntu

USER ubuntu
RUN git config --global user.name "Budik OS Builder" \
    && git config --global user.email "info@budikstore.org" \
    && git config --global color.ui false \
    && git config --global safe.directory '*' \
    && git lfs install

WORKDIR /src
