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
            WHERE ((r.intake_source='EXCEL' OR f.forwarded_at IS NOT NULL) AND f.status IN ('PROFILE_PENDING','CONTRACTED','PROFILE_CREATED') OR (r.intake_source='EXCEL' AND f.status='CONTACTED'))
            AND LOWER(r.full_name||' '||COALESCE(r.email,'')||' '||r.phone||' '||r.program_name||' '||COALESCE(r.enrollment_course,'')) LIKE ? ESCAPE '!'
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
    private final AdmissionProfiles profiles;

    public StudentAccountsService(WorkflowData data, CurrentUser current, ScreenCatalog catalog,
                                  ScreenPermissionsService permissions, UserRepository users,
                                  RoleRepository roles, PasswordEncoder passwords, EntityManager entities,
                                  Validator validator, StudentAccountMailer mailer, AdmissionProfiles profiles) {
        this.data=data;this.current=current;this.catalog=catalog;this.permissions=permissions;
        this.users=users;this.roles=roles;this.passwords=passwords;this.entities=entities;
        this.validator=validator;this.mailer=mailer;this.profiles=profiles;
    }

    private void requireAccess(User actor) {
        role(actor,"MANAGER");
        catalog.requireRole(catalog.get(SCREEN),actor.getRoleCode());
        permissions.require(actor,SCREEN);
    }

    @Transactional(readOnly=true)
    public Map<String,Object> list(String query,int page,int size) {
        User actor=current.get();requireAccess(actor);
        String term=Objects.toString(query,"").strip().toLowerCase(Locale.ROOT);
        if(page<0||page>100000||size<1||size>100||term.length()>200)throw invalid("filter","Trang hoặc từ khóa tìm kiếm không hợp lệ");
        String search="%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        long total=data.count("SELECT COUNT(*) "+QUEUE,search);
        var rows=data.rows("SELECT r.id,r.full_name,r.email,r.phone,r.program_name,r.enrollment_course,r.created_date,r.contact_time,r.intake_source AS source,f.status AS profile_status,"+AdmissionProfiles.DOCUMENT_STATUS_SQL+" AS document_status,f.version,f.profile_submitted_at,f.profile_created_at,(SELECT CONCAT('file:',w.id,':',w.filename) FROM workflow_files w WHERE w.id=f.scan_file_id) AS profile_evidence "+QUEUE
                +" ORDER BY r.created_date DESC,r.id LIMIT ? OFFSET ?",search,size,page*size);
        for(var row:rows) {
            row.put("profileDocuments",profiles.documents(uuid(row.get("id").toString())));
            String email=normalizedEmail(row.get("email"));
            row.put("email",email);
            var accounts=matchingAccounts(email);
            Map<String,Object> account=accounts.isEmpty()?null:accounts.get(0);
            String status=accountStatus(email,account);
            var contracts=data.rows("SELECT id,code,student_email,manager_id,status,signature_approved_at FROM training_contracts WHERE consultation_id=? AND status<>'CANCELLED' ORDER BY id",uuid(row.get("id").toString()));
            var ownedContracts=contracts.stream().filter(c->ownedContract(c,actor)).toList();
            boolean hasContract=!ownedContracts.isEmpty()&&contracts.stream().allMatch(c->ownedContract(c,actor)&&email.equals(normalizedEmail(c.get("studentEmail"))));
            String contractIssue=contractIssue(contracts,email,actor);
            row.put("hasContract",hasContract);
            row.put("hasApprovedContract",contractIssue.isEmpty());
            row.put("contractSigningStatus",ownedContracts.isEmpty()?null:ownedContracts.get(0).get("status"));
            row.put("contractId",ownedContracts.isEmpty()?null:ownedContracts.get(0).get("id"));
            row.put("contractCode",ownedContracts.isEmpty()?null:ownedContracts.get(0).get("code"));
            row.put("canContract",List.of("PROFILE_PENDING","PROFILE_CREATED").contains(row.get("profileStatus"))||("EXCEL".equals(row.get("source"))&&"CONTACTED".equals(row.get("profileStatus"))));
            boolean pending=!"PROFILE_CREATED".equals(row.get("profileStatus"));
            boolean reuse=pending&&status.equals("STUDENT")&&canUseExistingProfile(((Number)account.get("id")).longValue(),actor);
            String reason=reuse?"":blockedReason(status);
            if(status.equals("STUDENT")&&pending&&!reuse)reason="Hồ sơ học viên đã thuộc quản lý khác. Vui lòng liên hệ quản lý phụ trách.";
            if(!pending)reason="Đã tạo hồ sơ học viên.";
            if(reason.isEmpty())reason=profileIssue(row);
            if(pending&&!contractIssue.isEmpty())reason=contractIssue;
            row.put("accountId",account==null?null:account.get("id"));
            row.put("accountStatus",status);
            row.put("canCreate",pending&&contractIssue.isEmpty()&&(status.equals("NONE")||reuse)&&reason.isEmpty());
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
                SELECT r.id,r.full_name,r.email,r.phone,r.intake_source,f.forwarded_at,COALESCE(f.status,'NEW') AS status,COALESCE(f.version,0) AS version
                FROM consultation_registrations r LEFT JOIN consultation_followups f ON f.consultation_id=r.id WHERE r.id=?
                """,admission);
        requireAccess(actor);
        if("ADMISSION".equals(registration.get("intakeSource"))&&registration.get("forwardedAt")==null)throw conflict("Chờ tuyển sinh chuyển hồ sơ sang quản lý học vụ trước khi cấp tài khoản.");
        if(!"CONTRACTED".equals(registration.get("status")))throw conflict("Cần lập hợp đồng trước khi tạo tài khoản học viên. Hãy tải lại danh sách.");
        String email=normalizedEmail(registration.get("email"));
        if(((Number)registration.get("version")).longValue()!=version||!email.equals(expectedEmail))
            throw conflict("Hồ sơ hoặc email vừa được cập nhật. Hãy tải lại và kiểm tra trước khi tạo tài khoản.");
        if(!validEmail(email))throw invalid("email","Hồ sơ chưa có email hợp lệ. Bộ phận tuyển sinh cần cập nhật trước khi tạo tài khoản.");
        var contracts=data.rows("SELECT id,student_id,student_email,manager_id,status,signature_approved_at FROM training_contracts WHERE consultation_id=? AND status<>'CANCELLED' ORDER BY id FOR UPDATE",admission);
        String contractIssue=contractIssue(contracts,email,actor);
        if(!contractIssue.isEmpty())throw conflict(contractIssue);
        String profileIssue=profileIssue(registration);
        if(!profileIssue.isEmpty())throw invalid("admission",profileIssue);
        var matches=matchingAccounts(email);
        if(!matches.isEmpty()) {
            var existing=matches.get(0);long accountId=((Number)existing.get("id")).longValue();
            data.one("SELECT user_id FROM m_user WHERE user_id=? FOR UPDATE",accountId);
            matches=matchingAccounts(email);
            if(matches.size()!=1||!"STUDENT".equals(accountStatus(email,matches.get(0)))||!canUseExistingProfile(accountId,actor))
                throw conflict("Email đã có tài khoản không thể dùng để tạo hồ sơ này. Vui lòng kiểm tra quản lý phụ trách và trạng thái tài khoản.");
            if(data.count("SELECT COUNT(*) FROM student_profiles WHERE user_id=?",accountId)==0)
                data.jdbc.update("INSERT INTO student_profiles(user_id,manager_id) VALUES (?,?)",accountId,actor.id);
            else data.jdbc.update("UPDATE student_profiles SET manager_id=?,version=version+1 WHERE user_id=?",actor.id,accountId);
            completeProfile(admission,accountId,email,actor);
            return Map.of("accountId",accountId,"email",email,"notice","Đã gắn hợp đồng với tài khoản học viên hiện có theo email đã cung cấp. Trạng thái: Đã tạo hồ sơ.");
        }
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
        completeProfile(admission,account.id,email,actor);
        try {
            mailer.sendCredentials(email,account.fullName,password);
        } catch(WorkflowException failure) {
            throw failure;
        } catch(RuntimeException failure) {
            // Never attach the SMTP exception: providers can include message bodies or credentials.
            throw new WorkflowException(HttpStatus.BAD_GATEWAY,"MAIL_DELIVERY_FAILED",
                    "Chưa gửi được email tài khoản. Tài khoản chưa được lưu; vui lòng thử lại sau.",Map.of());
        }
        return Map.of("accountId",account.id,"email",email,"notice","Đã tạo tài khoản, gắn hợp đồng theo email học viên và gửi thông tin đăng nhập. Trạng thái: Đã tạo hồ sơ.");
    }

    private boolean canUseExistingProfile(long student,User actor) {
        return data.count("SELECT COUNT(*) FROM student_profiles WHERE user_id=? AND manager_id IS NOT NULL AND manager_id<>?",student,actor.id)==0;
    }

    private static boolean ownedContract(Map<String,Object> contract,User actor) {
        return contract.get("managerId") instanceof Number owner&&owner.longValue()==actor.id;
    }

    private static String contractIssue(List<Map<String,Object>> contracts,String email,User actor) {
        if(contracts.isEmpty())return "Cần lập hợp đồng bằng email học viên trước khi tạo tài khoản.";
        if(contracts.stream().anyMatch(c->!ownedContract(c,actor)))return "Hợp đồng của hồ sơ đang thuộc quản lý khác. Vui lòng liên hệ quản lý phụ trách.";
        if(contracts.stream().anyMatch(c->!email.equals(normalizedEmail(c.get("studentEmail")))))return "Email hồ sơ không khớp email đã cung cấp trên hợp đồng. Hãy kiểm tra lại hồ sơ.";
        if(contracts.stream().anyMatch(c->!List.of("SIGNED","ACTIVE","COMPLETED").contains(c.get("status"))||c.get("signatureApprovedAt")==null))
            return "Cần quản lý học vụ xác nhận bản hợp đồng đã ký trước khi tạo tài khoản.";
        return "";
    }

    private void completeProfile(UUID admission,long student,String email,User actor) {
        if(data.count("SELECT COUNT(*) FROM training_contracts WHERE consultation_id=? AND status<>'CANCELLED' AND student_id IS NOT NULL AND student_id<>?",admission,student)>0)
            throw conflict("Hợp đồng đã gắn với tài khoản học viên khác. Cần kiểm tra lại email.");
        data.jdbc.update("UPDATE training_contracts SET student_id=?,handed_over_at=COALESCE(handed_over_at,CURRENT_TIMESTAMP),version=version+1 WHERE consultation_id=? AND manager_id=? AND LOWER(TRIM(student_email))=? AND (student_id IS NULL OR student_id=?) AND status IN ('SIGNED','ACTIVE','COMPLETED') AND signature_approved_at IS NOT NULL",student,admission,actor.id,email,student);
        data.jdbc.update("UPDATE consultation_followups SET status='PROFILE_CREATED',profile_student_id=?,profile_created_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE consultation_id=?",student,admission);
        data.jdbc.update("INSERT INTO consultation_notes(id,consultation_id,actor_id,channel,outcome,content) VALUES (?,?,?,'IN_PERSON','PROFILE_CREATED','Quản lý đã cấp tài khoản và gắn hợp đồng theo email học viên cung cấp.')",UUID.randomUUID(),admission,actor.id);
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
