package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Inline constraints have generated names in H2 and PostgreSQL. */
public class V26__admission_contract_signing extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection=context.getConnection();
        dropCheck(connection,"training_contracts","%signed%");
        dropCheck(connection,"workflow_files","%file_size%");
        try(var input=getClass().getResourceAsStream("/db/migration-support/contract-signing.sql")){
            if(input==null)throw new IllegalStateException("Missing contract signing migration SQL");
            String sql=new String(input.readAllBytes(),StandardCharsets.UTF_8);
            try(var statement=connection.createStatement()){
                for(String command:sql.split(";"))if(!command.isBlank())statement.execute(command);
            }
        }
    }
    private void dropCheck(Connection connection,String table,String match) throws Exception {
        var checks=new ArrayList<String>();
        try(var statement=connection.prepareStatement("SELECT DISTINCT t.constraint_name,c.check_clause FROM information_schema.table_constraints t JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name WHERE t.table_schema=CURRENT_SCHEMA() AND LOWER(t.table_name)=? AND t.constraint_type='CHECK' AND LOWER(c.check_clause) LIKE ?")){
            statement.setString(1,table);statement.setString(2,match);
            try(var result=statement.executeQuery()){
                while(result.next()){
                    // PostgreSQL also exposes column NOT NULL rules here as CHECKs.
                    // Keep those rules: this migration only replaces the range/status check.
                    String clause=result.getString(2).replace("\"","").replace("(","").replace(")","").strip().replaceAll("\\s+"," ");
                    if(clause.matches("(?i)[a-z_][a-z0-9_]* IS NOT NULL"))continue;
                    checks.add(result.getString(1));
                }
            }
        }
        if(checks.size()!=1)throw new IllegalStateException("Expected one legacy check on "+table+", found "+checks.size());
        try(var statement=connection.createStatement()){statement.execute("ALTER TABLE "+table+" DROP CONSTRAINT \""+checks.get(0).replace("\"","\"\"")+"\"");}
    }
}
