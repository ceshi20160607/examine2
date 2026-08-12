package com.unique.examine.event.config;

import com.unique.examine.core.api.AccountRecoveryMailFacade;
import com.unique.examine.event.adapter.SmtpAccountRecoveryMailAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AccountRecoveryMailProperties.class)
public class EventMailConfiguration {
    private static final String SMTP_TIMEOUT_MILLIS = "3000";

    @Bean
    AccountRecoveryMailFacade accountRecoveryMailFacade(
            AccountRecoveryMailProperties properties
    ) {
        if (properties == null || !properties.isReady()) {
            return command -> {
                if (command == null) {
                    throw new IllegalArgumentException(
                            "Account recovery mail command is required");
                }
                return new AccountRecoveryMailFacade.DeliveryReceipt(
                        command.requestId(), AccountRecoveryMailFacade.Status.UNAVAILABLE);
            };
        }

        var sender = new JavaMailSenderImpl();
        sender.setHost(properties.getHost());
        sender.setPort(properties.getPort());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (properties.isSmtpAuth()) {
            sender.setUsername(properties.getUsername());
            sender.setPassword(properties.getPassword());
        }
        var mail = sender.getJavaMailProperties();
        mail.setProperty("mail.smtp.auth", Boolean.toString(properties.isSmtpAuth()));
        mail.setProperty("mail.smtp.starttls.enable", Boolean.toString(properties.isStartTls()));
        mail.setProperty("mail.smtp.starttls.required", Boolean.toString(properties.isStartTls()));
        mail.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        mail.setProperty("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MILLIS);
        mail.setProperty("mail.smtp.timeout", SMTP_TIMEOUT_MILLIS);
        mail.setProperty("mail.smtp.writetimeout", SMTP_TIMEOUT_MILLIS);
        mail.setProperty("mail.debug", "false");

        return new SmtpAccountRecoveryMailAdapter(
                sender, properties.getFrom(), properties.getPublicBaseUrl(), Clock.systemUTC());
    }
}
