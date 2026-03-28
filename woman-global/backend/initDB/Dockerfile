# Use the official PostgreSQL image as the base image
# FROM postgres:dev
FROM postgres:16.3


# Set environment variables
ENV POSTGRES_DB fem_hope
ENV POSTGRES_USER app_user1
ENV POSTGRES_PASSWORD DeusVult%21

# Install PostgreSQL extensions
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        postgresql-contrib \
        postgresql-16-pgvector \
        postgresql-16-cron \
        postgresql-16-postgis \
    && rm -rf /var/lib/apt/lists/*

# Optionally, you can include custom initialization scripts

COPY fem_hope_schema.sql /docker-entrypoint-initdb.d/
# Expose the PostgreSQL port
EXPOSE 5431