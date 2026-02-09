package jack.mwukzibackened.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserMeResponse {
    private UUID userId;
    private String provider;
    private String nickname;
    private String email;
}
