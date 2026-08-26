# syntax=docker/dockerfile:1.7

ARG NODE_IMAGE=node:24-bookworm-slim

FROM ${NODE_IMAGE} AS dependencies
ENV PNPM_HOME=/pnpm
ENV PATH=${PNPM_HOME}:${PATH}
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile --filter @wall-street-receipts/web...

FROM dependencies AS build
ENV NEXT_TELEMETRY_DISABLED=1 \
    NEXT_PUBLIC_DATA_MODE=DEMO \
    CALL_AUDIT_PROVIDER=api \
    API_BASE_URL=http://api:8080 \
    MARKET_PROVIDER=fixture \
    ANALYST_PROVIDER=fixture \
    SP500_HISTORY_PROVIDER=fixture \
    MARKET_BOARD_PROVIDER=fixture \
    METHODOLOGY_PROVIDER=fixture \
    INSTITUTION_DIRECTORY_PROVIDER=fixture \
    ANALYST_DIRECTORY_PROVIDER=fixture \
    MARKET_MAP_PROVIDER=fixture \
    MARKET_TREEMAP_PROVIDER=fixture \
    MACRO_PROVIDER=fixture \
    MEDIA_PROVIDER=fixture
COPY apps/web apps/web
COPY fixtures/v1 fixtures/v1
RUN mkdir -p apps/web/public \
    && pnpm --dir apps/web build

FROM ${NODE_IMAGE} AS production-dependencies
ENV PNPM_HOME=/pnpm
ENV PATH=${PNPM_HOME}:${PATH}
WORKDIR /workspace
RUN corepack enable && corepack prepare pnpm@11.19.0 --activate
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/package.json
RUN pnpm install --frozen-lockfile --prod --filter @wall-street-receipts/web...

FROM ${NODE_IMAGE} AS runtime
ARG WSR_GIT_SHA=local
LABEL org.opencontainers.image.title="Wall Street Receipts Web" \
      org.opencontainers.image.revision="${WSR_GIT_SHA}"
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    HOSTNAME=0.0.0.0 \
    PORT=3000
WORKDIR /workspace
COPY --from=production-dependencies --chown=node:node /workspace/node_modules node_modules
COPY --from=production-dependencies --chown=node:node /workspace/apps/web/node_modules apps/web/node_modules
COPY --chown=node:node package.json pnpm-workspace.yaml ./
COPY --chown=node:node apps/web/package.json apps/web/package.json
COPY --from=build --chown=node:node /workspace/apps/web/.next apps/web/.next
COPY --from=build --chown=node:node /workspace/apps/web/public apps/web/public
USER node
WORKDIR /workspace/apps/web
EXPOSE 3000
CMD ["node_modules/.bin/next", "start", "--hostname", "0.0.0.0", "--port", "3000"]
