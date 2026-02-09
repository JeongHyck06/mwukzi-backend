package jack.mwukzibackened.domain.room;

import jack.mwukzibackened.common.exception.UnauthorizedException;
import jack.mwukzibackened.common.security.AuthenticatedUser;
import jack.mwukzibackened.domain.room.dto.CreateRoomResponse;
import jack.mwukzibackened.domain.room.dto.JoinRoomRequest;
import jack.mwukzibackened.domain.room.dto.JoinRoomResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Room", description = "방 관련 API")
public class RoomController {

    private final RoomService roomService;

    /**
     * POST /api/v1/rooms
     * 방 생성 (인증 필요)
     */
    @PostMapping
    @Operation(summary = "방 생성", description = "로그인한 사용자가 방을 생성합니다.")
    public ResponseEntity<CreateRoomResponse> createRoom(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다");
        }
        CreateRoomResponse response = roomService.createRoom(principal.getUserId());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/rooms/join
     * 초대 코드로 방 참여 (인증 불필요)
     */
    @PostMapping("/join")
    @Operation(summary = "방 참여", description = "초대 코드로 방에 참여합니다. 인증이 필요 없습니다.")
    public ResponseEntity<JoinRoomResponse> joinRoom(
            @Valid @RequestBody JoinRoomRequest request
    ) {
        JoinRoomResponse response = roomService.joinRoom(
                request.getInviteCode(),
                request.getDisplayName()
        );
        return ResponseEntity.ok(response);
    }

}
