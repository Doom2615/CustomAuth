// EmailVerification.java
package dev.doom.customauth;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EmailVerification {
    private final CustomAuth plugin;
    private final Session emailSession;

    public EmailVerification(CustomAuth plugin) {
        this.plugin = plugin;
        Properties props = new Properties();
        props.put("mail.smtp.host", plugin.getConfig().getString("email.smtp.host"));
        props.put("mail.smtp.port", plugin.getConfig().getString("email.smtp.port"));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        emailSession = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                    plugin.getConfig().getString("email.username"),
                    plugin.getConfig().getString("email.password")
                );
            }
        });
    }

    public CompletableFuture<Boolean> sendVerificationEmail(String username, String email) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String token = generateVerificationToken();
                saveVerificationToken(username, token);

                Message message = new MimeMessage(emailSession);
                message.setFrom(new InternetAddress(plugin.getConfig().getString("email.from")));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(email));
                message.setSubject("Verify your account");

                String verificationLink = plugin.getConfig().getString("email.verification_url") +
                    "?token=" + token;

                String emailTemplate = plugin.getConfigManager().getEmailTemplate()
                    .replace("{username}", username)
                    .replace("{verification_link}", verificationLink)
                    .replace("{server_name}", plugin.getConfig().getString("server.name"));

                message.setText(emailTemplate);
                Transport.send(message);
                return true;
            } catch (MessagingException e) {
                plugin.getLogger().severe("Failed to send verification email: " + e.getMessage());
                return false;
            }
        });
    }

    private String generateVerificationToken() {
        return UUID.randomUUID().toString();
    }

    private void saveVerificationToken(String username, String token) {
        plugin.getDatabase().saveVerificationToken(username, token);
    }

    public CompletableFuture<Boolean> verifyToken(String token) {
        return plugin.getDatabase().verifyEmailToken(token);
    }
}
