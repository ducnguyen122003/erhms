package com.erhms.user.recovery;

import com.erhms.role.DashboardRole;
import com.erhms.user.entity.User;
import com.erhms.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(noRollbackFor = PasswordResetException.class)
public class PasswordResetService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_SECONDS = 600, TOKEN_SECONDS = 300, RESEND_SECONDS = 60;
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final PasswordResetMailer mailer;
    private final ApplicationEventPublisher publisher;

    public PasswordResetService(JdbcTemplate jdbc, UserRepository users, PasswordEncoder encoder,
            PasswordResetMailer mailer, ApplicationEventPublisher publisher) {
        this.jdbc = jdbc; this.users = users; this.encoder = encoder; this.mailer = mailer; this.publisher = publisher;
    }

    public Map<String, Object> request(PasswordResetInput.Request input, String remoteAddress) {
        mailer.requireConfigured();
        Instant now = Instant.now();
        rateLimitClient(remoteAddress, now);
        String key = hash(input.email());
        var matches = jdbc.queryForList("SELECT user_id FROM m_user WHERE LOWER(email)=? ORDER BY user_id", input.email());
        // Match completion/deletion lock order before touching the recovery row or its FK.
        User account = matches.size() == 1
                ? users.findForUpdateById(number(matches.get(0), "user_id")).orElse(null) : null;
        boolean eligible = account != null && account.email.toLowerCase(Locale.ROOT).equals(input.email())
                && account.accountEnabled && !account.accountLocked
                && DashboardRole.resolve(account.getRoleId(), account.getRoleCode()).isPresent();
        jdbc.update("INSERT INTO password_reset_challenges(email_key) VALUES (?) ON CONFLICT DO NOTHING", key);
        var challenge = challenge(key);
        Instant requested = time(challenge, "requested_at"), window = time(challenge, "request_window_at");
        if (requested != null && requested.plusSeconds(RESEND_SECONDS).isAfter(now)) {
            throw limited(secondsUntil(requested.plusSeconds(RESEND_SECONDS), now));
        }
        int count = window == null || window.plusSeconds(3600).isBefore(now) ? 0 : integer(challenge, "request_count");
        if (count >= 5) throw limited(secondsUntil(window.plusSeconds(3600), now));
        if (count == 0) window = now;

        UUID challengeId = UUID.randomUUID();
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        jdbc.update("""
                UPDATE password_reset_challenges SET user_id=?,challenge_id=?,code_hash=?,code_expires_at=?,
                requested_at=?,request_window_at=?,request_count=?,attempts=0,token_hash=NULL,token_expires_at=NULL,
                password_snapshot=?,credentials_version=?,consumed=FALSE WHERE email_key=?
                """, eligible ? account.getId() : null, challengeId, encoder.encode(code + challengeId),
                ts(now.plusSeconds(CODE_SECONDS)), ts(now), ts(window), count + 1,
                eligible ? account.passwordHash : null, eligible ? account.credentialsVersion : null, key);
        publisher.publishEvent(new PasswordResetMailConfiguration.Email(eligible ? account.email : null, code));
        return Map.of("message", "Nếu email đã được đăng ký và có thể sử dụng, bạn sẽ nhận được mã xác nhận gồm 6 số.",
                "expiresInSeconds", CODE_SECONDS, "retryAfterSeconds", RESEND_SECONDS);
    }

    public Map<String, Object> verify(PasswordResetInput.Verify input) {
        String key = hash(input.email());
        var challenge = challenge(key);
        Instant now = Instant.now();
        if (challenge == null || Boolean.TRUE.equals(challenge.get("consumed")) || challenge.get("code_hash") == null) {
            throw invalidCode();
        }
        if (integer(challenge, "attempts") >= 5) throw attemptsExceeded(challenge, now);
        Instant expiry = time(challenge, "code_expires_at");
        if (expiry == null || !expiry.isAfter(now)) {
            throw new PasswordResetException(HttpStatus.BAD_REQUEST, "RESET_CODE_EXPIRED", "Mã xác nhận đã hết hạn. Vui lòng gửi lại mã.", 0);
        }
        boolean matches = encoder.matches(input.code() + challenge.get("challenge_id"), (String)challenge.get("code_hash"));
        User account = challenge.get("user_id") == null ? null : users.findWithRoleById(number(challenge, "user_id")).orElse(null);
        if (!matches || !matchesAccount(challenge, account, input.email())) {
            int attempts = integer(challenge, "attempts") + 1;
            jdbc.update("UPDATE password_reset_challenges SET attempts=? WHERE email_key=?", attempts, key);
            if (attempts >= 5) throw attemptsExceeded(challenge, now);
            throw invalidCode();
        }
        byte[] secret = new byte[32]; RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        jdbc.update("UPDATE password_reset_challenges SET code_hash=NULL,token_hash=?,token_expires_at=? WHERE email_key=?",
                hash(token), ts(now.plusSeconds(TOKEN_SECONDS)), key);
        return Map.of("resetToken", token, "expiresInSeconds", TOKEN_SECONDS);
    }

    public void complete(PasswordResetInput.Complete input) {
        validatePasswords(input.newPassword(), input.confirmPassword());
        String key = hash(input.email());
        // Account deletion locks the user before cascading recovery records. Use the same
        // order here, then re-read and validate the challenge after acquiring both locks.
        var bindings = jdbc.queryForList("SELECT user_id FROM password_reset_challenges WHERE email_key=?", key);
        User account = bindings.isEmpty() || bindings.get(0).get("user_id") == null ? null
                : users.findForUpdateById(number(bindings.get(0), "user_id")).orElse(null);
        var challenge = challenge(key);
        Instant now = Instant.now();
        if (challenge == null || Boolean.TRUE.equals(challenge.get("consumed")) || challenge.get("token_hash") == null
                || !constantEquals((String)challenge.get("token_hash"), hash(input.resetToken()))) {
            throw invalidToken();
        }
        Instant expiry = time(challenge, "token_expires_at");
        if (expiry == null || !expiry.isAfter(now)) {
            throw new PasswordResetException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_EXPIRED", "Phiên đổi mật khẩu đã hết hạn. Vui lòng yêu cầu mã mới.", 0);
        }
        if (!matchesAccount(challenge, account, input.email())) throw invalidToken();
        account.passwordHash = encoder.encode(input.newPassword());
        account.credentialsVersion++;
        users.saveAndFlush(account);
        jdbc.update("""
                UPDATE password_reset_challenges SET consumed=TRUE,code_hash=NULL,token_hash=NULL,
                password_snapshot=NULL,token_expires_at=NULL WHERE email_key=?
                """, key);
    }

    private Map<String, Object> challenge(String key) {
        var rows = jdbc.queryForList("SELECT * FROM password_reset_challenges WHERE email_key=? FOR UPDATE", key);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void rateLimitClient(String remoteAddress, Instant now) {
        String key = hash(remoteAddress == null ? "unknown" : remoteAddress);
        jdbc.update("INSERT INTO password_reset_rate_limits(client_key,window_at) VALUES (?,?) ON CONFLICT DO NOTHING", key, ts(now));
        var row = jdbc.queryForMap("SELECT * FROM password_reset_rate_limits WHERE client_key=? FOR UPDATE", key);
        Instant window = time(row, "window_at");
        int count = window.plusSeconds(3600).isBefore(now) ? 0 : integer(row, "request_count");
        if (count >= 20) throw limited(secondsUntil(window.plusSeconds(3600), now));
        jdbc.update("UPDATE password_reset_rate_limits SET window_at=?,request_count=? WHERE client_key=?",
                ts(count == 0 ? now : window), count + 1, key);
    }

    private static boolean matchesAccount(Map<String, Object> challenge, User account, String email) {
        return account != null && account.accountEnabled && !account.accountLocked
                && challenge.get("user_id") != null && account.getId() == number(challenge, "user_id")
                && DashboardRole.resolve(account.getRoleId(), account.getRoleCode()).isPresent()
                && account.email.toLowerCase(Locale.ROOT).equals(email)
                && account.credentialsVersion == number(challenge, "credentials_version")
                && constantEquals(account.passwordHash, (String)challenge.get("password_snapshot"));
    }

    private static void validatePasswords(String password, String confirm) {
        var fields = new LinkedHashMap<String, String>();
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 8) {
            fields.put("newPassword", "Mật khẩu mới phải có ít nhất 8 ký tự.");
        } else if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            fields.put("newPassword", "Mật khẩu không được vượt quá 72 byte UTF-8.");
        }
        if (confirm == null || !confirm.equals(password)) fields.put("confirmPassword", "Mật khẩu xác nhận không khớp.");
        if (!fields.isEmpty()) throw new PasswordResetException(HttpStatus.BAD_REQUEST, "RESET_PASSWORD_INVALID", "Vui lòng kiểm tra mật khẩu mới.", 0, fields);
    }

    private static PasswordResetException invalidCode() {
        return new PasswordResetException(HttpStatus.BAD_REQUEST, "RESET_CODE_INVALID", "Mã xác nhận không đúng hoặc đã được sử dụng.", 0);
    }
    private static PasswordResetException invalidToken() {
        return new PasswordResetException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID", "Phiên đổi mật khẩu không hợp lệ. Vui lòng yêu cầu mã mới.", 0);
    }
    private static PasswordResetException limited(long seconds) {
        return new PasswordResetException(HttpStatus.TOO_MANY_REQUESTS, "RESET_RATE_LIMITED", "Bạn đã yêu cầu quá nhanh hoặc quá nhiều lần. Vui lòng thử lại sau.", seconds);
    }
    private static PasswordResetException attemptsExceeded(Map<String, Object> challenge, Instant now) {
        Instant requested = time(challenge, "requested_at");
        return new PasswordResetException(HttpStatus.TOO_MANY_REQUESTS, "RESET_ATTEMPTS_EXCEEDED", "Bạn đã nhập sai mã 5 lần. Vui lòng yêu cầu mã mới.", requested == null ? 0 : Math.max(0, secondsUntil(requested.plusSeconds(RESEND_SECONDS), now)));
    }
    private static long secondsUntil(Instant future, Instant now) { return Math.max(1, Duration.between(now, future).toSeconds() + 1); }
    private static int integer(Map<String, Object> row, String key) { return ((Number)row.get(key)).intValue(); }
    private static long number(Map<String, Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    private static Timestamp ts(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant time(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime date) return date.toInstant();
        return null;
    }
    private static boolean constantEquals(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
