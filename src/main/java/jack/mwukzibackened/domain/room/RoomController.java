package jack.mwukzibackened.domain.room;

import jack.mwukzibackened.common.exception.UnauthorizedException;
import jack.mwukzibackened.common.security.AuthenticatedUser;
import jack.mwukzibackened.domain.room.dto.CreateRoomRequest;
import jack.mwukzibackened.domain.room.dto.CreateRoomResponse;
import jack.mwukzibackened.domain.room.dto.JoinRoomRequest;
import jack.mwukzibackened.domain.room.dto.JoinRoomResponse;
import jack.mwukzibackened.domain.room.dto.ParticipantPreferenceResponse;
import jack.mwukzibackened.domain.room.dto.RoomParticipantResponse;
import jack.mwukzibackened.domain.room.dto.SubmitPreferenceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "Room", description = "방 관련 API")
public class RoomController {

    private final RoomService roomService;
    private final RoomSseService roomSseService;

    /**
     * POST /api/v1/rooms
     * 방 생성 (인증 필요)
     */
    @PostMapping
    @Operation(summary = "방 생성", description = "로그인한 사용자가 방을 생성합니다.")
    public ResponseEntity<CreateRoomResponse> createRoom(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody(required = false) CreateRoomRequest request
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다");
        }
        CreateRoomResponse response = roomService.createRoom(principal.getUserId(), request);
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

    /**
     * GET /api/v1/rooms/{roomId}/participants
     * 방 참여자 목록 조회 (인증 불필요)
     */
    @GetMapping("/{roomId}/participants")
    @Operation(summary = "참여자 조회", description = "방 참여자 목록을 조회합니다. 인증이 필요 없습니다.")
    public ResponseEntity<List<RoomParticipantResponse>> getParticipants(
            @PathVariable java.util.UUID roomId
    ) {
        List<RoomParticipantResponse> response = roomService.getParticipants(roomId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/rooms/participants?inviteCode=XXXXXX
     * 초대 코드로 참여자 목록 조회 (인증 불필요)
     */
    @GetMapping("/participants")
    @Operation(summary = "참여자 조회(초대코드)", description = "초대 코드로 방 참여자 목록을 조회합니다.")
    public ResponseEntity<List<RoomParticipantResponse>> getParticipantsByInviteCode(
            @RequestParam String inviteCode
    ) {
        List<RoomParticipantResponse> response = roomService.getParticipantsByInviteCode(inviteCode);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/rooms/participants/stream?inviteCode=XXXXXX
     * 참여자 목록 SSE 스트림
     */
    @GetMapping(value = "/participants/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "참여자 SSE", description = "참여자 목록 변경을 SSE로 전달합니다.")
    public SseEmitter streamParticipants(
            @RequestParam String inviteCode
    ) {
        SseEmitter emitter = roomSseService.subscribe(inviteCode);
        List<RoomParticipantResponse> participants = roomService.getParticipantsByInviteCode(inviteCode);
        roomSseService.sendParticipantsToEmitter(inviteCode, emitter, participants);
        return emitter;
    }

    /**
     * POST /api/v1/rooms/{roomId}/participants/host
     * 방장 참여 등록 (인증 필요)
     */
    @PostMapping("/{roomId}/participants/host")
    @Operation(summary = "방장 참여", description = "방장이 방에 참여자 정보로 등록됩니다.")
    public ResponseEntity<RoomParticipantResponse> joinAsHost(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable java.util.UUID roomId
    ) {
        if (principal == null) {
            throw new UnauthorizedException("인증이 필요합니다");
        }
        RoomParticipantResponse response = roomService.ensureHostParticipant(
                principal.getUserId(),
                roomId
        );
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/rooms/{roomId}/preferences/submit
     * 취향 제출 완료 처리 (SSE 브로드캐스트)
     */
    @PostMapping("/{roomId}/preferences/submit")
    @Operation(summary = "취향 제출 완료", description = "참여자의 취향 입력 완료 상태를 저장하고 SSE로 전파합니다.")
    public ResponseEntity<RoomParticipantResponse> submitPreference(
            @PathVariable java.util.UUID roomId,
            @RequestBody(required = false) SubmitPreferenceRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        java.util.UUID participantId = request == null ? null : request.getParticipantId();
        java.util.List<String> chips = request == null ? java.util.List.of() : request.getChips();
        String freeText = request == null ? "" : request.getFreeText();
        RoomParticipantResponse response = roomService.submitPreference(
                roomId,
                principal == null ? null : principal.getUserId(),
                participantId,
                chips,
                freeText
        );
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/rooms/{roomId}/preferences/{participantId}
     * 참여자 취향 상세 조회
     */
    @GetMapping("/{roomId}/preferences/{participantId}")
    @Operation(summary = "참여자 취향 상세 조회", description = "참여자의 취향 텍스트를 조회합니다.")
    public ResponseEntity<ParticipantPreferenceResponse> getParticipantPreference(
            @PathVariable java.util.UUID roomId,
            @PathVariable java.util.UUID participantId
    ) {
        ParticipantPreferenceResponse response = roomService.getParticipantPreference(roomId, participantId);
        return ResponseEntity.ok(response);
    }

}
