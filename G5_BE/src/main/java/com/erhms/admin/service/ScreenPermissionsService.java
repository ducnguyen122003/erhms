package com.erhms.admin.service;

import com.erhms.admin.dto.AdminRoleView;
import com.erhms.role.DashboardRole;
import com.erhms.shared.exception.BusinessException;
import com.erhms.user.entity.User;
import com.erhms.user.service.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import com.erhms.workspace.ScreenCatalog;

@Service
public class ScreenPermissionsService {
    private final JdbcTemplate jdbc;
    private final CurrentUser current;
    private final ScreenCatalog catalog;
    public ScreenPermissionsService(JdbcTemplate jdbc, CurrentUser current,ScreenCatalog catalog) { this.jdbc=jdbc; this.current=current; this.catalog=catalog; }
    public record Screen(String code,String screenCode,String name,String path,List<Long> allowedRoles) {}
    public record Grant(@NotNull Long roleId,@NotNull String functionCode) {}
    public record Input(@NotNull Long version,@NotNull @Size(max=1000) List<@Valid Grant> grants) {}
    public record Matrix(long version,List<AdminRoleView> roles,List<Screen> functions,List<Grant> grants) {}

    public List<String> codes(User user) {
        return jdbc.queryForList("SELECT function_code FROM role_function WHERE role_id=? ORDER BY function_code",String.class,user.getRoleId());
    }
    public void require(String code) { require(current.get(),code); }
    public void require(User user,String code) {
        if (DashboardRole.resolve(user.getRoleId(),user.getRoleCode()).isEmpty()
                || jdbc.queryForObject("SELECT COUNT(*) FROM role_function WHERE role_id=? AND function_code=?",Long.class,user.getRoleId(),code)==0) {
            throw new AccessDeniedException("Bạn chưa được cấp quyền truy cập màn này");
        }
    }
    @Transactional(readOnly=true)
    public Map<String,Object> mine() {
        User user=current.get();
        return Map.of("functions",codes(user),"version",revision(false));
    }
    @Transactional(readOnly=true)
    public Matrix matrix() { require("ROLE_PERMISSIONS"); return readMatrix(); }
    private Matrix readMatrix() {
        var roles=jdbc.query("SELECT role_id,code,name FROM m_role ORDER BY role_id",(rs,n)->new AdminRoleView(rs.getLong(1),rs.getString(2),rs.getString(3)))
                .stream().filter(r->DashboardRole.resolve(r.id(),r.code()).isPresent()).toList();
        var functions=jdbc.query("SELECT function_code,screen_code,name,path FROM m_function ORDER BY sort_order",(rs,n)->
                new Screen(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),eligibleRoles(rs.getString(1))));
        var grants=jdbc.query("SELECT role_id,function_code FROM role_function ORDER BY role_id,function_code",(rs,n)->new Grant(rs.getLong(1),rs.getString(2)));
        return new Matrix(revision(false),roles,functions,grants);
    }
    @Transactional
    public Matrix update(Input input) {
        long version=revision(true);
        require("ROLE_PERMISSIONS");
        if (input.version()!=version) throw new BusinessException(HttpStatus.CONFLICT,"Phân quyền vừa được cập nhật. Hãy tải lại trước khi lưu.");
        var screens=jdbc.queryForList("SELECT function_code FROM m_function",String.class);
        var seen=new HashSet<String>();
        for (Grant grant:input.grants()) {
            if (DashboardRole.fromId(grant.roleId()).isEmpty() || !screens.contains(grant.functionCode())
                    || (grant.functionCode().equals("RECRUITMENT") && grant.roleId()!=6 && grant.roleId()!=7)
                    || !eligibleRoles(grant.functionCode()).contains(grant.roleId())
                    || !seen.add(grant.roleId()+":"+grant.functionCode())) {
                throw new BusinessException(HttpStatus.BAD_REQUEST,"Danh sách phân quyền không hợp lệ hoặc bị trùng");
            }
        }
        for (String essential:List.of("USER_ACCOUNTS","ROLE_PERMISSIONS")) {
            if (!seen.contains("1:"+essential)) throw new BusinessException(HttpStatus.CONFLICT,"Cần giữ quyền quản lý tài khoản và phân quyền cho Admin để khôi phục hệ thống.");
        }
        jdbc.update("DELETE FROM role_function");
        for (Grant grant:input.grants()) jdbc.update("INSERT INTO role_function(role_id,function_code) VALUES (?,?)",grant.roleId(),grant.functionCode());
        jdbc.update("UPDATE permission_revision SET version=version+1 WHERE id=1");
        return readMatrix();
    }
    private long revision(boolean lock) { return jdbc.queryForObject("SELECT version FROM permission_revision WHERE id=1"+(lock?" FOR UPDATE":""),Long.class); }
    private List<Long> eligibleRoles(String code) {
        if(catalog.find(code).isPresent())return catalog.allowedRoles(code);
        return code.equals("RECRUITMENT")?List.of(6L,7L):List.of(1L,2L,3L,4L,5L,6L,7L);
    }
}
