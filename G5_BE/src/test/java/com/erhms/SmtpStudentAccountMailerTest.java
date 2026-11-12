package com.erhms;

import com.erhms.admin.service.SystemSettingsService;
import com.erhms.user.onboarding.SmtpStudentAccountMailer;
import com.erhms.workspace.WorkflowException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpStudentAccountMailerTest {
    private static final String FROM = "accounts@example.test";
    private static final String RECIPIENT = "student@example.test";
    private static final String LOGIN = "https://students.example.test/login";
    private static final String GENERATED_PASSWORD = "Generated-for-test-84!";

    @Test
    void sendsOneVietnameseWelcomeEmailWithOnlyTheIntendedRecipientAndCredentials() {
        var sender = mock(JavaMailSender.class);
        var mailer = mailer(sender, FROM, LOGIN);
        branding(mailer, "Trung tâm đào tạo", "support@example.test");

        mailer.sendCredentials(RECIPIENT, "Nguyễn Văn An", GENERATED_PASSWORD);

        var mail = delivered(sender);
        assertArrayEquals(new String[]{RECIPIENT}, mail.getTo());
        assertNull(mail.getCc());
        assertNull(mail.getBcc());
        assertEquals(FROM, mail.getFrom());
        assertEquals("support@example.test", mail.getReplyTo());
        assertEquals("Trung tâm đào tạo - Tài khoản học viên của bạn", mail.getSubject());
        assertTrue(mail.getText().contains("Xin chào Nguyễn Văn An,"));
        assertTrue(mail.getText().contains("Email đăng nhập: " + RECIPIENT));
        assertTrue(mail.getText().contains("Mật khẩu ban đầu: " + GENERATED_PASSWORD));
        assertTrue(mail.getText().contains("Trang đăng nhập: " + LOGIN));
        assertTrue(mail.getText().contains("đổi mật khẩu ngay sau lần đăng nhập đầu tiên"));
        assertFalse(mail.getSubject().contains(GENERATED_PASSWORD));
        assertEquals("StudentAccountEmail[REDACTED]", mail.toString());
        verifyNoMoreInteractions(sender);
    }

    @Test
    void emptyFromFallsBackToConfiguredUsername() {
        for (String from : new String[]{null, "", " \t "}) {
            var sender = mock(JavaMailSender.class);
            var mailer = mailer(sender, from, LOGIN);
            mailer.sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD);
            assertEquals(FROM, delivered(sender).getFrom());
        }
    }

    @Test
    void explicitFromOverridesUsernameAndDefaultsToCenterBrandWhenSettingsAreUnavailable() {
        var sender = mock(JavaMailSender.class);
        var mailer = mailer(sender, "  welcome@example.test  ", LOGIN);
        mailer.sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD);
        var mail = delivered(sender);
        assertEquals("welcome@example.test", mail.getFrom());
        assertTrue(mail.getSubject().startsWith("BK-VTCMS"));
        assertNull(mail.getReplyTo());
    }

    @Test
    void missingHostSenderOrAuthenticationCredentialsFailsBeforeSending() {
        for (String[] config : new String[][]{
                {"", FROM, "smtp-secret", FROM},
                {null, FROM, "smtp-secret", FROM},
                {"smtp.example.test", "", "smtp-secret", FROM},
                {"smtp.example.test", FROM, " ", FROM},
                {"smtp.example.test", "", "smtp-secret", ""},
                {"smtp.example.test", FROM, "smtp-secret", "not-an-address"}
        }) {
            var sender = mock(JavaMailSender.class);
            var mailer = new SmtpStudentAccountMailer(sender, config[0], config[1], config[2], config[3], true, LOGIN);
            var failure = assertThrows(WorkflowException.class,
                    () -> mailer.sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD));
            assertConfigurationFailure(failure);
            assertFalse(failure.getMessage().contains("smtp-secret"));
            assertFalse(failure.toString().contains(GENERATED_PASSWORD));
            verifyNoInteractions(sender);
        }
    }

    @Test
    void unauthenticatedSmtpAllowsEmptyCredentialsButStillRequiresHostAndSender() {
        var sender = mock(JavaMailSender.class);
        var mailer = new SmtpStudentAccountMailer(sender, "localhost", "", "", FROM, false, LOGIN);
        assertDoesNotThrow(mailer::requireConfigured);
        mailer.sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD);
        assertEquals(FROM, delivered(sender).getFrom());
        for (String[] config : new String[][]{{"", FROM}, {"localhost", ""}}) {
            var missing = new SmtpStudentAccountMailer(sender, config[0], "", "", config[1], false, LOGIN);
            assertConfigurationFailure(assertThrows(WorkflowException.class, missing::requireConfigured));
        }
    }

    @Test
    void smtpFailureDoesNotExposeOriginalExceptionRecipientOrPassword() {
        var sender = mock(JavaMailSender.class);
        var secretFailure = new MailSendException("SMTP failed for " + RECIPIENT + ": " + GENERATED_PASSWORD);
        doThrow(secretFailure).when(sender).send(any(SimpleMailMessage.class));
        var failure = assertThrows(WorkflowException.class,
                () -> mailer(sender, FROM, LOGIN).sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD));
        assertEquals(HttpStatus.BAD_GATEWAY, failure.status);
        assertEquals("MAIL_DELIVERY_FAILED", failure.code);
        assertTrue(failure.fields.isEmpty());
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.toString().contains(RECIPIENT));
        assertFalse(failure.toString().contains(GENERATED_PASSWORD));
        assertFalse(failure.toString().contains("SMTP failed"));
    }

    @Test
    void invalidLoginUrlsFailBeforeAnyMessageIsSent() {
        for (String url : new String[]{"", "javascript:alert(1)", "file:///tmp/login", "/login",
                "https:///login", "https://name:secret@example.test/login", "https://example.test/login\r\nBcc:x@example.test"}) {
            var sender = mock(JavaMailSender.class);
            var failure = assertThrows(WorkflowException.class,
                    () -> mailer(sender, FROM, url).sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD));
            assertConfigurationFailure(failure);
            verifyNoInteractions(sender);
        }
    }

    @Test
    void recipientCannotAddAnotherRecipientOrMailHeader() {
        for (String email : new String[]{"first@example.test,second@example.test", "first@example.test\r\nBcc:second@example.test"}) {
            var sender = mock(JavaMailSender.class);
            var failure = assertThrows(WorkflowException.class,
                    () -> mailer(sender, FROM, LOGIN).sendCredentials(email, "An", GENERATED_PASSWORD));
            assertEquals("MAIL_DELIVERY_FAILED", failure.code);
            verifyNoInteractions(sender);
        }
    }

    @Test
    void brandingCannotInjectSubjectOrReplyToHeaders() {
        var sender = mock(JavaMailSender.class);
        var mailer = mailer(sender, FROM, LOGIN);
        branding(mailer, "Trung tâm\r\nBcc: other@example.test", "support@example.test\r\nBcc: other@example.test");
        mailer.sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD);
        var mail = delivered(sender);
        assertFalse(mail.getSubject().contains("\r"));
        assertFalse(mail.getSubject().contains("\n"));
        assertNull(mail.getReplyTo());
        assertArrayEquals(new String[]{RECIPIENT}, mail.getTo());
    }

    @Test
    void applicationConfigurationSuppliesDefaultAndEnvironmentLoginUrls() {
        var context = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withUserConfiguration(SmtpStudentAccountMailer.class)
                .withPropertyValues("SMTP_HOST=smtp.example.test", "SMTP_USER=" + FROM,
                        "SMTP_PASSWORD=smtp-secret", "SMTP_FROM=", "SMTP_AUTH=true");
        context.run(application -> {
            assertNull(application.getStartupFailure());
            assertEquals("http://localhost:8082/login", application.getEnvironment().getProperty("app.student-accounts.login-url"));
            application.getBean(SmtpStudentAccountMailer.class).sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD);
            assertTrue(delivered(application.getBean(JavaMailSender.class)).getText().contains("http://localhost:8082/login"));
        });
        context.withPropertyValues("STUDENT_LOGIN_URL=" + LOGIN).run(application -> {
            assertNull(application.getStartupFailure());
            application.getBean(SmtpStudentAccountMailer.class).sendCredentials(RECIPIENT, "An", GENERATED_PASSWORD);
            assertTrue(delivered(application.getBean(JavaMailSender.class)).getText().contains(LOGIN));
        });
    }

    private static SmtpStudentAccountMailer mailer(JavaMailSender sender, String from, String loginUrl) {
        return new SmtpStudentAccountMailer(sender, "smtp.example.test", FROM, "smtp-secret", from, true, loginUrl);
    }

    private static SimpleMailMessage delivered(JavaMailSender sender) {
        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    private static void branding(SmtpStudentAccountMailer mailer, String name, String replyTo) {
        var settings = mock(SystemSettingsService.class);
        when(settings.branding()).thenReturn(Map.of("centerName", name, "notificationEmail", replyTo));
        @SuppressWarnings("unchecked") ObjectProvider<SystemSettingsService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(settings);
        mailer.setSystemSettings(provider);
    }

    private static void assertConfigurationFailure(WorkflowException failure) {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status);
        assertEquals("MAIL_NOT_CONFIGURED", failure.code);
        assertTrue(failure.fields.isEmpty());
        assertNull(failure.getCause());
    }
}
