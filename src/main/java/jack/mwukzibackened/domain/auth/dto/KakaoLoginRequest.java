package jack.mwukzibackened.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoLoginRequest {
    
    @NotBlank(message = "카카오 Access Token이 필요합니다")
    private String kakaoAccessToken;
}
