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
package com.mhs.onlinemarketingplatform.authentication;

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.authentication.util.HashUtility;
import com.mhs.onlinemarketingplatform.authentication.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
/**
 * @author Milad Haghighat Shahedi
 */
@Service
public class RefreshTokenService  {

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserService userService;
	private final RefreshtokenMapper mapper;
	private final HashUtility hashUtility;

	RefreshTokenService(
			RefreshTokenRepository refreshTokenRepository,
			UserService userService,
			RefreshtokenMapper mapper,
			HashUtility hashUtility) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userService = userService;
		this.mapper = mapper;
		this.hashUtility = hashUtility;
	}

	@Transactional
	public void saveRefreshToken(String phoneNumber, String rawRefreshToken, String hashedDeviceId) {
		User returnedUser = userService.findByPhoneNumber(phoneNumber);

		String hashedToken = hashUtility.sha256Base64(rawRefreshToken);

		refreshTokenRepository.deleteByUserIdAndDeviceId(returnedUser.id(), hashedDeviceId);

		RefreshToken mappedRefreshToken = this.mapper.mapToRefreshToken(returnedUser.id(), hashedToken, hashedDeviceId);

		this.refreshTokenRepository.save(mappedRefreshToken);
	}

	@Transactional
	public void revokeToken(String refreshToken) {
		refreshTokenRepository.findByHashedToken(refreshToken).ifPresent(refreshTokenRepository::delete);
	}

}

@Repository
interface RefreshTokenRepository extends CrudRepository<RefreshToken,Long> {

	Optional<RefreshToken> findByHashedToken(String token);

	@Query("DELETE FROM auth_refresh_token WHERE user_id= :userId AND device_id_hash= :deviceIdHash")
	void deleteByUserIdAndDeviceId(@Param("userId") UUID userId,@Param("deviceIdHash") String deviceIdHash);

}

@Table("refresh_token")
record RefreshToken(
		@Id UUID id,
		@Version Integer version,
		String hashedToken,
		String deviceIdHash,
		LocalDateTime expiresAt,
		UUID userId) {}

@Mapper(componentModel = "spring", imports = {UuidCreator.class, LocalDateTime.class})
interface RefreshtokenMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "hashedToken", source = "hashedToken")
	@Mapping(target = "deviceIdHash", source = "deviceIdHash")
	@Mapping(target = "expiresAt", expression = "java(LocalDateTime.now())")
	@Mapping(target = "userId", source = "userId")
	RefreshToken mapToRefreshToken(UUID userId,String hashedToken,String deviceIdHash);

}






