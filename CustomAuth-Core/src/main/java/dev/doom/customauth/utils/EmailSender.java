// dev/doom/customauth/utils/EmailSender.java
package dev.doom.customauth.utils;

import dev.doom.customauth.CustomAuth;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class EmailSender {
    private final CustomAuth plugin;
    private final Session emailSession;
    private final String fromAddress;
    private final RateLimiter emailRateLimiter;

    public EmailSender(CustomAuth plugin) {
        this.plugin = plugin;
        this.emailRateLimiter = RateLimiter.create(1.0); // Max 1 email per second
        this.fromAddress = plugin.getConfig().getString("email.smtp.from");

        Properties props = new Properties();
        props.put("mail.smtp.host", plugin.getConfig().getString("email.smtp.host"));
        props.put("mail.smtp.port", plugin.getConfig().getString("email.smtp.port"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", plugin.getConfig().getBoolean("email.smtp.starttls"));
        props.put("mail.smtp.ssl.enable", plugin.getConfig().getBoolean("email.smtp.ssl"));

        emailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    plugin.getConfig().getString("email.smtp.username"),
                    plugin.getConfig().getString("email.smtp.password")
                );
            }
        });
    }

    public CompletableFuture<Boolean> sendVerificationEmail(String username, String email, String token) {
        if (!emailRateLimiter.tryAcquire()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Message message = new MimeMessage(emailSession);
                message.setFrom(new InternetAddress(fromAddress));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                
                String subject = plugin.getConfig().getString("emails.verification_email.subject")
                    .replace("%server_name%", plugin.getConfig().getString("server.name"));
                
                String body = plugin.getConfig().getString("emails.verification_email.body")
                    .replace("%username%", username)
                    .replace("%server_name%", plugin.getConfig().getString("server.name"))
                    .replace("%verification_link%", 
                        plugin.getConfig().getString("email.verification_url") + "?token=" + token);

                message.setSubject(subject);
                message.setText(body);

                Transport.send(message);
                return true;
            } catch (MessagingException e) {
                plugin.getLogger().severe("Failed to send verification email: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> sendPasswordResetEmail(String username, String email, String token) {
        if (!emailRateLimiter.tryAcquire()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Message message = new MimeMessage(emailSession);
                message.setFrom(new InternetAddress(fromAddress));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                
                String subject = plugin.getConfig().getString("emails.password_reset_email.subject")
                    .replace("%server_name%", plugin.getConfig().getString("server.name"));
                
                String body = plugin.getConfig().getString("emails.password_reset_email.body")
                    .replace("%username%", username)
                    .replace("%server_name%", plugin.getConfig().getString("server.name"))
                    .replace("%reset_link%", 
                        plugin.getConfig().getString("email.reset_url") + "?token=" + token);

                message.setSubject(subject);
                message.setText(body);

                Transport.send(message);
                return true;
            } catch (MessagingException e) {
                plugin.getLogger().severe("Failed to send password reset email: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }

    public CompletableFuture<Boolean> sendPasswordChangeNotification(String username, String email) {
        if (!emailRateLimiter.tryAcquire()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Message message = new MimeMessage(emailSession);
                message.setFrom(new InternetAddress(fromAddress));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                
                String subject = plugin.getConfig().getString("emails.password_changed_email.subject")
                    .replace("%server_name%", plugin.getConfig().getString("server.name"));
                
                String body = plugin.getConfig().getString("emails.password_changed_email.body")
                    .replace("%username%", username)
                    .replace("%server_name%", plugin.getConfig().getString("server.name"));

                message.setSubject(subject);
                message.setText(body);

                Transport.send(message);
                return true;
            } catch (MessagingException e) {
                plugin.getLogger().severe("Failed to send password change notification: " + e.getMessage());
                return false;
            }
        }, plugin.getAsyncExecutor());
    }
}
