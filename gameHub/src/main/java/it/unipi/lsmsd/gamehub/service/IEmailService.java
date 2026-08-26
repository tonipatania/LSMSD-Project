package it.unipi.lsmsd.gamehub.service;

public interface IEmailService {
    void sendVerificationEmail(String toEmail, String username, String token);
}
