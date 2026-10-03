FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates cron && rm -rf /var/lib/apt/lists/*

COPY --chmod=755 build/bin/linuxX64/releaseExecutable/newapi-give-and-take.kexe /usr/local/bin/newapi-give-and-take
COPY --chmod=755 docker/entrypoint.sh /usr/local/bin/docker-entrypoint

WORKDIR /root/newapi-give-and-take
ENTRYPOINT ["/usr/local/bin/docker-entrypoint"]
