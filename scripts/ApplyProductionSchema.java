import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Run from repository root with the existing MySQL JDBC driver on the classpath. */
class ApplyProductionSchema {
    public static void main(String[] args) throws Exception {
        Map<String, String> config = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(".env"), StandardCharsets.UTF_8)) {
            if (line.trim().startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator < 1) continue;
            String value = line.substring(separator + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            config.put(line.substring(0, separator).trim(), value);
        }
        String name = config.get("DB_NAME");
        if (name == null || !name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid DB_NAME");
        String sql = Files.readString(Path.of("database/migrations/001_production_foundation.sql"), StandardCharsets.UTF_8)
                .replaceAll("(?m)^--.*$", "");
        String[] commands = sql.split(";");
        int count = 0;
        for (String command : commands) {
            if (command.trim().isEmpty()) continue;
            if (!command.trim().matches("(?s)CREATE TABLE IF NOT EXISTS (farm_plot|planting_cycle) \\(.*")) {
                throw new IllegalArgumentException("Only production foundation CREATE TABLE statements are allowed");
            }
            count++;
        }
        if (count != 2) throw new IllegalArgumentException("Expected exactly two table definitions");
        String url = "jdbc:mysql://" + config.get("DB_HOST") + ":" + config.get("DB_PORT") + "/" + name
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        // Do not log connection settings or JDBC exception text containing environment details.
        try (Connection connection = DriverManager.getConnection(url, config.get("DB_USERNAME"), config.get("DB_PASSWORD"));
                Statement statement = connection.createStatement()) {
            for (String command : commands) if (!command.trim().isEmpty()) statement.execute(command.trim());
            System.out.println("Production foundation schema applied: farm_plot, planting_cycle. No existing rows changed.");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Schema application failed; SQL state: " + exception.getSQLState()
                    + ", vendor code: " + exception.getErrorCode());
        }
    }
}
