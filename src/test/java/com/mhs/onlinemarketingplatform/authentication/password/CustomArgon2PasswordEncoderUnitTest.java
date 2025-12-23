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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
/**
 * @author Milad Haghighat Shahedi
 */
@ExtendWith(MockitoExtension.class)
public class CustomArgon2PasswordEncoderUnitTest {

	private CustomArgon2PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		passwordEncoder = new CustomArgon2PasswordEncoder(
				new Argon2PasswordEncoderProperties(
						16, 32, 2, 1 << 16, 3
				)
		);
	}

	@Test
	void encode_method_shouldEncodeSuccessfully() {
		// Arrange
		String rawPassword = "password";
		// Act
		String result = this.passwordEncoder.encode(rawPassword);
		// Assert
		assertNotNull(result);
	}

	@Test
	void encode_method_shouldProduceDifferentHash() {
		String rawPassword = "password";
		// Act
		String hash1 = this.passwordEncoder.encode(rawPassword);
		String hash2 = this.passwordEncoder.encode(rawPassword);
		// Assert
		assertNotEquals(hash1,hash2);
	}

	@Test
	void matches_method_shouldMatch() {
		// Arrange
		String rawPassword = "password";
		// Act
		String result = this.passwordEncoder.encode(rawPassword);
		// Assert
		assertNotNull(result);
		assertTrue(this.passwordEncoder.matches(rawPassword,result));
	}

	@Test
	void matches_method_shouldNotMatch() {
		// Arrange
		String rawPassword = "password";
		// Act
		String result = this.passwordEncoder.encode(rawPassword);
		// Assert
		assertNotNull(result);
		assertFalse(this.passwordEncoder.matches("password2",result));
	}

}
