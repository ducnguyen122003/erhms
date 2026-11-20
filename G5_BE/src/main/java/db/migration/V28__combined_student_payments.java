package db.migration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve existing bills while adding two allocations to one bank transfer. */
public class V28__combined_student_payments extends BaseJavaMigration {
    @Override
    public void migrate(Context context)throws Exception{
        var connection=context.getConnection();var checks=new ArrayList<String>();
        try(var query=connection.prepareStatement("SELECT DISTINCT t.constraint_name,c.check_clause FROM information_schema.table_constraints t JOIN information_schema.check_constraints c ON c.constraint_schema=t.constraint_schema AND c.constraint_name=t.constraint_name WHERE t.table_schema=CURRENT_SCHEMA() AND LOWER(t.table_name)='student_payment_submissions' AND t.constraint_type='CHECK' AND LOWER(c.check_clause) LIKE '%kind%'")){
            try(var result=query.executeQuery()){
                while(result.next()){
                    String clause=result.getString(2).replace("\"","").replace("(","").replace(")","").strip().replaceAll("\\s+"," ");
                    if(!clause.matches("(?i)[a-z_][a-z0-9_]* IS NOT NULL"))checks.add(result.getString(1));
                }
            }
        }
        if(checks.size()!=1)throw new IllegalStateException("Expected one legacy student payment kind check, found "+checks.size());
        try(var statement=connection.createStatement()){
            statement.execute("ALTER TABLE student_payment_submissions DROP CONSTRAINT \""+checks.get(0).replace("\"","\"\"")+"\"");
            try(var input=getClass().getResourceAsStream("/db/migration-support/combined-student-payments.sql")){
                if(input==null)throw new IllegalStateException("Missing combined student payment migration SQL");
                for(String command:new String(input.readAllBytes(),StandardCharsets.UTF_8).split(";"))if(!command.isBlank())statement.execute(command);
            }
        }
    }
}
