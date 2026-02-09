package jack.mwukzibackened.domain.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(nullable = false)
    private String provider; // "kakao"
    
    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId; // 카카오 ID
    
    @Column(nullable = false)
    private String nickname;
    
    private String email;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Builder
    public User(String provider, String providerUserId, String nickname, String email) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.nickname = nickname;
        this.email = email;
    }
    
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
