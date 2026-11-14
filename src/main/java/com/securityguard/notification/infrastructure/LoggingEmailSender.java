package com.securityguard.notification.infrastructure;

import com.securityguard.notification.application.EmailSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Placeholder adapter: no SMTP/email provider configured in this environment. See LoggingFcmClient for the same pattern. */
@Component
public class LoggingEmailSender implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public SendOutcome send(String toEmail, String subject, String body) {
        log.info("Email send (stub): to={} subject='{}'", toEmail, subject);
        return SendOutcome.SUCCESS;
    }
}
