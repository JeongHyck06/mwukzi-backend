package jack.mwukzibackened.domain.room;

import jack.mwukzibackened.common.exception.BadRequestException;
import jack.mwukzibackened.common.exception.NotFoundException;
import jack.mwukzibackened.common.exception.UnauthorizedException;
import jack.mwukzibackened.domain.participant.Participant;
import jack.mwukzibackened.domain.participant.ParticipantRepository;
import jack.mwukzibackened.domain.participant.ParticipantRole;
import jack.mwukzibackened.domain.room.dto.CreateRoomResponse;
import jack.mwukzibackened.domain.room.dto.JoinRoomResponse;
import jack.mwukzibackened.domain.user.User;
import jack.mwukzibackened.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

    private static final int INVITE_CODE_LENGTH = 6;
    private static final String INVITE_CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int DEFAULT_RADIUS_METERS = 1000;
    private static final BigDecimal DEFAULT_CENTER_LAT = BigDecimal.ZERO;
    private static final BigDecimal DEFAULT_CENTER_LNG = BigDecimal.ZERO;
    private static final int DEFAULT_EXPIRES_HOURS = 6;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public CreateRoomResponse createRoom(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        Room room = Room.builder()
                .inviteCode(generateUniqueInviteCode())
                .host(user)
                .radiusMeters(DEFAULT_RADIUS_METERS)
                .centerLat(DEFAULT_CENTER_LAT)
                .centerLng(DEFAULT_CENTER_LNG)
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

        return JoinRoomResponse.builder()
                .roomId(room.getId())
                .inviteCode(room.getInviteCode())
                .participantId(saved.getId())
                .displayName(saved.getDisplayName())
                .roomStatus(room.getStatus())
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
    }

    @Transactional
    public void leaveRoomAsGuest(UUID participantId) {
        Participant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new NotFoundException("참여자를 찾을 수 없습니다"));

        if (participant.getRole() == ParticipantRole.HOST) {
            throw new BadRequestException("방장은 이 방법으로 나갈 수 없습니다");
        }

        participantRepository.delete(participant);
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
}
