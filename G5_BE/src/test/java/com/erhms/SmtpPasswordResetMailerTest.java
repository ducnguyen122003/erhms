package com.erhms;

import com.erhms.user.recovery.PasswordResetException;
import com.erhms.user.recovery.SmtpPasswordResetMailer;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
class SmtpPasswordResetMailerTest {
    private static final String FROM = "erhms-sender@example.test";
    private static final String RECIPIENT = "learner@example.test";

    @Test
    void sendsMimeMessageThroughLocalSmtpAndPreservesLeadingZeroCode() throws Exception {
        try (var smtp = new LocalSmtp()) {
            var sender = sender(smtp.port());
            var mailer = new SmtpPasswordResetMailer(sender, "127.0.0.1", "", "", FROM, false);

            assertDoesNotThrow(mailer::requireConfigured);
            mailer.sendCode(RECIPIENT, "001234");

            CapturedMail captured = smtp.awaitMail();
            var message = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(captured.message()));
            assertEquals(FROM, captured.envelopeFrom());
            assertEquals(List.of(RECIPIENT), captured.envelopeRecipients());
            assertEquals(FROM, ((InternetAddress) message.getFrom()[0]).getAddress());
            assertEquals(1, message.getRecipients(Message.RecipientType.TO).length);
            assertEquals(RECIPIENT, ((InternetAddress) message.getRecipients(Message.RecipientType.TO)[0]).getAddress());
            assertTrue(message.getSubject().contains("ERHMS"), "Mail must identify the training center");
            assertTrue(message.getSubject().contains("đặt lại mật khẩu"), "Mail must describe password recovery");
            String body = (String) message.getContent();
            assertTrue(body.contains("001234"), "Mail must preserve all six code digits including leading zeroes");
            assertTrue(body.contains("6 chữ số"), "Mail must explain the six-digit code");
            assertTrue(body.contains("10 phút"), "Mail must explain the ten-minute expiry");
            assertTrue(body.contains("một lần"), "Mail must explain that the code is single use");
        }
    }

    @Test
    void blankFromFallsBackToUsernameInSmtpEnvelopeAndMimeMessage() throws Exception {
        for (String from : new String[]{null, "", " \t "}) {
            try (var smtp = new LocalSmtp()) {
                var mailer = new SmtpPasswordResetMailer(sender(smtp.port()), "127.0.0.1",
                        " " + FROM + " ", "configured-password", from, true);

                assertDoesNotThrow(mailer::requireConfigured);
                mailer.sendCode(RECIPIENT, "001234");

                CapturedMail captured = smtp.awaitMail();
                var message = new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(captured.message()));
                assertEquals(FROM, captured.envelopeFrom());
                assertEquals(FROM, ((InternetAddress) message.getFrom()[0]).getAddress());
                assertEquals(List.of(RECIPIENT), captured.envelopeRecipients());
                assertTrue(((String) message.getContent()).contains("001234"));
            }
        }
    }

    @Test
    void explicitFromTakesPrecedenceOverUsernameAndTrimsSurroundingWhitespace() throws Exception {
        try (var smtp = new LocalSmtp()) {
            var mailer = new SmtpPasswordResetMailer(sender(smtp.port()), "127.0.0.1",
                    "another-sender@example.test", "", " \t" + FROM + " \t", false);

            assertDoesNotThrow(mailer::requireConfigured);
            mailer.sendCode(RECIPIENT, "001234");

            CapturedMail captured = smtp.awaitMail();
            var message = new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(captured.message()));
            assertEquals(FROM, captured.envelopeFrom());
            assertEquals(FROM, ((InternetAddress) message.getFrom()[0]).getAddress());
        }
    }

    @Test
    void authenticatedSmtpRejectsMissingHostUsernameAndPassword() {
        assertNotConfigured(" ", "configured-user", "configured-password", FROM, true);
        assertNotConfigured(null, "configured-user", "configured-password", FROM, true);
        assertNotConfigured("127.0.0.1", " ", "configured-password", FROM, true);
        assertNotConfigured("127.0.0.1", null, "configured-password", FROM, true);
        assertNotConfigured("127.0.0.1", "configured-user", " ", FROM, true);
        assertNotConfigured("127.0.0.1", "configured-user", null, FROM, true);
        assertNotConfigured("127.0.0.1", " ", "configured-password", " ", true);
    }

    @Test
    void unauthenticatedLocalSmtpStillRequiresSenderAndHost() {
        assertNotConfigured("", "", "", FROM, false);
        assertNotConfigured("127.0.0.1", "", "", "", false);
        assertNotConfigured("127.0.0.1", null, null, null, false);
        assertNotConfigured("127.0.0.1", " \t", "", " \t", false);
        var mailer = new SmtpPasswordResetMailer(new JavaMailSenderImpl(), "127.0.0.1", "", "", FROM, false);
        assertDoesNotThrow(mailer::requireConfigured);
    }

    private static void assertNotConfigured(String host, String username, String password, String from, boolean authentication) {
        var mailer = new SmtpPasswordResetMailer(new JavaMailSenderImpl(), host, username, password, from, authentication);
        PasswordResetException failure = assertThrows(PasswordResetException.class, mailer::requireConfigured);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.status);
        assertEquals("MAIL_NOT_CONFIGURED", failure.code);
    }

    private static JavaMailSenderImpl sender(int port) {
        var sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "false");
        properties.setProperty("mail.smtp.starttls.enable", "false");
        properties.setProperty("mail.smtp.connectiontimeout", "4000");
        properties.setProperty("mail.smtp.timeout", "4000");
        properties.setProperty("mail.smtp.writetimeout", "4000");
        properties.setProperty("mail.debug", "false");
        sender.getSession().setDebug(false);
        return sender;
    }

    private record CapturedMail(String envelopeFrom, List<String> envelopeRecipients, byte[] message) {}

    /** One bounded loopback SMTP exchange. Message data and credentials are never logged. */
    private static final class LocalSmtp implements AutoCloseable {
        private final ServerSocket listener;
        private final ExecutorService executor;
        private final Future<CapturedMail> mail;
        private volatile Socket connection;
        private volatile boolean closed;

        private LocalSmtp() throws IOException {
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            listener.setSoTimeout(4000);
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "password-reset-test-smtp");
                thread.setDaemon(true);
                return thread;
            });
            mail = executor.submit(this::receive);
        }

        private int port() { return listener.getLocalPort(); }

        private CapturedMail awaitMail() throws Exception { return mail.get(5, TimeUnit.SECONDS); }

        private CapturedMail receive() throws IOException {
            try (Socket socket = listener.accept()) {
                connection = socket;
                if (closed) throw new IOException("SMTP fixture is closed");
                socket.setSoTimeout(4000);
                var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
                var output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
                reply(output, "220 localhost test SMTP ready");
                String from = null;
                var recipients = new ArrayList<String>();
                byte[] message = null;
                String line;
                while ((line = input.readLine()) != null) {
                    String command = line.toUpperCase(Locale.ROOT);
                    if (command.startsWith("EHLO ")) reply(output, "250-localhost\r\n250 SIZE 1048576");
                    else if (command.startsWith("HELO ")) reply(output, "250 localhost");
                    else if (command.startsWith("MAIL FROM:")) { from = mailbox(line.substring(10)); reply(output, "250 Sender accepted"); }
                    else if (command.startsWith("RCPT TO:")) { recipients.add(mailbox(line.substring(8))); reply(output, "250 Recipient accepted"); }
                    else if (command.equals("DATA")) {
                        reply(output, "354 End message with a single dot");
                        var raw = new StringBuilder();
                        while ((line = input.readLine()) != null && !line.equals(".")) {
                            if (line.startsWith("..")) line = line.substring(1);
                            raw.append(line).append("\r\n");
                            if (raw.length() > 1_048_576) throw new IOException("SMTP fixture message exceeded its size limit");
                        }
                        if (line == null) throw new IOException("SMTP fixture received incomplete message data");
                        message = raw.toString().getBytes(StandardCharsets.ISO_8859_1);
                        reply(output, "250 Message accepted");
                    }
                    else if (command.equals("QUIT")) { reply(output, "221 Bye"); break; }
                    else if (command.equals("NOOP") || command.equals("RSET")) reply(output, "250 OK");
                    else reply(output, "502 Command not implemented");
                }
                if (message == null || from == null || recipients.isEmpty()) throw new IOException("SMTP fixture did not receive a complete mail envelope");
                return new CapturedMail(from, List.copyOf(recipients), message);
            }
        }

        private static String mailbox(String argument) throws IOException {
            String value = argument.trim();
            int end = value.indexOf('>');
            if (!value.startsWith("<") || end < 1) throw new IOException("SMTP fixture received an invalid envelope address");
            return value.substring(1, end);
        }

        private static void reply(BufferedWriter output, String response) throws IOException {
            output.write(response + "\r\n");
            output.flush();
        }

        @Override public void close() throws Exception {
            closed = true;
            listener.close();
            Socket accepted = connection;
            if (accepted != null) accepted.close();
            mail.cancel(true);
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS), "SMTP fixture thread must terminate");
        }
    }
}
