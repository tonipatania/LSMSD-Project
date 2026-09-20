package it.unipi.lsmsd.gamehub.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import it.unipi.lsmsd.gamehub.DTO.NotificationDTO;
import it.unipi.lsmsd.gamehub.model.Notification;
import it.unipi.lsmsd.gamehub.model.NotificationType;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.model.ReviewReply;
import it.unipi.lsmsd.gamehub.repository.NotificationRepository;
import it.unipi.lsmsd.gamehub.repository.UserNeo4jRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserNeo4jRepository userNeo4jRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks private NotificationService notificationService;

    private final Pageable pageable = PageRequest.of(0, 20);

    private Review review(String author, String comment) {
        Review review = new Review();
        review.setId("r1");
        review.setTitle("BARRIER X");
        review.setUsername(author);
        review.setComment(comment);
        return review;
    }

    private Notification saved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private Notification notification(String id, NotificationType type, String actor) {
        return new Notification(
                id,
                "Kaistlin",
                type,
                actor,
                "BARRIER X",
                "r1",
                null,
                "Bello",
                false,
                Instant.now());
    }

    // --- creazione ----------------------------------------------------------------------------

    @Test
    void notifyFollow_savesAnUnreadNotificationForTheFollowedUser() {
        notificationService.notifyFollow("Lunark", "Kaistlin");

        Notification n = saved();
        assertThat(n.getRecipient()).isEqualTo("Kaistlin");
        assertThat(n.getActor()).isEqualTo("Lunark");
        assertThat(n.getType()).isEqualTo(NotificationType.FOLLOW);
        assertThat(n.isRead()).isFalse();
        assertThat(n.getCreatedAt()).isNotNull();
        assertThat(n.getReviewId()).isNull();
    }

    @Test
    void notifyLike_goesToTheReviewAuthorAndCarriesAnExcerptOfTheReview() {
        notificationService.notifyLike("Lunark", review("Kaistlin", "Bellissimo"));

        Notification n = saved();
        assertThat(n.getRecipient()).isEqualTo("Kaistlin");
        assertThat(n.getActor()).isEqualTo("Lunark");
        assertThat(n.getType()).isEqualTo(NotificationType.LIKE_REVIEW);
        assertThat(n.getReviewId()).isEqualTo("r1");
        assertThat(n.getGameName()).isEqualTo("BARRIER X");
        assertThat(n.getExcerpt()).isEqualTo("Bellissimo");
    }

    @Test
    void notifyReply_keepsTheReplyIdAndExcerptsTheReplyText() {
        ReviewReply reply = new ReviewReply("p1", "r1", "Lunark", "Concordo!", Instant.now());

        notificationService.notifyReply("Lunark", review("Kaistlin", "Bellissimo"), reply);

        Notification n = saved();
        assertThat(n.getRecipient()).isEqualTo("Kaistlin");
        assertThat(n.getType()).isEqualTo(NotificationType.REPLY_REVIEW);
        assertThat(n.getReplyId()).isEqualTo("p1");
        assertThat(n.getExcerpt()).isEqualTo("Concordo!");
    }

    @Test
    void notify_longText_isTruncatedToTheExcerptLength() {
        notificationService.notifyLike("Lunark", review("Kaistlin", "x".repeat(500)));

        // il taglio aggiunge un'ellissi: 140 caratteri di testo + "…"
        assertThat(saved().getExcerpt())
                .hasSize(NotificationService.EXCERPT_LENGTH + 1)
                .endsWith("…");
    }

    @Test
    void notify_aboutOwnAction_isNotSaved() {
        notificationService.notifyFollow("Lunark", "Lunark");
        notificationService.notifyLike("Lunark", review("Lunark", "Mio"));

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void notify_repositoryThrows_doesNotPropagate() {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("boom"));

        // un guasto sulle notifiche non deve far fallire il follow che le ha generate
        notificationService.notifyFollow("Lunark", "Kaistlin");
    }

    // --- rimozione ----------------------------------------------------------------------------

    @Test
    void removeMethods_deleteTheMatchingNotifications() {
        notificationService.removeFollow("Lunark", "Kaistlin");
        notificationService.removeLike("Lunark", "r1");
        notificationService.removeReply("p1");
        notificationService.removeForReview("r1");

        verify(notificationRepository)
                .deleteByRecipientAndTypeAndActor("Kaistlin", NotificationType.FOLLOW, "Lunark");
        verify(notificationRepository)
                .deleteByTypeAndActorAndReviewId(NotificationType.LIKE_REVIEW, "Lunark", "r1");
        verify(notificationRepository).deleteByReplyId("p1");
        verify(notificationRepository).deleteByReviewId("r1");
    }

    // --- lettura ------------------------------------------------------------------------------

    @Test
    void getNotifications_followNotificationsSayWhetherTheRecipientFollowsBack() {
        Page<Notification> page =
                new PageImpl<>(
                        List.of(
                                notification("n1", NotificationType.FOLLOW, "Lunark"),
                                notification("n2", NotificationType.FOLLOW, "Mira"),
                                notification("n3", NotificationType.LIKE_REVIEW, "Lunark")),
                        pageable,
                        3);
        when(notificationRepository.findByRecipientOrderByCreatedAtDesc("Kaistlin", pageable))
                .thenReturn(page);
        when(userNeo4jRepository.findFollowedAmong(eq("Kaistlin"), anyList()))
                .thenReturn(List.of("Lunark"));

        List<NotificationDTO> result =
                notificationService.getNotifications("Kaistlin", pageable).getContent();

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFollowingBack()).isTrue();
        assertThat(result.get(1).getFollowingBack()).isFalse();
        // solo i follow hanno il campo: per un like non ha senso
        assertThat(result.get(2).getFollowingBack()).isNull();
        assertThat(result.get(2).getExcerpt()).isEqualTo("Bello");
    }

    @Test
    void getNotifications_withoutFollowNotifications_doesNotQueryNeo4j() {
        when(notificationRepository.findByRecipientOrderByCreatedAtDesc("Kaistlin", pageable))
                .thenReturn(
                        new PageImpl<>(
                                List.of(notification("n1", NotificationType.LIKE_REVIEW, "Lunark")),
                                pageable,
                                1));

        notificationService.getNotifications("Kaistlin", pageable);

        verify(userNeo4jRepository, never()).findFollowedAmong(anyString(), anyList());
    }

    @Test
    void getNotifications_repositoryThrows_returnsAnEmptyPage() {
        when(notificationRepository.findByRecipientOrderByCreatedAtDesc(anyString(), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThat(notificationService.getNotifications("Kaistlin", pageable).getContent())
                .isEmpty();
    }

    @Test
    void countUnread_returnsTheRepositoryCount_andZeroOnFailure() {
        when(notificationRepository.countByRecipientAndReadFalse("Kaistlin")).thenReturn(4L);
        assertThat(notificationService.countUnread("Kaistlin")).isEqualTo(4);

        when(notificationRepository.countByRecipientAndReadFalse("Kaistlin"))
                .thenThrow(new RuntimeException("boom"));
        assertThat(notificationService.countUnread("Kaistlin")).isZero();
    }

    // --- stato letto / eliminazione -----------------------------------------------------------

    @Test
    void markRead_matchesOnBothIdAndRecipient() {
        when(mongoTemplate.updateFirst(
                        any(Query.class), any(UpdateDefinition.class), eq(Notification.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(notificationService.markRead("Kaistlin", "n1")).isTrue();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate)
                .updateFirst(query.capture(), any(UpdateDefinition.class), eq(Notification.class));
        // senza il filtro sul destinatario si potrebbe segnare come letta la notifica di un altro
        assertThat(query.getValue().getQueryObject().toJson())
                .contains("\"_id\": \"n1\"")
                .contains("\"recipient\": \"Kaistlin\"");
    }

    @Test
    void markRead_notificationOfSomeoneElse_returnsFalse() {
        when(mongoTemplate.updateFirst(
                        any(Query.class), any(UpdateDefinition.class), eq(Notification.class)))
                .thenReturn(UpdateResult.acknowledged(0, 0L, null));

        assertThat(notificationService.markRead("Kaistlin", "n-di-un-altro")).isFalse();
    }

    @Test
    void markAllRead_updatesOnlyTheRecipientsUnreadNotifications() {
        notificationService.markAllRead("Kaistlin");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate)
                .updateMulti(query.capture(), any(UpdateDefinition.class), eq(Notification.class));
        assertThat(query.getValue().getQueryObject().toJson())
                .contains("\"recipient\": \"Kaistlin\"")
                .contains("\"read\": false");
    }

    @Test
    void delete_onlyRemovesTheRecipientsOwnNotification() {
        when(notificationRepository.deleteByIdAndRecipient("n1", "Kaistlin")).thenReturn(1L);
        when(notificationRepository.deleteByIdAndRecipient("n2", "Kaistlin")).thenReturn(0L);

        assertThat(notificationService.delete("Kaistlin", "n1")).isTrue();
        assertThat(notificationService.delete("Kaistlin", "n2")).isFalse();
    }
}
