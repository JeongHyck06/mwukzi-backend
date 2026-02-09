package jack.mwukzibackened.domain.room;

import jack.mwukzibackened.domain.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "invite_code", unique = true, nullable = false, length = 6)
    private String inviteCode;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id", nullable = false)
    private User host;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RoomStatus status = RoomStatus.COLLECTING;
    
    @Column(name = "radius_meters", nullable = false)
    private Integer radiusMeters;
    
    @Column(name = "center_lat", nullable = false, precision = 10, scale = 8)
    private BigDecimal centerLat;
    
    @Column(name = "center_lng", nullable = false, precision = 11, scale = 8)
    private BigDecimal centerLng;
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Builder
    public Room(String inviteCode, User host, Integer radiusMeters, 
                BigDecimal centerLat, BigDecimal centerLng, LocalDateTime expiresAt) {
        this.inviteCode = inviteCode;
        this.host = host;
        this.radiusMeters = radiusMeters;
        this.centerLat = centerLat;
        this.centerLng = centerLng;
        this.expiresAt = expiresAt;
    }
    
    public void updateStatus(RoomStatus status) {
        this.status = status;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
