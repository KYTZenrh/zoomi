# Zoomi sync server: PocketBase (MIT) + Zoomi migrations/hooks.
# Multi-arch (amd64 for a PC, arm64 for Oracle Ampere A1). The binary is checksum-verified.
FROM alpine:3.22

ARG PB_VERSION=0.40.6
ARG TARGETARCH
# From https://github.com/pocketbase/pocketbase/releases/download/v0.40.6/checksums.txt
ARG PB_SHA256_AMD64=0e1f609158ed945f809f8a02ae012aa0427129c66ebb63c1aee76f2c0309373f
ARG PB_SHA256_ARM64=d22c66ceb67ca92b7d767f36d826e848a276bcfa3c197ead20bf87c42208984f

RUN apk add --no-cache ca-certificates unzip wget \
 && ARCH="${TARGETARCH:-amd64}" \
 && SHA="$([ "$ARCH" = arm64 ] && echo $PB_SHA256_ARM64 || echo $PB_SHA256_AMD64)" \
 && wget -q -O /tmp/pb.zip "https://github.com/pocketbase/pocketbase/releases/download/v${PB_VERSION}/pocketbase_${PB_VERSION}_linux_${ARCH}.zip" \
 && echo "$SHA  /tmp/pb.zip" | sha256sum -c - \
 && unzip -q /tmp/pb.zip pocketbase -d /pb \
 && rm /tmp/pb.zip \
 && adduser -D -H -u 10001 zoomi \
 && mkdir -p /pb/pb_data && chown zoomi /pb/pb_data

COPY pb_migrations /pb/pb_migrations
COPY pb_hooks /pb/pb_hooks

USER zoomi
WORKDIR /pb
EXPOSE 8090
VOLUME /pb/pb_data
# Admin dashboard (/_/) is only reachable if you expose it; see README for the superuser setup.
CMD ["/pb/pocketbase", "serve", "--http=0.0.0.0:8090", "--dir=/pb/pb_data", "--migrationsDir=/pb/pb_migrations", "--hooksDir=/pb/pb_hooks", "--automigrate=false"]
