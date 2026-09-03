for path in \
  deploy/home-server/preflight.sh \
  deploy/home-server/compose-production.sh \
  deploy/home-server/recovery-common.sh \
  deploy/home-server/recovery-preflight.sh \
  deploy/home-server/recovery-production.sh \
  deploy/home-server/schema-compatibility.sh \
  deploy/home-server/generation-promotion.sh \
  deploy/home-server/generation-state.sh \
  deploy/home-server/server-facts.sh \
  scripts/verify-home-server-database-evidence.sh \
  scripts/verify-home-server-schema-compatibility.sh \
  scripts/verify-home-server-generation-promotion.sh \
  scripts/verify-home-server-generation-state.sh \
  scripts/verify-home-server-server-facts.sh \
  scripts/verify-home-server-retention.sh
do
  bash -n "$path"
done
