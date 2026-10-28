package com.erhms;

import com.erhms.user.recovery.PasswordResetException;
import com.erhms.user.recovery.SmtpPasswordResetMailer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.*;

class MailConfigurationBindingTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(SmtpPasswordResetMailer.class)
            .withPropertyValues("SMTP_HOST=127.0.0.1", "SMTP_PORT=587", "SMTP_AUTH=true",
                    "SMTP_USER=sender@example.test", "SMTP_PASSWORD=TestAppPassword", "SMTP_FROM=");

    @Test
    void environmentCredentialsWorkEvenWhenComposeSuppliesAnEmptySenderAddress() {
        context.run(application -> {
            assertNull(application.getStartupFailure());
            assertEquals("", application.getEnvironment().getProperty("app.mail.from"));
            assertDoesNotThrow(application.getBean(SmtpPasswordResetMailer.class)::requireConfigured);
            var sender = application.getBean(JavaMailSenderImpl.class);
            assertEquals("sender@example.test", sender.getUsername());
            assertEquals("TestAppPassword", sender.getPassword());
            assertEquals("127.0.0.1", sender.getHost());
            assertEquals(587, sender.getPort());
        });
    }

    @Test
    void directSpringMailCredentialsRemainUsableWithAnEmptySmtpFromOverride() {
        context.withPropertyValues("spring.mail.username=direct@example.test",
                "spring.mail.password=DirectAppPassword").run(application -> {
            assertNull(application.getStartupFailure());
            assertDoesNotThrow(application.getBean(SmtpPasswordResetMailer.class)::requireConfigured);
            var sender = application.getBean(JavaMailSenderImpl.class);
            assertEquals("direct@example.test", sender.getUsername());
            assertEquals("DirectAppPassword", sender.getPassword());
        });
    }

    @Test
    void explicitSenderAndCredentialsUseTheSameApplicationConfiguration() {
        context.withPropertyValues("app.mail.from=explicit@example.test").run(application -> {
            assertNull(application.getStartupFailure());
            assertEquals("explicit@example.test", application.getEnvironment().getProperty("app.mail.from"));
            assertDoesNotThrow(application.getBean(SmtpPasswordResetMailer.class)::requireConfigured);
        });
    }

    @Test
    void blankCredentialsStillReportMissingConfigurationBeforeAnySmtpConnection() {
        context.withPropertyValues("SMTP_USER=", "SMTP_PASSWORD=").run(application -> {
            assertNull(application.getStartupFailure());
            var failure = assertThrows(PasswordResetException.class,
                    application.getBean(SmtpPasswordResetMailer.class)::requireConfigured);
            assertEquals("MAIL_NOT_CONFIGURED", failure.code);
            assertEquals(503, failure.status.value());
            assertFalse(failure.getMessage().contains("spring.mail"));
        });
    }
}
