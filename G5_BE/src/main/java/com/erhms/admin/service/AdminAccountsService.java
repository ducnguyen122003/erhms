package com.erhms.admin.service;

import com.erhms.admin.dto.AdminAccountInput;
import com.erhms.admin.dto.AdminAccountLockInput;
import com.erhms.admin.dto.AdminAccountPage;
import com.erhms.admin.dto.AdminAccountStatusInput;
import com.erhms.admin.dto.AdminAccountView;
import com.erhms.admin.dto.AdminRoleView;
import com.erhms.admin.exception.AdminAccountException;
import com.erhms.role.DashboardRole;
import com.erhms.role.entity.Role;
import com.erhms.role.repository.RoleRepository;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import com.erhms.user.service.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AdminAccountsService {
    // Keep business history intact even when foreign-key actions are changed in a future migration.
    private static final String BUSINESS_REFERENCES = """
            SELECT COUNT(*) FROM (
                SELECT created_by AS user_id FROM recruitment_requests WHERE created_by = :id
                UNION ALL SELECT actor_id FROM request_events WHERE actor_id = :id
                UNION ALL SELECT lecturer_id FROM training_classes WHERE lecturer_id = :id
                UNION ALL SELECT student_id FROM training_enrollments WHERE student_id = :id
                UNION ALL SELECT student_id FROM training_attendance WHERE student_id = :id
                UNION ALL SELECT student_id FROM training_contracts WHERE student_id = :id
                UNION ALL SELECT requested_by FROM equipment_requests WHERE requested_by = :id
                UNION ALL SELECT student_id FROM equipment_loans WHERE student_id = :id
                UNION ALL SELECT reported_by FROM equipment_incidents WHERE reported_by = :id
                UNION ALL SELECT assigned_to FROM consultation_followups WHERE assigned_to = :id
            ) related_accounts
            """;
    private final CurrentUser current;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwords;
    private final EntityManager entities;

    public AdminAccountsService(CurrentUser current, UserRepository users, RoleRepository roles,
                                PasswordEncoder passwords, EntityManager entities) {
        this.current = current;
        this.users = users;
        this.roles = roles;
        this.passwords = passwords;
        this.entities = entities;
    }

    @Transactional(readOnly = true)
    public AdminAccountPage list(String query, Long roleId, int page, int size) {
        requireAdmin(current.get());
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "Trang không hợp lệ (page từ 0, size từ 1 đến 100)", null);
        }
        if (roleId != null && DashboardRole.fromId(roleId).isEmpty()) {
            throw error(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ", "roleId");
        }
        String term = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (term.length() > 200) throw error(HttpStatus.BAD_REQUEST, "Từ khóa tìm kiếm tối đa 200 ký tự", "q");
        String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<User> criteria = (root, ignored, builder) -> {
            var filters = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (!term.isEmpty()) filters.add(builder.or(
                    builder.like(builder.lower(root.get("fullName")), pattern, '\\'),
                    builder.like(builder.lower(root.get("email")), pattern, '\\'),
                    builder.like(builder.lower(root.get("phone")), pattern, '\\')));
            if (roleId != null) filters.add(builder.equal(root.get("role").get("id"), roleId));
            return builder.and(filters.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var found = users.findAll(criteria, PageRequest.of(page, size, Sort.by("id").descending()));
        return new AdminAccountPage(found.stream().map(AdminAccountView::from).toList(), found.getTotalElements(),
                found.getNumber(), found.getSize(), found.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<AdminRoleView> roles() {
        requireAdmin(current.get());
        return entities.createQuery("select new com.erhms.admin.dto.AdminRoleView(r.id, r.code, r.name) "
                        + "from Role r order by r.id", AdminRoleView.class).getResultStream()
                .filter(role -> DashboardRole.resolve(role.id(), role.code()).isPresent()).toList();
    }

    @Transactional(readOnly = true)
    public AdminAccountView detail(Long id) {
        requireAdmin(current.get());
        return AdminAccountView.from(users.findWithRoleById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản", null)));
    }

    @Transactional
    public AdminAccountView create(AdminAccountInput input) {
        lockAndRequireAdmin();
        Role role = validRole(input.roleId());
        validatePassword(input, true);
        ensureUniqueEmail(input.email(), null);
        var account = new User();
        apply(account, input, role);
        account.passwordHash = passwords.encode(input.password());
        return save(account);
    }

    @Transactional
    public AdminAccountView update(Long id, AdminAccountInput input) {
        User administrator = lockAndRequireAdmin();
        Role role = validRole(input.roleId());
        User account = Objects.equals(administrator.id, id) ? administrator : users.findForUpdateById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản", null));
        if (Objects.equals(administrator.id, account.id) && role.getId() != DashboardRole.ADMIN.id()) {
            throw error(HttpStatus.CONFLICT, "Bạn không thể thay đổi quyền quản trị viên của chính mình", "roleId");
        }
        if (DashboardRole.resolve(account.getRoleId(), account.getRoleCode()).orElse(null) == DashboardRole.ADMIN
                && role.getId() != DashboardRole.ADMIN.id() && account.accountEnabled && !account.accountLocked
                && users.countUsableAdministrators() <= 1) {
            throw error(HttpStatus.CONFLICT, "Cần giữ ít nhất một quản trị viên đang hoạt động", "roleId");
        }
        ensureUniqueEmail(input.email(), account.id);
        boolean passwordChanged = validatePassword(input, false);
        if (!Objects.equals(account.email, input.email()) || !Objects.equals(account.getRoleId(), role.getId())
                || passwordChanged) {
            account.credentialsVersion++;
        }
        if (passwordChanged) account.passwordHash = passwords.encode(input.password());
        apply(account, input, role);
        return save(account);
    }

    @Transactional
    public AdminAccountView updateStatus(Long id, AdminAccountStatusInput input) {
        User administrator = lockAndRequireAdmin();
        User account = Objects.equals(administrator.id, id) ? administrator : users.findForUpdateById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản", null));
        boolean enabled = input.accountEnabled();
        if (!enabled && Objects.equals(administrator.id, account.id)) {
            throw error(HttpStatus.CONFLICT, "Bạn không thể vô hiệu hóa tài khoản đang đăng nhập của chính mình", "accountEnabled");
        }
        if (account.accountEnabled != enabled) {
            if (!enabled && !account.accountLocked
                    && DashboardRole.resolve(account.getRoleId(), account.getRoleCode()).orElse(null) == DashboardRole.ADMIN
                    && users.countUsableAdministrators() <= 1) {
                throw error(HttpStatus.CONFLICT, "Cần giữ ít nhất một quản trị viên đang hoạt động", "accountEnabled");
            }
            account.accountEnabled = enabled;
            // Old sessions and recovery challenges must stay invalid after the account is re-enabled.
            account.credentialsVersion++;
            return save(account);
        }
        return AdminAccountView.from(account);
    }

    @Transactional
    public AdminAccountView updateLock(Long id, AdminAccountLockInput input) {
        User administrator = lockAndRequireAdmin();
        User account = Objects.equals(administrator.id, id) ? administrator : users.findForUpdateById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản", null));
        boolean locked = input.accountLocked();
        if (locked && Objects.equals(administrator.id, account.id)) {
            throw error(HttpStatus.CONFLICT, "Bạn không thể khóa tài khoản đang đăng nhập của chính mình", "accountLocked");
        }
        if (account.accountLocked != locked) {
            if (locked && account.accountEnabled
                    && DashboardRole.resolve(account.getRoleId(), account.getRoleCode()).orElse(null) == DashboardRole.ADMIN
                    && users.countUsableAdministrators() <= 1) {
                throw error(HttpStatus.CONFLICT, "Cần giữ ít nhất một quản trị viên đang hoạt động", "accountLocked");
            }
            account.accountLocked = locked;
            // Unlocking must not restore sessions or recovery challenges issued before locking.
            account.credentialsVersion++;
            return save(account);
        }
        return AdminAccountView.from(account);
    }

    @Transactional
    public void delete(Long id) {
        User administrator = lockAndRequireAdmin();
        if (Objects.equals(administrator.id, id)) {
            throw error(HttpStatus.CONFLICT, "Bạn không thể xóa tài khoản đang đăng nhập của chính mình", null);
        }
        User account = users.findForUpdateById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản", null));
        if (DashboardRole.resolve(account.getRoleId(), account.getRoleCode()).orElse(null) == DashboardRole.ADMIN
                && account.accountEnabled && !account.accountLocked && users.countUsableAdministrators() <= 1) {
            throw error(HttpStatus.CONFLICT, "Cần giữ ít nhất một quản trị viên đang hoạt động", null);
        }
        long related = ((Number) entities.createNativeQuery(BUSINESS_REFERENCES)
                .setParameter("id", id).getSingleResult()).longValue();
        if (related > 0) throw relatedData();
        try {
            // Only authentication records (login history and reset challenges) cascade at the database level.
            users.delete(account);
            users.flush();
        } catch (DataIntegrityViolationException exception) {
            // A foreign key also protects against a related record inserted concurrently.
            throw relatedData();
        }
    }

    private static AdminAccountException relatedData() {
        return error(HttpStatus.CONFLICT, "Không thể xóa: tài khoản đang có dữ liệu liên quan. Hãy giữ tài khoản để bảo toàn lịch sử nghiệp vụ.", null);
    }

    private static boolean validatePassword(AdminAccountInput input, boolean required) {
        String password = input.password(), confirm = input.confirmPassword();
        boolean passwordBlank = password == null || password.isBlank();
        boolean confirmBlank = confirm == null || confirm.isBlank();
        if (!required && passwordBlank && confirmBlank) return false;
        if (passwordBlank || password.codePointCount(0, password.length()) < 8) {
            throw error(HttpStatus.BAD_REQUEST, "Mật khẩu phải có ít nhất 8 ký tự.", "password");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw error(HttpStatus.BAD_REQUEST, "Mật khẩu không được vượt quá 72 byte UTF-8.", "password");
        }
        if (confirm == null || !confirm.equals(password)) {
            throw error(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp.", "confirmPassword");
        }
        return true;
    }

    private User lockAndRequireAdmin() {
        requireAdmin(current.get());
        // A common lock serializes competing administrative changes before locking individual users.
        entities.find(Role.class, DashboardRole.ADMIN.id(), LockModeType.PESSIMISTIC_WRITE);
        // Re-read the actor after waiting: a previous administrator may have changed this account meanwhile.
        entities.clear();
        User administrator = current.getForUpdate();
        requireAdmin(administrator);
        return administrator;
    }

    private static void requireAdmin(User user) {
        if (DashboardRole.resolve(user.getRoleId(), user.getRoleCode()).orElse(null) != DashboardRole.ADMIN) {
            throw new AccessDeniedException("Chỉ quản trị viên được quản lý tài khoản người dùng");
        }
    }

    private Role validRole(Long id) {
        return roles.findById(id).filter(role -> DashboardRole.resolve(role.getId(), role.getCode()).isPresent())
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ", "roleId"));
    }

    private void ensureUniqueEmail(String email, Long exceptId) {
        if (exceptId == null ? users.existsByEmailIgnoreCase(email) : users.existsByEmailIgnoreCaseAndIdNot(email, exceptId)) {
            throw error(HttpStatus.CONFLICT, "Email này đã được sử dụng", "email");
        }
    }

    private AdminAccountView save(User user) {
        try {
            return AdminAccountView.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw error(HttpStatus.CONFLICT, "Email này đã được sử dụng hoặc dữ liệu tài khoản vừa thay đổi", "email");
        }
    }

    private static void apply(User user, AdminAccountInput input, Role role) {
        user.fullName = input.fullName();
        user.email = input.email();
        user.phone = input.phone();
        user.role = role;
    }

    private static AdminAccountException error(HttpStatus status, String message, String field) {
        return new AdminAccountException(status, message, field);
    }
}
