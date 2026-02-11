package jack.mwukzibackened.domain.room;

import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.exception.NotFoundException;
import jack.mwukzibackened.common.exception.UnauthorizedException;
import jack.mwukzibackened.domain.participant.Participant;
import jack.mwukzibackened.domain.participant.ParticipantRepository;
import jack.mwukzibackened.domain.participant.ParticipantRole;
import jack.mwukzibackened.domain.room.dto.CreateRoomResponse;
import jack.mwukzibackened.domain.room.dto.CreateRoomRequest;
import jack.mwukzibackened.domain.room.dto.JoinRoomResponse;
import jack.mwukzibackened.domain.room.dto.ParticipantPreferenceResponse;
import jack.mwukzibackened.domain.room.dto.RoomParticipantResponse;
import jack.mwukzibackened.domain.user.User;
import jack.mwukzibackened.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RoomSseService roomSseService;

    private static final int INVITE_CODE_LENGTH = 6;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int DEFAULT_RADIUS_METERS = 1500;
    private static final int DEFAULT_EXPIRES_HOURS = 6;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public CreateRoomResponse createRoom(UUID userId, CreateRoomRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        if (request == null
                || request.getCenterLat() == null
                || request.getCenterLng() == null) {
            throw new BadRequestException("방장 위치 권한을 허용한 뒤 방을 다시 만들어 주세요");
        }

        Integer radiusMeters = request != null && request.getRadiusMeters() != null
                ? request.getRadiusMeters()
                : DEFAULT_RADIUS_METERS;
        BigDecimal centerLat = BigDecimal.valueOf(request.getCenterLat());
        BigDecimal centerLng = BigDecimal.valueOf(request.getCenterLng());

        Room room = Room.builder()
                .inviteCode(generateUniqueInviteCode())
                .host(user)
                .radiusMeters(radiusMeters)
                .centerLat(centerLat)
                .centerLng(centerLng)
                .expiresAt(LocalDateTime.now().plusHours(DEFAULT_EXPIRES_HOURS))
                .build();
        Room savedRoom = roomRepository.save(room);

        return CreateRoomResponse.builder()
                .roomId(savedRoom.getId())
                .inviteCode(savedRoom.getInviteCode())
                .roomStatus(savedRoom.getStatus())
                .build();
    }

    @Transactional
    public JoinRoomResponse joinRoom(String inviteCode, String displayName) {
        String normalizedCode = inviteCode.trim().toUpperCase();
        String normalizedName = displayName.trim();

        Room room = roomRepository.findByInviteCode(normalizedCode)
                .orElseThrow(() -> new NotFoundException("초대 코드를 찾을 수 없습니다"));

        if (room.isExpired() || room.getStatus() == RoomStatus.EXPIRED) {
            room.updateStatus(RoomStatus.EXPIRED);
            roomRepository.save(room);
            throw new BadRequestException("만료된 방입니다");
        }

        if (participantRepository.existsByRoomIdAndDisplayName(room.getId(), normalizedName)) {
            throw new BadRequestException("이미 사용 중인 이름입니다");
        }

        Participant participant = Participant.builder()
                .room(room)
                .user(null)
                .displayName(normalizedName)
                .role(ParticipantRole.GUEST)
                .build();
        Participant saved = participantRepository.save(participant);

        participantRepository.findByRoomIdAndUserId(room.getId(), room.getHost().getId())
                .orElseGet(() -> participantRepository.save(Participant.builder()
                        .room(room)
                        .user(room.getHost())
                        .displayName(room.getHost().getNickname())
                        .role(ParticipantRole.HOST)
                        .build()));

        JoinRoomResponse response = JoinRoomResponse.builder()
                .roomId(room.getId())
                .inviteCode(room.getInviteCode())
                .participantId(saved.getId())
                .displayName(saved.getDisplayName())
                .roomStatus(room.getStatus())
                .build();
        broadcastParticipants(room.getInviteCode());
        return response;
    }

    @Transactional
    public List<RoomParticipantResponse> getParticipants(UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));

        participantRepository.findByRoomIdAndUserId(room.getId(), room.getHost().getId())
                .orElseGet(() -> participantRepository.save(Participant.builder()
                        .room(room)
                        .user(room.getHost())
                        .displayName(room.getHost().getNickname())
                        .role(ParticipantRole.HOST)
                        .build()));

        return participantRepository.findByRoomId(room.getId()).stream()
                .map(participant -> RoomParticipantResponse.builder()
                        .participantId(participant.getId())
                        .displayName(participant.getDisplayName())
                        .role(participant.getRole())
                        .hasSubmitted(Boolean.TRUE.equals(participant.getHasSubmitted()))
                        .build())
                .toList();
    }

    @Transactional
    public List<RoomParticipantResponse> getParticipantsByInviteCode(String inviteCode) {
        String normalized = inviteCode.trim().toUpperCase();
        Room room = roomRepository.findByInviteCode(normalized)
                .orElseThrow(() -> new NotFoundException("초대 코드를 찾을 수 없습니다"));

        participantRepository.findByRoomIdAndUserId(room.getId(), room.getHost().getId())
                .orElseGet(() -> participantRepository.save(Participant.builder()
                        .room(room)
                        .user(room.getHost())
                        .displayName(room.getHost().getNickname())
                        .role(ParticipantRole.HOST)
                        .build()));

        return participantRepository.findByRoomId(room.getId()).stream()
                .map(participant -> RoomParticipantResponse.builder()
                        .participantId(participant.getId())
                        .displayName(participant.getDisplayName())
                        .role(participant.getRole())
                        .hasSubmitted(Boolean.TRUE.equals(participant.getHasSubmitted()))
                        .build())
                .toList();
    }

    @Transactional
    public RoomParticipantResponse ensureHostParticipant(UUID userId, UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));

        if (!room.getHost().getId().equals(userId)) {
            throw new UnauthorizedException("방장만 참여할 수 있습니다");
        }

        Participant participant = participantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseGet(() -> participantRepository.save(Participant.builder()
                        .room(room)
                        .user(room.getHost())
                        .displayName(room.getHost().getNickname())
                        .role(ParticipantRole.HOST)
                        .build()));

        RoomParticipantResponse response = RoomParticipantResponse.builder()
                .participantId(participant.getId())
                .displayName(participant.getDisplayName())
                .role(participant.getRole())
                .hasSubmitted(Boolean.TRUE.equals(participant.getHasSubmitted()))
                .build();
        broadcastParticipants(room.getInviteCode());
        return response;
    }

    @Transactional
    public RoomParticipantResponse submitPreference(
            UUID roomId,
            UUID userId,
            UUID participantId,
            List<String> chips,
            String freeText
    ) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));

        Participant participant;
        if (userId != null) {
            if (!room.getHost().getId().equals(userId)) {
                throw new UnauthorizedException("방장만 인증 기반 제출이 가능합니다");
            }
            participant = participantRepository.findByRoomIdAndUserId(roomId, userId)
                    .orElseGet(() -> participantRepository.save(Participant.builder()
                            .room(room)
                            .user(room.getHost())
                            .displayName(room.getHost().getNickname())
                            .role(ParticipantRole.HOST)
                            .build()));
        } else {
            if (participantId == null) {
                throw new BadRequestException("participant_id가 필요합니다");
            }
            participant = participantRepository.findById(participantId)
                    .orElseThrow(() -> new NotFoundException("참여자를 찾을 수 없습니다"));
            if (!participant.getRoom().getId().equals(roomId)) {
                throw new BadRequestException("방 정보가 올바르지 않습니다");
            }
        }

        participant.submitPreference(buildPreferenceText(chips, freeText));
        participant.updateLastSeen();

        RoomParticipantResponse response = RoomParticipantResponse.builder()
                .participantId(participant.getId())
                .displayName(participant.getDisplayName())
                .role(participant.getRole())
                .hasSubmitted(Boolean.TRUE.equals(participant.getHasSubmitted()))
                .build();
        broadcastParticipants(room.getInviteCode());
        return response;
    }

    @Transactional
    public ParticipantPreferenceResponse getParticipantPreference(
            UUID roomId,
            UUID participantId
    ) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));

        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NotFoundException("참여자를 찾을 수 없습니다"));
        if (!participant.getRoom().getId().equals(room.getId())) {
            throw new BadRequestException("방 정보가 올바르지 않습니다");
        }

        return ParticipantPreferenceResponse.builder()
                .participantId(participant.getId())
                .displayName(participant.getDisplayName())
                .hasSubmitted(Boolean.TRUE.equals(participant.getHasSubmitted()))
                .preferenceText(participant.getPreferenceText() == null ? "" : participant.getPreferenceText())
                .build();
    }

    @Transactional
    public void leaveRoomAsHost(UUID userId, UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("방을 찾을 수 없습니다"));

        if (!room.getHost().getId().equals(userId)) {
            throw new UnauthorizedException("방장만 방을 삭제할 수 있습니다");
        }

        participantRepository.deleteByRoomId(roomId);
        roomRepository.delete(room);
        roomSseService.closeRoom(room.getInviteCode());
    }

    @Transactional
    public void leaveRoomAsGuest(UUID participantId) {
        Participant participant = participantRepository.findById(participantId)
                .orElse(null);
        if (participant == null) {
            // 방장이 먼저 나가며 방/참여자가 정리된 경우 게스트 나가기를 멱등 처리
            return;
        }

        if (participant.getRole() == ParticipantRole.HOST) {
            throw new BadRequestException("방장은 이 방법으로 나갈 수 없습니다");
        }

        String inviteCode = participant.getRoom().getInviteCode();
        participantRepository.delete(participant);
        broadcastParticipants(inviteCode);
    }

    private void broadcastParticipants(String inviteCode) {
        try {
            List<RoomParticipantResponse> participants = getParticipantsByInviteCode(inviteCode);
            roomSseService.sendParticipants(inviteCode, participants);
        } catch (Exception ex) {
            log.debug("SSE 참여자 갱신 실패: inviteCode={}", inviteCode);
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            code = generateInviteCode();
        } while (roomRepository.existsByInviteCode(code));
        return code;
    }

    private String generateInviteCode() {
        StringBuilder builder = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            int index = random.nextInt(INVITE_CODE_CHARS.length());
            builder.append(INVITE_CODE_CHARS.charAt(index));
        }
        return builder.toString();
    }

    private String buildPreferenceText(List<String> chips, String freeText) {
        List<String> normalizedChips = chips == null
                ? Collections.emptyList()
                : chips.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        String normalizedFreeText = freeText == null ? "" : freeText.trim();

        String chipPart = normalizedChips.isEmpty() ? "없음" : String.join(", ", normalizedChips);
        String freeTextPart = normalizedFreeText.isEmpty() ? "없음" : normalizedFreeText;

        return "[취향 입력 요약]\n- 선택 태그: " + chipPart + "\n- 자유 입력: " + freeTextPart;
    }
}
