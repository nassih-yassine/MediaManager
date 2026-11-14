FROM rust:1.99-bookworm AS build
WORKDIR /app
COPY crates/api/Cargo.toml crates/api/Cargo.lock ./crates/api/
COPY crates/api/src ./crates/api/src
COPY crates/api/migrations ./crates/api/migrations
RUN cargo build --release --manifest-path crates/api/Cargo.toml

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates curl ffmpeg nodejs poppler-utils python3 unzip \
    && curl -fsSL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp \
        -o /usr/local/bin/yt-dlp \
    && chmod +x /usr/local/bin/yt-dlp \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --uid 1000 --create-home --shell /usr/sbin/nologin mm
COPY --from=build /app/crates/api/target/release/mm /usr/local/bin/mm

ENV MM_LIBRARY_DIR=/library \
    MM_BIND=0.0.0.0:8787 \
    MM_WEB_ORIGIN=http://localhost:3001 \
    HOME=/home/mm

RUN mkdir -p /library && chown mm:mm /library
USER mm
EXPOSE 8787
VOLUME ["/library"]
ENTRYPOINT ["/usr/local/bin/mm"]
