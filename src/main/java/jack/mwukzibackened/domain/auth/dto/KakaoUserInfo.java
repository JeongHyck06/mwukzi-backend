package jack.mwukzibackened.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class KakaoUserInfo {
    
    private Long id;
    
    @JsonProperty("kakao_account")
    private KakaoAccount kakaoAccount;
    
    @Getter
    @NoArgsConstructor
    public static class KakaoAccount {
        private Profile profile;
        private String email;
        
        @Getter
        @NoArgsConstructor
        public static class Profile {
            private String nickname;
        }
    }
    
    public String getNickname() {
        return kakaoAccount != null && kakaoAccount.profile != null 
            ? kakaoAccount.profile.nickname 
            : "사용자";
    }
    
    public String getEmail() {
        return kakaoAccount != null ? kakaoAccount.email : null;
    }
}
