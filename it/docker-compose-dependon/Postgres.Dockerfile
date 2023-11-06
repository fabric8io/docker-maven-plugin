FROM postgres:13-alpine
HEALTHCHECK --interval=5s --timeout=5s --retries=5 \
    CMD "pg_isready" "-U" "postgres"
