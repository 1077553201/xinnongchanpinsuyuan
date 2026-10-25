import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Adds only receipt-verification metadata, preserving original business and transaction values. */
class ApplyReceiptVerificationSchema {
    public static void main(String[] args) throws Exception {
        Map<String,String> config=new HashMap<>();
        for(String line:Files.readAllLines(Path.of(".env"),StandardCharsets.UTF_8)) {
            if(line.trim().startsWith("#"))continue;int separator=line.indexOf('=');if(separator<1)continue;
            String value=line.substring(separator+1).trim();
            if(value.length()>=2&&((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);
            config.put(line.substring(0,separator).trim(),value);
        }
        String name=config.get("DB_NAME");if(name==null||!name.matches("[A-Za-z0-9_]+"))throw new IllegalArgumentException("Invalid database name");
        String url="jdbc:mysql://"+config.get("DB_HOST")+":"+config.get("DB_PORT")+"/"+name+"?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8";
        try(Connection connection=DriverManager.getConnection(url,config.get("DB_USERNAME"),config.get("DB_PASSWORD"))) {
            for(String[] column:new String[][]{{"receipt_verified_at","DATETIME(6) NULL"},{"receipt_verification_hash","VARCHAR(64) NULL"}}) {
                boolean exists;
                try(PreparedStatement query=connection.prepareStatement("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=? AND table_name='chain_transaction' AND column_name=?")) {
                    query.setString(1,name);query.setString(2,column[0]);try(ResultSet result=query.executeQuery()){result.next();exists=result.getInt(1)>0;}
                }
                if(!exists)try(Statement statement=connection.createStatement()){statement.execute("ALTER TABLE chain_transaction ADD COLUMN "+column[0]+" "+column[1]);}
            }
            System.out.println("Receipt verification columns ready. Existing transaction and business values preserved.");
        } catch(SQLException error){throw new IllegalStateException("Schema application failed; SQL state: "+error.getSQLState()+", code: "+error.getErrorCode());}
    }
}
