FROM docker.io/library/debian:bookworm-slim@sha256:3783cc01769c7b2b1b83a5c5ad96c815348e28ed7da68e2e3687004faa906251

ARG PZ_UID=1000
ARG PZ_GID=1000

ENV STEAMCMD_DIR=/steamcmd \
    PZ_DIR=/pzserver \
    HOME=/home/pzuser \
    DEBIAN_FRONTEND=noninteractive

# Project Zomboid's dedicated server binaries are 32-bit-linked in places,
# so steamcmd needs the i386 runtime libs even on an amd64 host.
RUN dpkg --add-architecture i386 && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        lib32gcc-s1 \
        lib32stdc++6 \
        locales \
        tzdata && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd -g ${PZ_GID} pzuser && \
    useradd -m -u ${PZ_UID} -g ${PZ_GID} -d ${HOME} -s /bin/bash pzuser

# Pre-create mount points owned by pzuser so Docker's volume "copy up"
# preserves correct ownership when named volumes are attached at runtime.
COPY vendor/steamcmd_linux.tar.gz /tmp/steamcmd_linux.tar.gz
RUN mkdir -p ${STEAMCMD_DIR} ${PZ_DIR} ${HOME}/Zomboid && \
    tar zxf /tmp/steamcmd_linux.tar.gz -C ${STEAMCMD_DIR} && \
    rm /tmp/steamcmd_linux.tar.gz && \
    chown -R pzuser:pzuser ${STEAMCMD_DIR} ${PZ_DIR} ${HOME}

USER pzuser
WORKDIR ${PZ_DIR}

COPY --chown=pzuser:pzuser entrypoint.sh /home/pzuser/entrypoint.sh
RUN chmod +x /home/pzuser/entrypoint.sh

# Game traffic + RCON. (Steam auth for players who own the game happens
# client-side; the dedicated server itself needs no Steam license.)
EXPOSE 16271/udp 16272/udp 27025/tcp

ENTRYPOINT ["/home/pzuser/entrypoint.sh"]
