import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Apply transport plan tables, backfills and view; run from repository root with the JDBC driver. */
class ApplyTransportPlanSchema {
    public static void main(String[] args) throws Exception {
        Map<String,String> config = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(".env"), StandardCharsets.UTF_8)) {
            if (line.trim().startsWith("#")) continue;
            int separator = line.indexOf('='); if (separator < 1) continue;
            String value = line.substring(separator + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
            config.put(line.substring(0, separator).trim(), value);
        }
        String name = config.get("DB_NAME"); if (name == null || !name.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid database name");
        String sql = Files.readString(Path.of("database/migrations/023_transport_plan_history.sql"), StandardCharsets.UTF_8);
        List<String> commands = new ArrayList<>();
        for (String part : sql.split(";")) {
            String command = part.trim(); if (command.isEmpty()) continue;
            if (!(command.matches("(?s)CREATE TABLE IF NOT EXISTS (transport_plan_origin|transport_plan_action|transport_code_registry) \\(.*") || command.startsWith("INSERT INTO transport_plan_origin(") || command.startsWith("INSERT INTO transport_code_registry(") || command.startsWith("CREATE OR REPLACE VIEW transport_current_plan AS"))) throw new IllegalArgumentException("Unexpected migration statement");
            commands.add(command);
        }
        if (commands.size() != 6) throw new IllegalArgumentException("Expected three tables, two backfills and one plan view");
        String url = "jdbc:mysql://" + config.get("DB_HOST") + ":" + config.get("DB_PORT") + "/" + name + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        try (Connection connection = DriverManager.getConnection(url, config.get("DB_USERNAME"), config.get("DB_PASSWORD")); Statement statement = connection.createStatement()) {
            for (String command : commands) statement.execute(command);
            System.out.println("Transport plan history ready. Existing plan quantities and codes backfilled; original shipments unchanged.");
        } catch (SQLException exception) { throw new IllegalStateException("Schema application failed; SQL state: " + exception.getSQLState() + ", code: " + exception.getErrorCode()); }
    }
}
