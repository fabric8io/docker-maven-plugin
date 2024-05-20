FROM postgres:16.3-alpine
HEALTHCHECK --interval=5s --timeout=5s --retries=5 \
    CMD "pg_isready" "-U" "postgres"
