FROM caddy:2.11.4-alpine AS runtime

ARG WSR_GIT_SHA=unknown
LABEL org.opencontainers.image.revision=$WSR_GIT_SHA

# Empty named volumes inherit this ownership on first creation, allowing the
# numeric non-root runtime user to persist ACME state without a root entrypoint.
RUN chown 65532:65532 /data /config \
    && chmod 0700 /data /config

USER 65532:65532
