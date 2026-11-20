package db.migration;

import com.erhms.workspace.EnrollmentCourses;
import com.erhms.workspace.FixedCourseCatalog;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve historical offerings while making new plans use exactly the four supported programs. */
public class V30__fixed_course_schedule_catalog extends BaseJavaMigration {
    @Override public void migrate(Context context)throws Exception{
        var connection=context.getConnection();
        try(var statement=connection.createStatement()){
            statement.execute("ALTER TABLE training_courses ADD COLUMN fixed_catalog BOOLEAN NOT NULL DEFAULT FALSE");
            statement.execute("ALTER TABLE training_courses ALTER COLUMN manager_id DROP NOT NULL");
            statement.execute("ALTER TABLE training_courses ADD CONSTRAINT ck_course_manager CHECK(fixed_catalog=TRUE OR manager_id IS NOT NULL)");
        }
        try(var insert=connection.prepareStatement("INSERT INTO training_courses(id,code,name,profession,duration_months,total_sessions,tuition_fee,objectives,status,manager_id,class_capacity,fixed_catalog) VALUES (?,?,?,?,?,1,?,?,'OPEN',NULL,20,TRUE)")){
            for(int index=0;index<EnrollmentCourses.CATALOG.size();index++){
                var course=EnrollmentCourses.CATALOG.get(index);
                insert.setObject(1,FixedCourseCatalog.IDS.get(index));insert.setString(2,FixedCourseCatalog.CODES.get(index));insert.setString(3,course.name());insert.setString(4,course.name());insert.setInt(5,course.durationMonths());insert.setBigDecimal(6,course.tuitionFee());insert.setString(7,"Đào tạo kiến thức và thực hành "+course.name());insert.executeUpdate();
            }
        }
        try(var statement=connection.createStatement()){
            statement.execute("INSERT INTO m_function(function_code,screen_code,name,path,sort_order) VALUES ('CourseSchedules','CourseSchedules','Cấu hình lịch học theo khóa','/screens/CourseSchedules',140)");
            statement.execute("DELETE FROM role_function WHERE function_code IN ('ListProfession','AddProfession','EditProfession','ListCourse','AddCourse','EditCourse','CourseDetail') OR (role_id=1 AND function_code='ListClass')");
            statement.execute("INSERT INTO role_function(role_id,function_code) VALUES (1,'CourseSchedules')");
            statement.execute("UPDATE permission_revision SET version=version+1 WHERE id=1");
        }
    }
}
