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
package com.mhs.onlinemarketingplatform.authentication.util;

import com.mhs.onlinemarketingplatform.authentication.error.hash.HashErrorCode;
import com.mhs.onlinemarketingplatform.authentication.error.hash.HashMechanismInavlidDataException;
import io.micrometer.common.util.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;
/**
 * @author Milad Haghighat Shahedi
 */
public interface HashUtility {

	String sha256Base64(String data);

	boolean verfiyToken(String rawData,String hashedData);

}

@Component
class SHA256TokenHash implements HashUtility {

	private final MessageSource messageSource;

	SHA256TokenHash(MessageSource messageSource) {
		this.messageSource = messageSource;
	}

	@Override
	public String sha256Base64(String data) {
		if (data == null || StringUtils.isBlank(data)) {
			throw new HashMechanismInavlidDataException(
					messageSource.getMessage("error.hash.mechanism.data.invalid",
							new Object[] {},
							Locale.getDefault()),
					HashErrorCode.HASHING_INVALID_DATA);
		}

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			byte[] digest = messageDigest.digest(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (Exception e) {
			throw new HashMechanismInavlidDataException(
					messageSource.getMessage("error.hash.mechanism.unknown.error"+e,
							new Object[] {},
							Locale.getDefault()),
					HashErrorCode.HASHING_UKNOWN_ERROR);}
	}

	@Override
	public boolean verfiyToken(String rawData,String hashedData) {
		return sha256Base64(rawData).equals(hashedData);
	}

}
