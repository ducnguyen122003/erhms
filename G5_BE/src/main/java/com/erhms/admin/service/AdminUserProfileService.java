package com.erhms.admin.service;

import com.erhms.admin.dto.AdminAccountView;
import com.erhms.admin.dto.AdminUserProfileView;
import com.erhms.shared.exception.BusinessException;
import com.erhms.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
public class AdminUserProfileService {
    private static final String ACCOUNT_HISTORY = """
            ((target_id=? AND action_code IN ('ACCOUNT_CREATE','ACCOUNT_UPDATE','ACCOUNT_LOCK_CHANGE','ACCOUNT_STATUS_CHANGE'))
             OR (actor_id=? AND action_code IN ('LOGIN','LOGOUT','PROFILE_UPDATE','PASSWORD_CHANGE')))
            """;
    private final UserRepository users;
    private final ScreenPermissionsService permissions;
    private final JdbcTemplate jdbc;

    public AdminUserProfileService(UserRepository users, ScreenPermissionsService permissions, JdbcTemplate jdbc) {
        this.users=users; this.permissions=permissions; this.jdbc=jdbc;
    }

    @Transactional(readOnly=true)
    public AdminUserProfileView get(Long id) {
        permissions.require("USER_ACCOUNTS");
        var account=users.findWithRoleById(id)
                .orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"Không tìm thấy tài khoản"));
        var screens=jdbc.query("""
                SELECT f.function_code,f.screen_code,f.name FROM m_function f
                JOIN role_function rf ON rf.function_code=f.function_code WHERE rf.role_id=? ORDER BY f.sort_order
                """,(rs,n)->new AdminUserProfileView.Screen(rs.getString(1),rs.getString(2),rs.getString(3)),account.getRoleId());
        var history=jdbc.query("SELECT action_code,action_label,actor_name,result,change_note,occurred_at FROM system_audit_log WHERE "
                +ACCOUNT_HISTORY+" ORDER BY occurred_at DESC,id DESC LIMIT 20",(rs,n)->new AdminUserProfileView.Activity(
                rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant()),id.toString(),id);
        Instant created=time("SELECT MIN(occurred_at) FROM system_audit_log WHERE target_id=? AND action_code='ACCOUNT_CREATE' AND result='SUCCESS'",id.toString());
        Instant updated=time("SELECT MAX(occurred_at) FROM system_audit_log WHERE result='SUCCESS' AND "+ACCOUNT_HISTORY
                +" AND action_code NOT IN ('LOGIN','LOGOUT')",id.toString(),id);
        Instant lastLogin=time("SELECT MAX(occurred_at) FROM user_login_events WHERE user_id=?",id);
        return new AdminUserProfileView(AdminAccountView.from(account),account.role==null?null:account.role.name,
                created,updated,lastLogin,screens,history);
    }

    private Instant time(String sql,Object... args) {
        Timestamp value=jdbc.queryForObject(sql,Timestamp.class,args);
        return value==null?null:value.toInstant();
    }
}
