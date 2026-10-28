package com.erhms.user.recovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class PasswordResetMailConfiguration {
    @Bean("passwordResetMailExecutor")
    Executor passwordResetMailExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("password-reset-mail-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }

    public record Email(String recipient, String code) {
        @Override public String toString() { return "PasswordResetEmail[REDACTED]"; }
    }

    @Component
    public static class Dispatcher {
        private static final Logger LOG = LoggerFactory.getLogger(Dispatcher.class);
        private final PasswordResetMailer mailer;
        public Dispatcher(PasswordResetMailer mailer) { this.mailer = mailer; }

        @Async("passwordResetMailExecutor")
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void deliver(Email email) {
            // Unknown addresses take the same request path without revealing account existence.
            if (email.recipient() == null) return;
            try { mailer.sendCode(email.recipient(), email.code()); }
            catch (RuntimeException failure) {
                // SMTP exceptions can contain recipient/credentials; never log their contents.
                LOG.warn("Không gửi được email khôi phục mật khẩu. Kiểm tra cấu hình SMTP.");
            }
        }
    }
}
