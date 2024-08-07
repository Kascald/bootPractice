package com.onboarding.preonboarding.SecureLogin;


import com.onboarding.preonboarding.entity.RefreshToken;
import com.onboarding.preonboarding.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JWTTokenProvider {

	private SecretKey mySecretKey;
	private final RefreshTokenRepository refreshTokenRepository;

	//	@Value("${accessTokenExpiration}")
	private long ACCESS_EXPIRATION_PERIOD;   //10 min => millseeconds
	//	@Value("${refreshTokenExpiration}")
	private long REFRESH_EXPIRATION_PERIOD;   //1 hour => millseeconds


	public JWTTokenProvider(RefreshTokenRepository refreshTokenRepository, @Value("${myJwtRandomKeyHashed}")String jwtSecretKey,
	                        @Value("${accessTokenExpiration}")long accessExpiration, @Value("${refreshTokenExpiration}")long refreshExpriation) {
		this.refreshTokenRepository = refreshTokenRepository;
		mySecretKey = new SecretKeySpec(jwtSecretKey.getBytes(StandardCharsets.UTF_8), Jwts.SIG.HS256.key().build().getAlgorithm());
		ACCESS_EXPIRATION_PERIOD = accessExpiration;
		REFRESH_EXPIRATION_PERIOD = refreshExpriation;
	}

	public String generateToken(String username
			, List<String> userRole, Long expriation) {
		ZoneId zoneId = ZoneId.of("Asia/Seoul");
		Instant now = ZonedDateTime.now(zoneId).toInstant();
		Date issuedAt = Date.from(now);
		Date expiration = Date.from(now.plusMillis(expriation));
		Map<String, Object> claims = new HashMap<>();
		claims.put("userRole", userRole);

		return Jwts.builder()
				.subject(username)
				.claims(claims)
				.signWith(mySecretKey)
				.issuedAt(issuedAt)
				.expiration(expiration)
				.encodePayload(true)
				.compact();
	}

	public String getUsernameFromToken(String token) {
		return Jwts.parser().verifyWith(mySecretKey).build().parseSignedClaims(token).getPayload().get("sub", String.class);
	}

	public List<String> getRoleList(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(mySecretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();

		List<?> rawList = claims.get("userRole", List.class);
		List<String> roleList = null;

		if (rawList != null) {
			roleList = rawList.stream()
					.filter(item -> item instanceof String)
					.map(item -> (String) item)
					.collect(Collectors.toList());
		}

		return roleList;
	}

	public String createAccessToken(String username,  List<String> userRole) {
		return generateToken(username, userRole, ACCESS_EXPIRATION_PERIOD);
	}

	public String createRefreshToken(String username, List<String> userRole) {
		return generateToken(username,  userRole, REFRESH_EXPIRATION_PERIOD);
	}

	public void saveRefreshToken(String refreshToken) {
		dbSaveRT(refreshToken);
	}

	private void dbSaveRT(String refreshToken) {
		refreshTokenRepository.save(convertToRefreshTokenEntity(refreshToken));
	}

	private RefreshToken convertToRefreshTokenEntity(String refreshToken) {
		Claims refreshClaims = parsePayloadFromToken(refreshToken);
		RefreshToken tokenEntity = new RefreshToken();

		tokenEntity.setRefreshToken(refreshToken);
		tokenEntity.setSubject(refreshClaims.getSubject());
		tokenEntity.setExpiration(refreshClaims.getExpiration());

		return tokenEntity;
	}

	public Claims parsePayloadFromToken(String token) throws JwtException {
		return Jwts.parser()
				.verifyWith(mySecretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}
	public String getUserEmailFromToken(String token) throws JwtException {
		return parsePayloadFromToken(token).getSubject();
	}


	public boolean validateToken(String token) {
		try {
			parsePayloadFromToken(token);
			return true;
		} catch (JwtException e) {
			return false;
		}
	}

	public boolean isTokenExpiration(String token) {
		return Jwts.parser().verifyWith(mySecretKey).build().parseSignedClaims(token).getPayload().getExpiration().before(new Date());
	}

	public void setAuthorizationHeaderForAccessToken(HttpServletResponse response, String accessToken) {
		response.setHeader("Authorization", "Bearer "+ accessToken);
	}

	public void setAuthorizationHeaderForRefreshToken(HttpServletResponse response, String refreshToken) {
		response.setHeader("Refresh-Token", "Bearer "+ refreshToken);
	}

	public boolean isExistsRefreshToken(String refreshToken) {
		return Jwts.parser().verifyWith(mySecretKey).build().parseSignedClaims(refreshToken).getPayload().getExpiration().before(new Date());
	}

	public boolean isRefreshTokenValid(String token) {
		if (!validateToken(token)) {
			return false;
		}

		Optional<RefreshToken> refreshToken = refreshTokenRepository.findByRefreshToken(token);
		return refreshToken.isPresent() && refreshToken.get().getExpiration().after(new Date());
	}

	public void deleteRefreshToken(String token) {
		refreshTokenRepository.deleteByRefreshToken(token);
	}
}
