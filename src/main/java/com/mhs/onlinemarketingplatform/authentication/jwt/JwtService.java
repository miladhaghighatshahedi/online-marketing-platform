/*
 * Copyright 2025-2026 the original author.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mhs.onlinemarketingplatform.authentication.jwt;

import com.mhs.onlinemarketingplatform.authentication.device.DeviceBindingService;
import com.mhs.onlinemarketingplatform.authentication.error.token.InvalidAccessTokenException;
import com.mhs.onlinemarketingplatform.authentication.error.token.InvalidRefreshTokenException;
import com.mhs.onlinemarketingplatform.authentication.error.token.TokenDecodingException;
import com.mhs.onlinemarketingplatform.authentication.error.token.TokenErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
/**
 * @author Milad Haghighat Shahedi
 */
@Component
public class JwtService {

	private final JwtTokenProperties properties;
	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final MessageSource messageSource;
	private final DeviceBindingService deviceService;

	public JwtService(
			JwtTokenProperties properties,
			JwtEncoder jwtEncoder,
			JwtDecoder jwtDecoder,
			MessageSource messageSource,
			DeviceBindingService deviceBindingService) {
		this.properties = properties;
		this.jwtEncoder = jwtEncoder;
		this.jwtDecoder = jwtDecoder;
		this.messageSource = messageSource;
		this.deviceService = deviceBindingService;
	}


	public String generateAccessToken(Authentication authentication, String deviceIdHash, String userAgentHash, String ipAddressHash,String jtiHash) {
		Instant now = Instant.now();
		Instant accessTokenExpiry = now.plus(this.properties.jwtAccessTokenExpiryInSec(), ChronoUnit.SECONDS);

		String scope = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.joining(" "));

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(this.properties.jwtIssuer())
				.subject(authentication.getName())
				.issuedAt(now)
				.notBefore(now)
				.expiresAt(accessTokenExpiry)
				.claim("scope", scope)
				.claim("jti", jtiHash)
				.claim("x-device-id", deviceIdHash)
				.claim("user-agent", userAgentHash)
				.claim("ip-address", ipAddressHash)
				.claim("type", this.properties.jwtAccessTokenClaimType())
				.build();

		return this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
	}

	public Jwt validateAccessToken(String token) {
		try {
			Jwt jwt = this.jwtDecoder.decode(token);
			validateAccessTokenType(jwt);
			validateAccessTokenIssuer(jwt);
			validateAccessTokenDeviceId(jwt);
			validateAccessTokenUserAgent(jwt);
			validateAccessTokenIpHash(jwt);
			validateAccessTokenJti(jwt);
			return jwt;
		} catch (JwtValidationException e) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.EXPIRED);
		} catch (BadJwtException e) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.exception",
					new Object[]{},
					LocaleContextHolder.getLocale()),TokenErrorCode.MALFORMED);
		} catch (Exception e) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.UNKNOWN);
		}
	}

	public String generateRefreshToken(Authentication authentication, String deviceIdHash, String userAgentHash, String ipAddressHash,String jtiHash) {

		Instant now = Instant.now();
		Instant refreshTokenExpiry = now.plus(this.properties.jwtRefreshTokenExpiryInSec(),ChronoUnit.SECONDS);

		String scope = authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.collect(Collectors.joining(" "));

		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(this.properties.jwtIssuer())
				.subject(authentication.getName())
				.issuedAt(now)
				.notBefore(now)
				.expiresAt(refreshTokenExpiry)
				.claim("scope", scope)
				.claim("jti", jtiHash)
				.claim("x-device-id", deviceIdHash)
				.claim("user-agent", userAgentHash)
				.claim("ip-address", ipAddressHash)
				.claim("type", this.properties.jwtRefreshTokenClaimType())
				.build();

		return this.jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
	}

	public Jwt validateRefreshToken(String token, String deviceId, String userAgent, String ipAddress) {
		try {
			Jwt jwt = this.jwtDecoder.decode(token);
			validateRefreshTokenType(jwt);
			validateRefreshTokenIssuer(jwt);
			validateRefreshTokenDeviceId(jwt);
			validateRefreshTokenUserAgent(jwt);
			validateRefreshTokenIpHash(jwt);
			validateRefreshTokenJti(jwt);
			return jwt;
		} catch (JwtValidationException e) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.EXPIRED);
		} catch (BadJwtException e) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.MALFORMED);
		} catch (Exception e) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.UNKNOWN);
		}
	}


	private void validateAccessTokenType(Jwt decodedJwt){
		if(!this.properties.jwtAccessTokenClaimType().equals(decodedJwt.getClaimAsString("type"))) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.type",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_TYPE);
		}
	}

	private void validateAccessTokenIssuer(Jwt decodedJwt) {
		String issuer = decodedJwt.getIssuer() != null ? decodedJwt.getIssuer().toString() : null;
		if(!this.properties.jwtIssuer().equals(issuer)) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.issuer",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_ISSUER);
		}
	}

	private void validateAccessTokenDeviceId(Jwt jwt) {
		String deviceId = jwt.getClaimAsString("x_device_id");
		if (this.deviceService.existsByDeviceIdHash(deviceId)) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.device.id",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_DEVICE_ID);
		}
	}

	private void validateAccessTokenUserAgent(Jwt jwt) {
		String uaHash = jwt.getClaimAsString("user_agent");
		if (!this.deviceService.existsByUserAgentHash(uaHash)) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.user.agent",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_USER_AGENT);
		}
	}

	private void validateAccessTokenIpHash(Jwt jwt) {
		String ipHash = jwt.getClaimAsString("ip_address");
		if (!this.deviceService.existsByIpHash(ipHash)) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.invalid.ip.hash",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_IP_HASH);
		}
	}

	private void validateAccessTokenJti(Jwt jwt) {
		String jti = jwt.getId();
		if (this.deviceService.isReplay(jti)) {
			throw new InvalidAccessTokenException(
					messageSource.getMessage("error.authentication.access.token.replay.detected",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.REPLAY_DETECTED);
		}
	}


	private void validateRefreshTokenType(Jwt decodedJwt){
		if(!this.properties.jwtRefreshTokenClaimType().equals(decodedJwt.getClaimAsString("type"))) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.type",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_TYPE);
		}
	}

	private void validateRefreshTokenIssuer(Jwt decodedJwt) {
		String issuer = decodedJwt.getIssuer() != null ? decodedJwt.getIssuer().toString() : null;
		if(!this.properties.jwtIssuer().equals(issuer)) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.issuer",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_ISSUER);
		}
	}

	private void validateRefreshTokenDeviceId(Jwt jwt) {
		String deviceId = jwt.getClaimAsString("x_device_id");
		if (this.deviceService.existsByDeviceIdHash(deviceId)) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.device.id",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_DEVICE_ID);
		}
	}

	private void validateRefreshTokenUserAgent(Jwt jwt) {
		String uaHash = jwt.getClaimAsString("user_agent");
		if (!this.deviceService.existsByUserAgentHash(uaHash)) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.user.agent",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_USER_AGENT);
		}
	}

	private void validateRefreshTokenIpHash(Jwt jwt) {
		String ipHash = jwt.getClaimAsString("ip_address");
		if (!this.deviceService.existsByIpHash(ipHash)) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.invalid.ip.hash",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.INVALID_IP_HASH);
		}
	}

	private void validateRefreshTokenJti(Jwt jwt) {
		String jti = jwt.getId();
		if (this.deviceService.isReplay(jti)) {
			throw new InvalidRefreshTokenException(
					messageSource.getMessage("error.authentication.refresh.token.replay.detected",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.REPLAY_DETECTED);
		}
	}


	public boolean isBearerToken(String header) {
		return header != null && header.startsWith("Bearer ");
	}

	public Jwt decode(String token) {
        Jwt jwt;
		try {
			jwt = jwtDecoder.decode(token);
		} catch (JwtValidationException e) {
			throw new TokenDecodingException(
					messageSource.getMessage("error.authentication.token.decoding.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.EXPIRED);
		} catch (BadJwtException e) {
			throw new TokenDecodingException(
					messageSource.getMessage("error.authentication.token.decoding.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.MALFORMED);
		} catch (Exception e) {
			throw new TokenDecodingException(
					messageSource.getMessage("error.authentication.token.decoding.exception",
							new Object[]{},
							LocaleContextHolder.getLocale()),TokenErrorCode.UNKNOWN);
		}
		return jwt;
	}

	public String extractSubject(String token) {
		return decode(token).getSubject();
	}

	public List<GrantedAuthority> extractAuthorities(String token) {
		String scope = decode(token).getClaimAsString("scope");

		return Arrays.stream(scope.split(" "))
				.filter(s -> !s.isBlank())
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());
	}

	public Authentication buildAuthenticationFromJwt(Jwt decodedJwt){
		String phoneNumber = decodedJwt.getSubject();
		String scope = decodedJwt.getClaimAsString("scope");

		List<GrantedAuthority> authorities = Arrays.stream(scope.split(" "))
				.filter(s -> !s.isBlank())
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());

		UserDetails principal = new User(phoneNumber, "", authorities);
		return new UsernamePasswordAuthenticationToken(principal, null, authorities);
	}

}
