package com.erhms.workspace;

import com.erhms.admin.service.ScreenPermissionsService;
import com.erhms.role.DashboardRole;
import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.onboarding.StudentAccountMailer;
import com.erhms.user.repository.UserRepository;
import com.erhms.user.service.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.erhms.workspace.WorkflowData.*;

@Service
public class StudentAccountsService {
    private static final String SCREEN = "ListStudentAccount";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
    private static final String QUEUE = """
            FROM consultation_registrations r
            JOIN consultation_followups f ON f.consultation_id=r.id
            WHERE f.status='CONTACTED'
            AND LOWER(r.full_name||' '||COALESCE(r.email,'')||' '||r.phone||' '||r.program_name) LIKE ? ESCAPE '!'
            """;
    private final WorkflowData data;
    private final CurrentUser current;
    private final ScreenCatalog catalog;
    private final ScreenPermissionsService permissions;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final EntityManager entities;
    private final Validator validator;
    private final StudentAccountMailer mailer;

    public StudentAccountsService(WorkflowData data, CurrentUser current, ScreenCatalog catalog,
                                  ScreenPermissionsService permissions, UserRepository users,
                                  RoleRepository roles, PasswordEncoder passwords, EntityManager entities,
                                  Validator validator, StudentAccountMailer mailer) {
        this.data=data;this.current=current;this.catalog=catalog;this.permissions=permissions;
        this.users=users;this.roles=roles;this.passwords=passwords;this.entities=entities;
        this.validator=validator;this.mailer=mailer;
    }

    private void requireAccess(User actor) {
        role(actor,"MANAGER");
        catalog.requireRole(catalog.get(SCREEN),actor.getRoleCode());
        permissions.require(actor,SCREEN);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> list(String query,int page,int size) {
        requireAccess(current.get());
        String term=Objects.toString(query,"").strip().toLowerCase(Locale.ROOT);
        if(page<0||page>100000||size<1||size>100||term.length()>200)throw invalid("filter","Trang hoặc từ khóa tìm kiếm không hợp lệ");
        String search="%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        long total=data.count("SELECT COUNT(*) "+QUEUE,search);
        var rows=data.rows("SELECT r.id,r.full_name,r.email,r.phone,r.program_name,r.created_date,r.contact_time,r.intake_source AS source,f.version "+QUEUE
                +" ORDER BY r.created_date DESC,r.id LIMIT ? OFFSET ?",search,size,page*size);
        for(var row:rows) {
            String email=normalizedEmail(row.get("email"));
            row.put("email",email);
            var accounts=matchingAccounts(email);
            Map<String,Object> account=accounts.isEmpty()?null:accounts.get(0);
            String status=accountStatus(email,account);
            String reason=blockedReason(status);
            if(reason.isEmpty())reason=profileIssue(row);
            row.put("accountId",account==null?null:account.get("id"));
            row.put("accountStatus",status);
            row.put("canCreate",status.equals("NONE")&&reason.isEmpty());
            row.put("blockedReason",reason);
        }
        return Map.of("items",rows,"total",total,"totalPages",(total+size-1)/size,"page",page,"size",size);
    }

    @Transactional
    public Map<String,Object> create(String admissionId,Map<String,Object> input) {
        requireAccess(current.get());
        if(!input.keySet().equals(Set.of("expectedEmail","version")))throw invalid("request","Chỉ gửi email và phiên bản hồ sơ đã chọn");
        String expectedEmail=normalizedEmail(text(input,"expectedEmail",190,true));
        long version=number(input,"version",0,Long.MAX_VALUE);
        UUID admission=uuid(admissionId);
        // Use the same lock order as AdminAccountsService for case-insensitive email uniqueness.
        entities.find(Role.class,DashboardRole.ADMIN.id(),LockModeType.PESSIMISTIC_WRITE);
        entities.clear();
        User actor=current.getForUpdate();requireAccess(actor);
        // HR consultation changes and contract creation also lock the registration first.
        data.one("SELECT id FROM consultation_registrations WHERE id=? FOR UPDATE",admission);
        var registration=data.one("""
                SELECT r.id,r.full_name,r.email,r.phone,COALESCE(f.status,'NEW') AS status,COALESCE(f.version,0) AS version
                FROM consultation_registrations r LEFT JOIN consultation_followups f ON f.consultation_id=r.id WHERE r.id=?
                """,admission);
        requireAccess(actor);
        if(!"CONTACTED".equals(registration.get("status")))throw conflict("Chỉ tạo tài khoản từ hồ sơ ở trạng thái Đã liên hệ. Hãy tải lại danh sách.");
        String email=normalizedEmail(registration.get("email"));
        if(((Number)registration.get("version")).longValue()!=version||!email.equals(expectedEmail))
            throw conflict("Hồ sơ hoặc email vừa được cập nhật. Hãy tải lại và kiểm tra trước khi tạo tài khoản.");
        if(!validEmail(email))throw invalid("email","Hồ sơ chưa có email hợp lệ. Bộ phận tuyển sinh cần cập nhật trước khi tạo tài khoản.");
        String profileIssue=profileIssue(registration);
        if(!profileIssue.isEmpty())throw invalid("admission",profileIssue);
        if(!matchingAccounts(email).isEmpty())throw conflict("Email này đã có tài khoản. Không tạo mới hoặc thay đổi mật khẩu của tài khoản hiện có.");
        Role studentRole=roles.findById(DashboardRole.STUDENT.id())
                .filter(r->DashboardRole.resolve(r.id,r.code).orElse(null)==DashboardRole.STUDENT)
                .orElseThrow(()->conflict("Vai trò học viên chưa được cấu hình hợp lệ"));
        try {
            mailer.requireConfigured();
        } catch(WorkflowException failure) {
            throw failure;
        } catch(RuntimeException failure) {
            throw new WorkflowException(HttpStatus.SERVICE_UNAVAILABLE,"MAIL_NOT_CONFIGURED",
                    "Chưa cấu hình email gửi tài khoản. Vui lòng liên hệ quản trị viên.",Map.of());
        }
        String password=initialPassword();
        var account=new User();
        account.email=email;account.fullName=registration.get("fullName").toString().strip();
        account.phone=Objects.toString(registration.get("phone"),"").strip();
        account.role=studentRole;account.passwordHash=passwords.encode(password);account.adminVersion=1;
        users.saveAndFlush(account);
        data.jdbc.update("INSERT INTO student_profiles(user_id,manager_id) VALUES (?,?)",account.id,actor.id);
        try {
            mailer.sendCredentials(email,account.fullName,password);
        } catch(WorkflowException failure) {
            throw failure;
        } catch(RuntimeException failure) {
            // Never attach the SMTP exception: providers can include message bodies or credentials.
            throw new WorkflowException(HttpStatus.BAD_GATEWAY,"MAIL_DELIVERY_FAILED",
                    "Chưa gửi được email tài khoản. Tài khoản chưa được lưu; vui lòng thử lại sau.",Map.of());
        }
        return Map.of("accountId",account.id,"email",email,"notice","Đã tạo tài khoản học viên và gửi thông tin đăng nhập đến email trong hồ sơ.");
    }

    private List<Map<String,Object>> matchingAccounts(String email) {
        if(email.isEmpty())return List.of();
        return data.rows("""
                SELECT u.user_id AS id,u.role_id,r.code AS role_code,u.account_enabled,u.account_locked
                FROM m_user u LEFT JOIN m_role r ON r.role_id=u.role_id
                WHERE LOWER(TRIM(u.email))=? ORDER BY u.user_id
                """,email);
    }

    private String accountStatus(String email,Map<String,Object> account) {
        if(!validEmail(email))return "INVALID_EMAIL";
        if(account==null)return "NONE";
        if(!Objects.equals(3L,((Number)account.get("roleId")).longValue())||!"STUDENT".equals(account.get("roleCode")))return "OTHER_ROLE";
        if(!Boolean.TRUE.equals(account.get("accountEnabled")))return "DISABLED";
        if(Boolean.TRUE.equals(account.get("accountLocked")))return "LOCKED";
        return "STUDENT";
    }

    private static String blockedReason(String status) {
        return switch(status) {
            case "INVALID_EMAIL"->"Hồ sơ chưa có email hợp lệ; cần cập nhật qua bộ phận tuyển sinh.";
            case "OTHER_ROLE"->"Email đã thuộc tài khoản có vai trò khác. Liên hệ quản trị viên để kiểm tra.";
            case "DISABLED"->"Tài khoản học viên đã bị vô hiệu hóa. Liên hệ quản trị viên.";
            case "LOCKED"->"Tài khoản học viên đang bị khóa. Liên hệ quản trị viên.";
            case "STUDENT"->"Học viên đã có tài khoản; không gửi lại hoặc thay đổi mật khẩu.";
            default->"";
        };
    }

    private static String profileIssue(Map<String,Object> row) {
        String name=Objects.toString(row.get("fullName"),"").strip(),phone=Objects.toString(row.get("phone"),"").strip();
        if(name.isEmpty()||name.length()>120)return "Họ tên trong hồ sơ không hợp lệ; cần cập nhật qua bộ phận tuyển sinh.";
        if(phone.length()>20)return "Số điện thoại trong hồ sơ vượt quá 20 ký tự; cần cập nhật qua bộ phận tuyển sinh.";
        return "";
    }

    private record EmailValue(@NotBlank @Email @Size(max=190) String value) {}
    private boolean validEmail(String email) {return validator.validate(new EmailValue(email)).isEmpty()&&!email.chars().anyMatch(Character::isWhitespace);}
    private static String normalizedEmail(Object value) {return Objects.toString(value,"").strip().toLowerCase(Locale.ROOT);}
    private static String initialPassword() {
        char[] chars=new char[24];
        for(int index=0;index<chars.length;index++)chars[index]=PASSWORD_CHARS[RANDOM.nextInt(PASSWORD_CHARS.length)];
        return new String(chars);
    }
}
