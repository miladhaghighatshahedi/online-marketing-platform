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
package com.mhs.onlinemarketingplatform.authentication.password;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
/**
 * @author Milad Haghighat Shahedi
 */
@Component
public class CustomArgon2PasswordEncoder implements PasswordEncoder {

	private final Argon2PasswordEncoder passwordEncoder;

	CustomArgon2PasswordEncoder(Argon2PasswordEncoderProperties properties) {
		this.passwordEncoder = new Argon2PasswordEncoder(
				properties.saltLength(),
				properties.hashLength(),
				properties.parallelism(),
				properties.memory(),
				properties.iteration());
	}

	@Override
	public String encode(CharSequence rawPassword) {
		return this.passwordEncoder.encode(rawPassword);
	}

	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		if (rawPassword == null || rawPassword.isEmpty()) return false;
		if (encodedPassword == null || encodedPassword.isBlank()) return false;

		return this.passwordEncoder.matches(rawPassword,encodedPassword);
	}

}
