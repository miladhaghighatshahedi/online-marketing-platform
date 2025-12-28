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
import com.mhs.onlinemarketingplatform.authentication.error.hash.HashMechanismInvalidDataException;
import io.micrometer.common.util.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
/**
 * @author Milad Haghighat Shahedi
 */
public interface HashUtility {

	String sha256Base64(String data);

	boolean match(String rawData, String hashedData);

	List<String> preservedOrderHashing(List<String> inputs);

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
			throw new HashMechanismInvalidDataException(
					messageSource.getMessage("error.hash.mechanism.data.invalid",
							new Object[] {},
							Locale.getDefault()),
					HashErrorCode.HASH_INVALID_DATA);
		}

		try {
			MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
			byte[] digest = messageDigest.digest(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
		} catch (Exception e) {
			throw new HashMechanismInvalidDataException(
					messageSource.getMessage("error.hash.mechanism.unknown.error"+e,
							new Object[] {},
							Locale.getDefault()),
					HashErrorCode.HASH_UNKNOWN_ERROR);}
	}

	@Override
	public boolean match(String rawData, String hashedData) {
		if (rawData == null || hashedData == null) return false;
		if (rawData.isBlank()|| hashedData.isBlank()) return false;

		byte[] digestA = sha256Base64(rawData).getBytes(StandardCharsets.UTF_8);
		byte[] digestB = hashedData.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(digestA,digestB);
	}

	@Override
	public List<String> preservedOrderHashing(List<String> inputs) {
		List<String> hashList = new ArrayList<>(inputs.size());
		for (String input : inputs) {
			hashList.add(sha256Base64(input));
		}
		return hashList;
	}

}
