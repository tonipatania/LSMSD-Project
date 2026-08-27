package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.service.IEmailService;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class EmailService implements IEmailService {

    // Nome visualizzato nel client di posta del destinatario: indipendente dall'indirizzo
    // mittente configurato in GAMEHUB_MAIL_FROM.
    private static final String SENDER_DISPLAY_NAME = "GameHub (no-reply)";

    private static final String BREVO_SEND_ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    // Se l'API Brevo e' lenta o irraggiungibile, la richiesta deve fallire in fretta invece di
    // restare appesa e bloccare la risposta HTTP della registrazione (vedi il problema analogo
    // che si aveva con la mancanza di connectiontimeout su smtp.gmail.com).
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final String frontendBaseUrl;

    public EmailService(
            RestClient.Builder restClientBuilder,
            @Value("${gamehub.brevo.api-key}") String apiKey,
            @Value("${gamehub.mail.from}") String from,
            @Value("${gamehub.frontend.base-url}") String frontendBaseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.apiKey = apiKey;
        this.from = from;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendVerificationEmail(String toEmail, String username, String token) {
        String confirmationLink = frontendBaseUrl + "/confirm-email?token=" + token;
        String text =
                "Ciao "
                        + username
                        + ",\n\n"
                        + "Grazie per esserti registrato su GameHub. Conferma il tuo account"
                        + " cliccando sul link seguente:\n\n"
                        + confirmationLink
                        + "\n\n"
                        + "Il link scade tra 24 ore. Se non hai richiesto questa registrazione,"
                        + " ignora questa email.\n\n"
                        + "Questa e' un'email automatica: non rispondere a questo indirizzo.";

        Map<String, Object> body =
                Map.of(
                        "sender", Map.of("name", SENDER_DISPLAY_NAME, "email", from),
                        "to", List.of(Map.of("email", toEmail)),
                        "subject", "Conferma il tuo account GameHub",
                        "textContent", text);

        restClient
                .post()
                .uri(BREVO_SEND_ENDPOINT)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        log.info("Email di conferma inviata a {}", toEmail);
    }
}
