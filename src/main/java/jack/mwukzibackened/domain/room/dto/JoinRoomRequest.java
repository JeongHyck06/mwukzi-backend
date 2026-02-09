package jack.mwukzibackened.domain.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinRoomRequest {

    @NotBlank(message = "초대 코드를 입력해 주세요")
    @Size(min = 6, max = 6, message = "초대 코드는 6자리여야 합니다")
    @Pattern(regexp = "^[A-Za-z0-9]{6}$", message = "초대 코드는 영문/숫자 조합이어야 합니다")
    private String inviteCode;

    @NotBlank(message = "이름을 입력해 주세요")
    @Size(max = 20, message = "이름은 20자 이하로 입력해 주세요")
    private String displayName;
}
