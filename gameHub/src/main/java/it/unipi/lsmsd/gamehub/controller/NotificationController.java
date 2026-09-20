package it.unipi.lsmsd.gamehub.controller;

import it.unipi.lsmsd.gamehub.DTO.NotificationDTO;
import it.unipi.lsmsd.gamehub.service.INotificationService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Il destinatario e' sempre l'utente del token, mai un parametro: altrimenti chiunque potrebbe
// leggere o svuotare le notifiche di un altro.
@RequestMapping("notifications")
@RestController
@Slf4j
public class NotificationController {
    @Autowired private INotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getNotifications(
            @AuthenticationPrincipal String username,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(notificationService.getNotifications(username, pageable));
    }

    // il client lo interroga a intervalli per il badge della campanella: deve restare leggero
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal String username) {
        return ResponseEntity.ok(Map.of("count", notificationService.countUnread(username)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal String username, @PathVariable String id) {
        return notificationService.markRead(username, id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal String username) {
        notificationService.markAllRead(username);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal String username, @PathVariable String id) {
        return notificationService.delete(username, id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
