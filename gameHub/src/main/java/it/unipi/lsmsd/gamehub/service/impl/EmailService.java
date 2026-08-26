package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.service.IEmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService implements IEmailService {

    // Nome visualizzato nel client di posta del destinatario: indipendente dalla parte locale
    // dell'indirizzo del mittente (GAMEHUB_MAIL_USERNAME puo' restare "gamehub@gmail.com" o
    // qualsiasi altro nome, non serve chiamare l'account "no-reply").
    private static final String SENDER_DISPLAY_NAME = "GameHub (no-reply)";

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendBaseUrl;

    public EmailService(
            JavaMailSender mailSender,
            @Value("${gamehub.mail.from}") String from,
            @Value("${gamehub.frontend.base-url}") String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String username, String token) {
        String confirmationLink = frontendBaseUrl + "/confirm-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        // Le virgolette attorno al nome sono necessarie: contenendo parentesi (caratteri
        // speciali nel formato email RFC822, che senza escaping delimiterebbero un commento),
        // il nome visualizzato va racchiuso come stringa letterale.
        message.setFrom("\"" + SENDER_DISPLAY_NAME + "\" <" + from + ">");
        message.setTo(toEmail);
        message.setSubject("Conferma il tuo account GameHub");
        message.setText(
                "Ciao "
                        + username
                        + ",\n\n"
                        + "Grazie per esserti registrato su GameHub. Conferma il tuo account"
                        + " cliccando sul link seguente:\n\n"
                        + confirmationLink
                        + "\n\n"
                        + "Il link scade tra 24 ore. Se non hai richiesto questa registrazione,"
                        + " ignora questa email.\n\n"
                        + "Questa e' un'email automatica: non rispondere a questo indirizzo.");

        mailSender.send(message);
        log.info("Email di conferma inviata a {}", toEmail);
    }
}
