# syntax=docker/dockerfile:1.7
# Explicit locally built, reviewed Web runtime base; no public-service changes.
ARG WSR_WEB_IMAGE
FROM ${WSR_WEB_IMAGE}
ARG WSR_GIT_SHA
LABEL org.opencontainers.image.title="Wall Street Receipts local CPI operator" \
      org.opencontainers.image.revision="${WSR_GIT_SHA}"
COPY --chown=node:node apps/web/operator/start.mjs apps/web/operator/gateway.ts ./operator/
COPY --chown=node:node apps/web/src/lib/operator-cpi.ts ./src/lib/operator-cpi.ts
USER node
STOPSIGNAL SIGTERM
CMD ["node", "operator/start.mjs"]
