package jack.mwukzibackened.domain.room;

import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.security.AuthenticatedUser;
import jack.mwukzibackened.domain.room.dto.LeaveRoomRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Room", description = "방 관련 API")
public class RoomLeaveController {

    private final RoomService roomService;

    @PostMapping("/rooms/leave")
    @Operation(summary = "방 나가기", description = "참여자는 방을 나가고 방장은 방을 삭제합니다.")
    public ResponseEntity<Void> leaveRoom(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestBody LeaveRoomRequest request
    ) {
        if (principal != null) {
            if (request.getRoomId() == null) {
                throw new BadRequestException("room_id가 필요합니다");
            }
            roomService.leaveRoomAsHost(principal.getUserId(), request.getRoomId());
            return ResponseEntity.noContent().build();
        }

        if (request.getParticipantId() == null) {
            throw new BadRequestException("participant_id가 필요합니다");
        }
        roomService.leaveRoomAsGuest(request.getParticipantId());
        return ResponseEntity.noContent().build();
    }
}
