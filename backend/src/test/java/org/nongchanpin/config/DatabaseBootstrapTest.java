package org.nongchanpin.config;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nongchanpin.security.Passwords;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

class DatabaseBootstrapTest {
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;
    private MockEnvironment environment;
    private DatabaseBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE sys_organization(id BIGINT AUTO_INCREMENT PRIMARY KEY,org_code VARCHAR(64) UNIQUE,org_name VARCHAR(128),org_type VARCHAR(32),status VARCHAR(32))");
        jdbc.execute("CREATE TABLE sys_role(id BIGINT AUTO_INCREMENT PRIMARY KEY,role_code VARCHAR(64),status VARCHAR(32))");
        jdbc.execute("CREATE TABLE sys_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,organization_id BIGINT,username VARCHAR(64) UNIQUE,password_hash VARCHAR(128),display_name VARCHAR(64),status VARCHAR(32))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT,UNIQUE(user_id,role_id))");
        jdbc.execute("INSERT INTO sys_role(role_code,status) VALUES('ADMIN','ACTIVE')");
        environment = new MockEnvironment().withProperty("trace.bootstrap.admin-password", "initial-test-password");
        bootstrap = new DatabaseBootstrap(source, environment);
    }

    @Test
    void emptyDatabaseGetsUsableAdministratorAndOrganization() throws Exception {
        try (Connection connection = source.getConnection()) { bootstrap.initializeAdministrator(connection); }
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user u JOIN sys_organization o ON o.id=u.organization_id JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE u.username='admin' AND u.status='ACTIVE' AND o.status='ACTIVE' AND r.role_code='ADMIN'", Long.class));
        assertTrue(Passwords.matches("initial-test-password", jdbc.queryForObject("SELECT password_hash FROM sys_user", String.class)));
    }

    @Test
    void restartingDoesNotResetPasswordOrDuplicateAccount() throws Exception {
        try (Connection connection = source.getConnection()) {
            bootstrap.initializeAdministrator(connection);
            String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user", String.class);
            environment.setProperty("trace.bootstrap.admin-password", "changed-config-password");
            bootstrap.initializeAdministrator(connection);
            assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM sys_user", String.class));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Long.class));
        }
    }

    @Test
    void existingUsersAreNotPromotedEvenWhenNoAdminExists() throws Exception {
        jdbc.execute("INSERT INTO sys_user(username,status) VALUES('existing-viewer','ACTIVE')");
        environment.setProperty("trace.bootstrap.admin-password", "");
        try (Connection connection = source.getConnection()) { bootstrap.initializeAdministrator(connection); }
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role", Long.class));
    }

    @Test
    void missingInitialPasswordFailsWithoutCreatingAccount() throws Exception {
        environment.setProperty("trace.bootstrap.admin-password", "");
        try (Connection connection = source.getConnection()) {
            assertThrows(IllegalStateException.class, () -> bootstrap.initializeAdministrator(connection));
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class));
    }

    @Test
    void failedRoleGrantRollsBackOrganizationAndUser() throws Exception {
        jdbc.execute("ALTER TABLE sys_user_role ADD CONSTRAINT reject_grant CHECK(user_id<0)");
        try (Connection connection = source.getConnection()) {
            assertThrows(java.sql.SQLException.class, () -> bootstrap.initializeAdministrator(connection));
            assertTrue(connection.getAutoCommit());
        }
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_organization", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user", Long.class));
    }

    @Test
    void schemaIsPackagedAndInitializationCanBeDisabled() throws Exception {
        assertTrue(new ClassPathResource("db/schema.sql").exists());
        environment.setProperty("trace.bootstrap.enabled", "false");
        new DatabaseBootstrap(null, environment).initialize();
    }
}
