package it.unipi.lsmsd.gamehub.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.unipi.lsmsd.gamehub.DTO.NotificationDTO;
import it.unipi.lsmsd.gamehub.model.NotificationType;
import it.unipi.lsmsd.gamehub.security.JwtService;
import it.unipi.lsmsd.gamehub.security.SecurityConfig;
import it.unipi.lsmsd.gamehub.security.TokenBlacklistService;
import it.unipi.lsmsd.gamehub.service.INotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ExtendWith(SpringExtension.class)
@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private INotificationService notificationService;

    // see LoginControllerTest for why these two are required even with addFilters = false
    @MockBean private JwtService jwtService;
    @MockBean private TokenBlacklistService tokenBlacklistService;

    // con addFilters = false il JwtAuthenticationFilter non gira: si imposta a mano il contesto di
    // sicurezza, come fa il filtro vero (principal = username in chiaro)
    private static RequestPostProcessor asUser(String username) {
        return request -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    username,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            return request;
        };
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getNotifications_usesTheTokenUserNotAParameter() throws Exception {
        NotificationDTO dto = new NotificationDTO();
        dto.setId("n1");
        dto.setType(NotificationType.LIKE_REVIEW);
        dto.setActor("Lunark");
        dto.setCreatedAt(Instant.parse("2026-09-20T10:00:00Z"));
        // una Page "unpaged" non si serializza (getOffset lancia): serve una pagina vera
        when(notificationService.getNotifications(eq("Kaistlin"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/notifications").with(asUser("Kaistlin")).param("recipient", "Mira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("n1"))
                .andExpect(jsonPath("$.content[0].type").value("LIKE_REVIEW"))
                .andExpect(jsonPath("$.content[0].actor").value("Lunark"));
    }

    @Test
    void getUnreadCount_returnsTheCountOfTheTokenUser() throws Exception {
        when(notificationService.countUnread("Kaistlin")).thenReturn(3L);

        mockMvc.perform(get("/notifications/unread-count").with(asUser("Kaistlin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void markRead_ownNotification_returnsNoContent() throws Exception {
        when(notificationService.markRead("Kaistlin", "n1")).thenReturn(true);

        mockMvc.perform(post("/notifications/n1/read").with(asUser("Kaistlin")))
                .andExpect(status().isNoContent());
    }

    @Test
    void markRead_notificationOfSomeoneElse_returnsNotFound() throws Exception {
        when(notificationService.markRead("Kaistlin", "n9")).thenReturn(false);

        mockMvc.perform(post("/notifications/n9/read").with(asUser("Kaistlin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void markAllRead_marksTheTokenUsersNotifications() throws Exception {
        mockMvc.perform(post("/notifications/read-all").with(asUser("Kaistlin")))
                .andExpect(status().isNoContent());

        verify(notificationService).markAllRead("Kaistlin");
    }

    @Test
    void delete_ownNotification_returnsNoContent() throws Exception {
        when(notificationService.delete("Kaistlin", "n1")).thenReturn(true);

        mockMvc.perform(delete("/notifications/n1").with(asUser("Kaistlin")))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notificationOfSomeoneElse_returnsNotFound() throws Exception {
        when(notificationService.delete("Kaistlin", "n9")).thenReturn(false);

        mockMvc.perform(delete("/notifications/n9").with(asUser("Kaistlin")))
                .andExpect(status().isNotFound());

        verify(notificationService, never()).markAllRead(any());
    }
}
