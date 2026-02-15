package jack.mwukzibackened.domain.place;

import jack.mwukzibackened.domain.participant.Participant;
import jack.mwukzibackened.domain.room.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "place_selections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceSelection {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @Column(name = "place_name", nullable = false)
    private String placeName;

    @Column(name = "provider_place_id")
    private String providerPlaceId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public PlaceSelection(
            Room room,
            Participant participant,
            String placeName,
            String providerPlaceId
    ) {
        this.room = room;
        this.participant = participant;
        this.placeName = placeName;
        this.providerPlaceId = providerPlaceId;
    }
}
