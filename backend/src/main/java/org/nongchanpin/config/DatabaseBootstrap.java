package org.nongchanpin.config;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.nongchanpin.security.Passwords;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.util.StreamUtils;

@Slf4j
public class DatabaseBootstrap {
    private final DataSource dataSource;
    private final Environment environment;

    public DatabaseBootstrap(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    public void initialize() throws Exception {
        if (!environment.getProperty("trace.bootstrap.enabled", Boolean.class, true)) return;
        try (Connection connection = dataSource.getConnection()) {
            boolean mysql = connection.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
            String lock = "agri-bootstrap:" + connection.getCatalog();
            if (lock.length() > 64) lock = lock.substring(0, 64);
            if (mysql && scalar(connection, "SELECT GET_LOCK(?, 60)", lock) != 1) {
                throw new IllegalStateException("数据库初始化锁获取失败，请稍后重启。");
            }
            try {
                initializeSchema(connection);
                initializeAdministrator(connection);
                log.info("Database schema and initial account initialization completed; existing users preserved.");
            } finally {
                if (mysql) scalar(connection, "SELECT RELEASE_LOCK(?)", lock);
            }
        }
    }

    protected void initializeSchema(Connection connection) throws Exception {
        String sql;
        try (var input = new ClassPathResource("db/schema.sql").getInputStream()) {
            sql = StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        }
        // Always use the configured catalog, including custom DB_NAME; never switch to agri_trace.
        sql = sql.replaceFirst("(?is)^\\s*CREATE DATABASE[^;]*;\\s*USE[^;]*;", "");
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        populator.populate(connection);
    }

    void initializeAdministrator(Connection connection) throws SQLException {
        if (scalar(connection, "SELECT COUNT(*) FROM sys_user") > 0) return;
        String username = environment.getProperty("trace.bootstrap.admin-username", "admin");
        String password = environment.getProperty("trace.bootstrap.admin-password", "");
        if (!username.matches("[A-Za-z0-9_-]{3,64}") || password.isBlank()
                || password.length() < 12 || password.length() > 128) {
            throw new IllegalStateException("空用户库需要配置 INIT_ADMIN_USERNAME（3–64 位字母数字下划线或短横线）及 INIT_ADMIN_PASSWORD（12–128 位）；不会使用默认密码。");
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long role = scalar(connection, "SELECT id FROM sys_role WHERE role_code='ADMIN' AND status='ACTIVE'");
            if (role < 1) throw new IllegalStateException("ADMIN 角色缺失或已停用，无法初始化管理员。");
            long organization = scalar(connection, "SELECT id FROM sys_organization WHERE org_code='ORG-INITIAL' AND status='ACTIVE'");
            if (organization == 0) {
                execute(connection, "INSERT INTO sys_organization(org_code,org_name,org_type,status) VALUES('ORG-INITIAL','初始农场','FARM','ACTIVE')");
                organization = scalar(connection, "SELECT id FROM sys_organization WHERE org_code='ORG-INITIAL'");
            }
            execute(connection, "INSERT INTO sys_user(organization_id,username,password_hash,display_name,status) VALUES(?,?,?,'系统管理员','ACTIVE')",
                    organization, username, Passwords.hash(password));
            long user = scalar(connection, "SELECT id FROM sys_user WHERE username=?", username);
            execute(connection, "INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", user, role);
            connection.commit();
            log.info("Initial administrator created. Password was loaded from local configuration and was not logged.");
        } catch (SQLException | RuntimeException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private long scalar(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0; }
        }
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
    }
}
