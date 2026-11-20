# Flyway migrations

Flyway scans this classpath location. V1 creates the complete initial schema for the MySQL 8.4
production database and does not drop existing tables.

Before startup, use a dedicated, database-scoped migration/application user rather than root.
`application.properties` keeps Flyway enabled, validates migrations and disables Flyway clean.
