package jack.mwukzibackened.domain.participant;

import jack.mwukzibackened.domain.room.Room;
import jack.mwukzibackened.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participant {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // host는 user 참조, guest는 null
    
    @Column(name = "display_name", nullable = false)
    private String displayName;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantRole role;
    
    @Column(name = "has_submitted", nullable = false)
    private Boolean hasSubmitted = false;

    @Column(name = "preference_text", columnDefinition = "TEXT")
    private String preferenceText;
    
    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;
    
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
    
    @Builder
    public Participant(Room room, User user, String displayName, ParticipantRole role) {
        this.room = room;
        this.user = user;
        this.displayName = displayName;
        this.role = role;
    }
    
    public void submitPreference(String preferenceText) {
        this.hasSubmitted = true;
        this.preferenceText = preferenceText;
    }
    
    public void updateLastSeen() {
        this.lastSeenAt = LocalDateTime.now();
    }
}
