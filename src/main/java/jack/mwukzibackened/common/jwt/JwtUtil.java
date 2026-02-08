package jack.mwukzibackened.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {
    
    private final SecretKey secretKey;
    private final long jwtExpiration;
    private final long roomTokenExpiration;
    
    public JwtUtil(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration}") long jwtExpiration,
        @Value("${jwt.room-token-expiration}") long roomTokenExpiration
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpiration = jwtExpiration;
        this.roomTokenExpiration = roomTokenExpiration;
    }
    
    // 방장용 JWT 생성
    public String generateUserToken(UUID userId, String provider) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("provider", provider);
        claims.put("role", "user");
        
        return Jwts.builder()
                .subject(userId.toString())
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(secretKey)
                .compact();
    }
    
    // 방 전용 토큰 생성
    public String generateRoomToken(UUID roomId, UUID participantId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roomId", roomId.toString());
        claims.put("participantId", participantId.toString());
        claims.put("role", role);
        
        return Jwts.builder()
                .subject("room-access")
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + roomTokenExpiration))
                .signWith(secretKey)
                .compact();
    }
    
    // 토큰 검증 및 Claims 추출
    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    // userId 추출 (방장용 JWT)
    public UUID extractUserId(String token) {
        Claims claims = validateAndGetClaims(token);
        return UUID.fromString(claims.getSubject());
    }
    
    // roomId 추출 (roomToken)
    public UUID extractRoomId(String token) {
        Claims claims = validateAndGetClaims(token);
        return UUID.fromString((String) claims.get("roomId"));
    }
    
    // participantId 추출 (roomToken)
    public UUID extractParticipantId(String token) {
        Claims claims = validateAndGetClaims(token);
        return UUID.fromString((String) claims.get("participantId"));
    }
    
    // role 추출
    public String extractRole(String token) {
        Claims claims = validateAndGetClaims(token);
        return (String) claims.get("role");
    }
}
