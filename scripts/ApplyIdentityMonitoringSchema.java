import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.security.SecureRandom;
import java.util.*;
import org.nongchanpin.security.Passwords;

/** Run from repository root, classpath: backend/target/classes and MySQL JDBC driver. */
class ApplyIdentityMonitoringSchema {
    public static void main(String[] args) throws Exception {
        Map<String, String> config = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(".env"), StandardCharsets.UTF_8)) {
            if (line.trim().startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator < 1) continue;
            String value = line.substring(separator + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
            config.put(line.substring(0, separator).trim(), value);
        }
        String name = config.get("DB_NAME");
        if (name == null || !name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid database name");
        String sql = Files.readString(Path.of("database/migrations/002_identity_monitoring.sql"), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
        List<String> commands = new ArrayList<>();
        for (String part : sql.split(";")) {
            String command = part.trim();
            if (command.isEmpty()) continue;
            if (!command.matches("(?s)CREATE TABLE IF NOT EXISTS (operation_audit|device_ownership|monitoring_binding|reading_provenance|window_provenance) \\(.*")
                    && !command.startsWith("INSERT INTO sys_role (role_code, role_name, status) VALUES")) throw new IllegalArgumentException("Unexpected migration command");
            commands.add(command);
        }
        if (commands.size() != 6) throw new IllegalArgumentException("Expected six migration statements");
        String url = "jdbc:mysql://" + config.get("DB_HOST") + ":" + config.get("DB_PORT") + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        try (Connection connection = DriverManager.getConnection(url, config.get("DB_USERNAME"), config.get("DB_PASSWORD")); Statement statement = connection.createStatement()) {
            for (String command : commands) statement.execute(command);
            System.out.println("Identity and monitoring tables/roles ready. Existing business data was not reassigned.");
            if (!Arrays.asList(args).contains("--bootstrap")) return;
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM sys_user")) {
                rows.next();
                if (rows.getInt(1) != 0) { System.out.println("Bootstrap skipped: accounts already exist; no account changed."); return; }
            }
            Long organizationId = null;
            try (ResultSet rows = statement.executeQuery("SELECT id FROM sys_organization WHERE status = 'ACTIVE' ORDER BY id LIMIT 1")) {
                if (rows.next()) organizationId = rows.getLong(1);
            }
            if (organizationId == null) throw new IllegalStateException("Create an active organization before bootstrapping an account");
            Path credentials = Path.of(".local", "initial-admin.json");
            Files.createDirectories(credentials.getParent());
            if (Files.exists(credentials)) throw new IllegalStateException("Initial credential file exists; no account was changed");
            byte[] random = new byte[24]; new SecureRandom().nextBytes(random);
            String password = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            connection.setAutoCommit(false);
            try {
                long userId;
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sys_user (organization_id, username, password_hash, display_name, status) VALUES (?, 'local-admin', ?, '本地管理员', 'ACTIVE')", Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, organizationId); insert.setString(2, Passwords.hash(password)); insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) { keys.next(); userId = keys.getLong(1); }
                }
                try (PreparedStatement role = connection.prepareStatement("INSERT INTO sys_user_role (user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code = 'ADMIN' AND status = 'ACTIVE'")) {
                    role.setLong(1, userId); if (role.executeUpdate() != 1) throw new IllegalStateException("Administrator role is unavailable");
                }
                Files.writeString(credentials, "{\n  \"username\": \"local-admin\",\n  \"password\": \"" + password + "\"\n}\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                connection.commit();
                System.out.println("Initial administrator created. Credentials are stored only in ignored .local/initial-admin.json; change the password after login.");
            } catch (Exception exception) { connection.rollback(); throw new IllegalStateException("Account initialization failed; no credentials logged"); }
        } catch (SQLException exception) {
            throw new IllegalStateException("Database operation failed; SQL state: " + exception.getSQLState() + ", code: " + exception.getErrorCode());
        }
    }
}
