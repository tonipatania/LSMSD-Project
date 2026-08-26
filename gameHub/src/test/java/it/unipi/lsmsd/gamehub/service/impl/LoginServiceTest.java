package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.Mockito.verifyNoInteractions;

import com.mongodb.MongoException;
import it.unipi.lsmsd.gamehub.DTO.LoginDTO;
import it.unipi.lsmsd.gamehub.DTO.RegistrationDTO;
import it.unipi.lsmsd.gamehub.model.User;
import it.unipi.lsmsd.gamehub.repository.LoginRepository;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.service.IEmailService;
import it.unipi.lsmsd.gamehub.utils.AuthResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock private LoginRepository loginRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private IEmailService emailService;

    @InjectMocks private LoginService loginService;

    private User bcryptUser() {
        User user = new User();
        user.setId("u1");
        user.setUsername("Lunark");
        user.setPassword("$2a$10$hashedvalue");
        user.setRole(null);
        return user;
    }

    private User plaintextUser() {
        User user = new User();
        user.setId("u1");
        user.setUsername("Lunark");
        user.setPassword("plaintextpwd");
        user.setRole(null);
        return user;
    }

    @Test
    void authenticate_unknownUsername_returnsUnsuccessfulResponse() {
        when(loginRepository.findByUsername("ghost")).thenReturn(null);

        AuthResponse response = loginService.authenticate(new LoginDTO("ghost", "pwd"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("Invalid username or password");
    }

    @Test
    void authenticate_bcryptPasswordMismatch_returnsUnsuccessfulResponse() {
        User user = bcryptUser();
        when(loginRepository.findByUsername("Lunark")).thenReturn(user);
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        AuthResponse response = loginService.authenticate(new LoginDTO("Lunark", "wrong"));

        assertThat(response.isSuccess()).isFalse();
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_bcryptPasswordMatch_returnsTokenAndDoesNotRewritePassword() {
        User user = bcryptUser();
        when(loginRepository.findByUsername("Lunark")).thenReturn(user);
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken("Lunark", "USER")).thenReturn("jwt-token");

        AuthResponse response = loginService.authenticate(new LoginDTO("Lunark", "correct"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getToken()).isEqualTo("jwt-token");
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_legacyPlaintextPasswordMatch_migratesToHashAndSaves() {
        User user = plaintextUser();
        when(loginRepository.findByUsername("Lunark")).thenReturn(user);
        when(passwordEncoder.encode("plaintextpwd")).thenReturn("$2a$10$freshhash");
        when(jwtService.generateToken("Lunark", "USER")).thenReturn("jwt-token");

        AuthResponse response = loginService.authenticate(new LoginDTO("Lunark", "plaintextpwd"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(user.getPassword()).isEqualTo("$2a$10$freshhash");
        verify(loginRepository).save(user);
    }

    @Test
    void authenticate_legacyPlaintextPasswordMismatch_returnsUnsuccessfulResponseWithoutSaving() {
        User user = plaintextUser();
        when(loginRepository.findByUsername("Lunark")).thenReturn(user);

        AuthResponse response = loginService.authenticate(new LoginDTO("Lunark", "otherpwd"));

        assertThat(response.isSuccess()).isFalse();
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void authenticate_mongoExceptionOnLookup_returnsUnsuccessfulResponse() {
        when(loginRepository.findByUsername("Lunark")).thenThrow(new MongoException("down"));

        AuthResponse response = loginService.authenticate(new LoginDTO("Lunark", "pwd"));

        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void authenticate_accountNotConfirmed_returnsUnsuccessfulResponseWithoutToken() {
        User user = bcryptUser();
        user.setEnabled(false);
        when(loginRepository.findByUsername("Lunark")).thenReturn(user);
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);

        AuthResponse response = loginService.authenticate(new LoginDTO("Lunark", "correct"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("non confermato");
        assertThat(response.getToken()).isNull();
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void authenticate_legacyUserWithNullEnabled_succeeds() {
        User user = bcryptUser();
        assertThat(user.getEnabled()).isNull();
        when(loginRepository.findByUsername("Lunark")).thenReturn(user);
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken("Lunark", "USER")).thenReturn("jwt-token");

        AuthResponse response = loginService.authenticate(new LoginDTO("Lunark", "correct"));

        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void roleUser_userWithRole_returnsRoleWithOkStatus() {
        User admin = bcryptUser();
        admin.setRole("ADMIN");
        when(loginRepository.findById("u1")).thenReturn(Optional.of(admin));

        ResponseEntity<String> response = loginService.roleUser("u1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("ADMIN");
    }

    @Test
    void roleUser_userWithoutRole_returnsUnauthorized() {
        User plainUser = bcryptUser();
        plainUser.setRole(null);
        when(loginRepository.findById("u1")).thenReturn(Optional.of(plainUser));

        ResponseEntity<String> response = loginService.roleUser("u1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void roleUser_mongoExceptionOnLookup_returnsInternalServerError() {
        when(loginRepository.findById("u1")).thenThrow(new MongoException("down"));

        ResponseEntity<String> response = loginService.roleUser("u1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private RegistrationDTO registrationDTO() {
        return new RegistrationDTO("Mario", "Rossi", "mariorossi", "pwd123", "mario@test.it");
    }

    @Test
    void registrate_usernameAlreadyUsed_returnsConflict() {
        when(loginRepository.existsByUsername("mariorossi")).thenReturn(true);

        ResponseEntity<String> response = loginService.registrate(registrationDTO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void registrate_emailAlreadyUsed_returnsConflict() {
        when(loginRepository.existsByUsername("mariorossi")).thenReturn(false);
        when(loginRepository.existsByEmail("mario@test.it")).thenReturn(true);

        ResponseEntity<String> response = loginService.registrate(registrationDTO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void registrate_newUser_savesEncodedPasswordAndReturnsCreated() {
        when(loginRepository.existsByUsername("mariorossi")).thenReturn(false);
        when(loginRepository.existsByEmail("mario@test.it")).thenReturn(false);
        when(passwordEncoder.encode("pwd123")).thenReturn("$2a$10$encoded");
        when(loginRepository.save(any(User.class)))
                .thenAnswer(
                        invocation -> {
                            User u = invocation.getArgument(0);
                            u.setId("newId");
                            return u;
                        });

        ResponseEntity<String> response = loginService.registrate(registrationDTO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("newId");
        verify(loginRepository)
                .save(
                        argThat(
                                u ->
                                        "$2a$10$encoded".equals(u.getPassword())
                                                && "mariorossi".equals(u.getUsername())
                                                && Boolean.FALSE.equals(u.getEnabled())));
    }

    @Test
    void registrate_concurrentDuplicateEmail_returnsConflictWithEmailMessage() {
        when(loginRepository.existsByUsername("mariorossi")).thenReturn(false);
        when(loginRepository.existsByEmail("mario@test.it")).thenReturn(false);
        when(passwordEncoder.encode("pwd123")).thenReturn("$2a$10$encoded");
        when(loginRepository.save(any(User.class)))
                .thenThrow(new DuplicateKeyException("E11000 duplicate key error ... email_1 ..."));

        ResponseEntity<String> response = loginService.registrate(registrationDTO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Email");
    }

    @Test
    void registrate_concurrentDuplicateUsername_returnsConflictWithUsernameMessage() {
        when(loginRepository.existsByUsername("mariorossi")).thenReturn(false);
        when(loginRepository.existsByEmail("mario@test.it")).thenReturn(false);
        when(passwordEncoder.encode("pwd123")).thenReturn("$2a$10$encoded");
        when(loginRepository.save(any(User.class)))
                .thenThrow(
                        new DuplicateKeyException("E11000 duplicate key error ... username_1 ..."));

        ResponseEntity<String> response = loginService.registrate(registrationDTO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Username");
    }

    @Test
    void registrate_unexpectedException_returnsInternalServerError() {
        when(loginRepository.existsByUsername("mariorossi")).thenReturn(false);
        when(loginRepository.existsByEmail("mario@test.it")).thenReturn(false);
        when(passwordEncoder.encode("pwd123")).thenReturn("$2a$10$encoded");
        when(loginRepository.save(any(User.class))).thenThrow(new RuntimeException("boom"));

        ResponseEntity<String> response = loginService.registrate(registrationDTO());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void removeUser_repositoryDeletes_returnsOk() {
        ResponseEntity<String> response = loginService.removeUser("u1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(loginRepository).deleteById("u1");
    }

    @Test
    void removeUser_repositoryThrows_returnsInternalServerError() {
        doThrow(new RuntimeException("boom")).when(loginRepository).deleteById("u1");

        ResponseEntity<String> response = loginService.removeUser("u1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void updateUser_newUsernameAlreadyTaken_returnsConflict() {
        when(loginRepository.findByUsername("newName")).thenReturn(bcryptUser());

        ResponseEntity<String> response = loginService.updateUser("oldName", "newName");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void updateUser_newUsernameFree_updatesAndReturnsOk() {
        User existing = bcryptUser();
        existing.setUsername("oldName");
        when(loginRepository.findByUsername("newName")).thenReturn(null);
        when(loginRepository.findByUsername("oldName")).thenReturn(existing);
        when(loginRepository.save(existing)).thenReturn(existing);

        ResponseEntity<String> response = loginService.updateUser("oldName", "newName");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(existing.getUsername()).isEqualTo("newName");
    }

    @Test
    void updateUser_repositoryThrows_returnsInternalServerError() {
        when(loginRepository.findByUsername("newName")).thenReturn(null);
        when(loginRepository.findByUsername("oldName")).thenThrow(new RuntimeException("boom"));

        ResponseEntity<String> response = loginService.updateUser("oldName", "newName");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void sendVerificationEmail_existingUser_generatesTokenSavesAndSendsEmail() {
        User user = bcryptUser();
        user.setEmail("lunark@test.it");
        when(loginRepository.findById("u1")).thenReturn(Optional.of(user));

        loginService.sendVerificationEmail("u1");

        assertThat(user.getVerificationToken()).isNotBlank();
        assertThat(user.getVerificationTokenExpiry()).isNotNull();
        verify(loginRepository).save(user);
        verify(emailService)
                .sendVerificationEmail("lunark@test.it", "Lunark", user.getVerificationToken());
    }

    @Test
    void sendVerificationEmail_unknownUser_doesNothing() {
        when(loginRepository.findById("ghost")).thenReturn(Optional.empty());

        loginService.sendVerificationEmail("ghost");

        verify(loginRepository, never()).save(any(User.class));
        verifyNoInteractions(emailService);
    }

    @Test
    void sendVerificationEmail_emailSendingThrows_doesNotPropagate() {
        User user = bcryptUser();
        when(loginRepository.findById("u1")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("smtp down"))
                .when(emailService)
                .sendVerificationEmail(any(), any(), any());

        loginService.sendVerificationEmail("u1");

        verify(loginRepository).save(user);
    }

    @Test
    void confirmEmail_validToken_enablesAccountAndReturnsOk() {
        User user = bcryptUser();
        user.setEnabled(false);
        user.setVerificationToken("tok-123");
        user.setVerificationTokenExpiry(System.currentTimeMillis() + 60_000);
        when(loginRepository.findByVerificationToken("tok-123")).thenReturn(user);

        ResponseEntity<String> response = loginService.confirmEmail("tok-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getEnabled()).isTrue();
        verify(loginRepository).save(user);
    }

    @Test
    void confirmEmail_tokenReusedAfterConfirmation_returnsOkInsteadOfInvalidToken() {
        // Un client email che pre-carica il link (es. scanner anti-phishing aziendali) o un
        // doppio click dell'utente non deve rompere la conferma gia' avvenuta: il token resta
        // valido e riutilizzabile una volta che l'account e' confermato, si veda il commento in
        // LoginService.confirmEmail.
        User user = bcryptUser();
        user.setEnabled(false);
        user.setVerificationToken("tok-123");
        user.setVerificationTokenExpiry(System.currentTimeMillis() + 60_000);
        when(loginRepository.findByVerificationToken("tok-123")).thenReturn(user);

        loginService.confirmEmail("tok-123");
        ResponseEntity<String> secondResponse = loginService.confirmEmail("tok-123");

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirmEmail_unknownToken_returnsBadRequest() {
        when(loginRepository.findByVerificationToken("bad-token")).thenReturn(null);

        ResponseEntity<String> response = loginService.confirmEmail("bad-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void confirmEmail_expiredToken_returnsGone() {
        User user = bcryptUser();
        user.setEnabled(false);
        user.setVerificationToken("tok-123");
        user.setVerificationTokenExpiry(System.currentTimeMillis() - 1_000);
        when(loginRepository.findByVerificationToken("tok-123")).thenReturn(user);

        ResponseEntity<String> response = loginService.confirmEmail("tok-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(user.getEnabled()).isFalse();
        verify(loginRepository, never()).save(any(User.class));
    }

    @Test
    void confirmEmail_alreadyConfirmed_returnsOkWithoutResaving() {
        User user = bcryptUser();
        user.setEnabled(true);
        when(loginRepository.findByVerificationToken("tok-123")).thenReturn(user);

        ResponseEntity<String> response = loginService.confirmEmail("tok-123");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(loginRepository, never()).save(any(User.class));
    }
}
