package com.documind.backend.repository;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL-only integration test. The auth schema below is a minimal test double for Supabase
 * and does not prove that policies work unchanged in a hosted Supabase project.
 */
class DatabaseMigrationIntegrationTests {

    private static DatabaseCredentials credentials;

    @BeforeAll
    static void requireDatabaseAndInstallSupabaseAuthTestDouble() throws SQLException {
        credentials = credentialsFromEnvironment();
        requireDedicatedTestDatabase(credentials.url());
        bootstrapSupabaseAuthTestDouble(credentials);
    }

    @Test
    void migrationIntegrityRlsAndRolePolicyAreEnforced() throws Exception {
        Flyway flyway = Flyway.configure()
            .dataSource(credentials.url(), credentials.username(), credentials.password())
            .locations("classpath:db/migration")
            .load();

        flyway.migrate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(singleInt("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success"))
            .isEqualTo(1);

        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID documentA = UUID.randomUUID();
        UUID documentB = UUID.randomUUID();
        UUID activeJob = UUID.randomUUID();
        UUID completedJob = UUID.randomUUID();

        try (Connection connection = open()) {
            insertUser(connection, userA);
            insertUser(connection, userB);
            insertDocument(connection, documentA, userA, "upload-" + UUID.randomUUID());
            insertDocument(connection, documentB, userB, "upload-" + UUID.randomUUID());
            insertJob(connection, activeJob, documentA, userA, "PENDING", 1, null);

            assertThatThrownBy(() -> insertJob(
                connection, UUID.randomUUID(), documentA, userA, "PENDING", 2, null
            )).isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> insertJob(
                connection, UUID.randomUUID(), documentA, userB, "COMPLETED", 3, OffsetDateTime.now()
            )).isInstanceOf(SQLException.class);

            failActiveJob(connection, activeJob);
            insertJob(connection, completedJob, documentA, userA, "COMPLETED", 2, OffsetDateTime.now());
            insertResult(connection, UUID.randomUUID(), completedJob, documentA, userA);

            assertThatThrownBy(() -> insertResult(
                connection, UUID.randomUUID(), completedJob, documentA, userA
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertResult(
                connection, UUID.randomUUID(), completedJob, documentB, userA
            )).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertResult(
                connection, UUID.randomUUID(), completedJob, documentA, userB
            )).isInstanceOf(SQLException.class);
        }

        assertThat(visibleDocumentCount(userA, documentA)).isEqualTo(1);
        assertThat(visibleDocumentCount(userB, documentA)).isZero();
        assertThat(visibleDocumentCount(userB, documentB)).isEqualTo(1);
        assertThat(visibleDocumentCount(null, documentA)).isZero();

        assertRoleSecurityProperties();
        assertCascadeDelete(userA, userB);
    }

    private static void bootstrapSupabaseAuthTestDouble(DatabaseCredentials database) throws SQLException {
        try (Connection connection = open(database); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS auth");
            statement.execute("DO $$ BEGIN CREATE ROLE authenticated NOLOGIN NOSUPERUSER NOBYPASSRLS; "
                + "EXCEPTION WHEN duplicate_object THEN NULL; END $$");
            statement.execute("DO $$ BEGIN CREATE ROLE documind_runtime NOLOGIN NOSUPERUSER NOBYPASSRLS; "
                + "EXCEPTION WHEN duplicate_object THEN NULL; END $$");
            statement.execute("GRANT authenticated TO documind_runtime");
            statement.execute("CREATE TABLE IF NOT EXISTS auth.users (id uuid PRIMARY KEY)");
            statement.execute("CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$ "
                + "SELECT nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$");
            statement.execute("GRANT USAGE ON SCHEMA auth TO authenticated");
            statement.execute("GRANT EXECUTE ON FUNCTION auth.uid() TO authenticated");
        }
    }

    private void assertRoleSecurityProperties() throws SQLException {
        try (Connection connection = open()) {
            assertThat(roleFlag(connection, "authenticated", "rolbypassrls")).isFalse();
            assertThat(roleFlag(connection, "authenticated", "rolsuper")).isFalse();
            assertThat(roleFlag(connection, "documind_runtime", "rolbypassrls")).isFalse();
            assertThat(roleFlag(connection, "documind_runtime", "rolsuper")).isFalse();
            assertThat(booleanQuery(connection,
                "SELECT pg_has_role('documind_runtime', 'authenticated', 'MEMBER')")).isTrue();
            assertThat(booleanQuery(connection, """
                SELECT has_table_privilege('authenticated', 'public.documents', 'SELECT')
                   AND has_table_privilege('authenticated', 'public.documents', 'INSERT')
                   AND has_table_privilege('authenticated', 'public.documents', 'UPDATE')
                   AND has_table_privilege('authenticated', 'public.documents', 'DELETE')
                """))
                .isTrue();
            assertThat(booleanQuery(connection,
                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname = current_user"))
                .as("CI migration account is privileged and must never be the application runtime account")
                .isTrue();
        }
    }

    private void assertCascadeDelete(UUID deletedUser, UUID remainingUser) throws SQLException {
        try (Connection connection = open(); PreparedStatement delete = connection.prepareStatement(
            "DELETE FROM auth.users WHERE id = ?"
        )) {
            delete.setObject(1, deletedUser);
            assertThat(delete.executeUpdate()).isEqualTo(1);
        }
        assertThat(singleInt("SELECT count(*) FROM public.documents WHERE owner_id = '" + deletedUser + "'"))
            .isZero();
        assertThat(singleInt("SELECT count(*) FROM public.analysis_jobs WHERE owner_id = '" + deletedUser + "'"))
            .isZero();
        assertThat(singleInt("SELECT count(*) FROM public.analysis_results WHERE owner_id = '" + deletedUser + "'"))
            .isZero();
        assertThat(singleInt("SELECT count(*) FROM public.documents WHERE owner_id = '" + remainingUser + "'"))
            .isEqualTo(1);
    }

    private void insertUser(Connection connection, UUID userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO auth.users (id) VALUES (?)")) {
            statement.setObject(1, userId);
            statement.executeUpdate();
        }
    }

    private void insertDocument(Connection connection, UUID documentId, UUID ownerId, String uploadKey)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO public.documents (
                id, owner_id, title, original_filename, storage_object_path,
                upload_idempotency_key, upload_request_fingerprint, mime_type, size_bytes, page_count
            ) VALUES (?, ?, 'Test document', 'test.pdf', ?, ?, 'fingerprint', 'application/pdf', 1024, 1)
            """)) {
            statement.setObject(1, documentId);
            statement.setObject(2, ownerId);
            statement.setString(3, ownerId + "/" + documentId + "/source.pdf");
            statement.setString(4, uploadKey);
            statement.executeUpdate();
        }
    }

    private void insertJob(
        Connection connection,
        UUID jobId,
        UUID documentId,
        UUID ownerId,
        String status,
        int attemptNumber,
        OffsetDateTime completedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO public.analysis_jobs (
                id, document_id, owner_id, status, attempt_number, idempotency_key,
                request_fingerprint, retry_count, max_retries, deadline_at, completed_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'fingerprint', 0, 2, now() + interval '10 minutes', ?)
            """)) {
            statement.setObject(1, jobId);
            statement.setObject(2, documentId);
            statement.setObject(3, ownerId);
            statement.setString(4, status);
            statement.setInt(5, attemptNumber);
            statement.setString(6, "analysis-" + UUID.randomUUID());
            statement.setObject(7, completedAt);
            statement.executeUpdate();
        }
    }

    private void failActiveJob(Connection connection, UUID jobId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE public.analysis_jobs
            SET status = 'FAILED', failure_code = 'TEST', failure_message = 'Test failure', completed_at = now()
            WHERE id = ?
            """)) {
            statement.setObject(1, jobId);
            statement.executeUpdate();
        }
    }

    private void insertResult(
        Connection connection,
        UUID resultId,
        UUID jobId,
        UUID documentId,
        UUID ownerId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO public.analysis_results (
                id, analysis_job_id, document_id, owner_id, result, model_name, pipeline_version
            ) VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'test-model', 'test-pipeline')
            """)) {
            statement.setObject(1, resultId);
            statement.setObject(2, jobId);
            statement.setObject(3, documentId);
            statement.setObject(4, ownerId);
            statement.setString(5,
                "{\"schemaVersion\":\"1.0\",\"summary\":{},\"keyPoints\":[],\"keywords\":[],\"warnings\":[]}");
            statement.executeUpdate();
        }
    }

    private int visibleDocumentCount(UUID authenticatedUser, UUID documentId) throws SQLException {
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET LOCAL ROLE authenticated");
                }
                if (authenticatedUser != null) {
                    try (PreparedStatement claim = connection.prepareStatement(
                        "SELECT set_config('request.jwt.claim.sub', ?, true)"
                    )) {
                        claim.setString(1, authenticatedUser.toString());
                        claim.execute();
                    }
                }
                try (PreparedStatement query = connection.prepareStatement(
                    "SELECT count(*) FROM public.documents WHERE id = ?"
                )) {
                    query.setObject(1, documentId);
                    try (ResultSet result = query.executeQuery()) {
                        result.next();
                        return result.getInt(1);
                    }
                }
            } finally {
                connection.rollback();
            }
        }
    }

    private boolean roleFlag(Connection connection, String role, String column) throws SQLException {
        if (!column.equals("rolbypassrls") && !column.equals("rolsuper")) {
            throw new IllegalArgumentException("Unsupported role flag");
        }
        return booleanQuery(connection, "SELECT " + column + " FROM pg_roles WHERE rolname = '" + role + "'");
    }

    private boolean booleanQuery(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private int singleInt(String sql) throws SQLException {
        try (Connection connection = open(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }

    private static Connection open() throws SQLException {
        return open(credentials);
    }

    private static Connection open(DatabaseCredentials database) throws SQLException {
        return DriverManager.getConnection(database.url(), database.username(), database.password());
    }

    private static DatabaseCredentials credentialsFromEnvironment() {
        String url = System.getenv("TEST_DATABASE_URL");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                "TEST_DATABASE_URL is required for databaseIntegrationTest; this test must not be skipped"
            );
        }
        return new DatabaseCredentials(
            url,
            System.getenv().getOrDefault("TEST_DATABASE_USERNAME", "postgres"),
            System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "postgres")
        );
    }

    private static void requireDedicatedTestDatabase(String jdbcUrl) {
        String path = URI.create(jdbcUrl.substring("jdbc:".length())).getPath();
        if (path == null || !path.endsWith("_test")) {
            throw new IllegalStateException("TEST_DATABASE_URL must target a database ending in _test");
        }
    }

    private record DatabaseCredentials(String url, String username, String password) {
    }
}
