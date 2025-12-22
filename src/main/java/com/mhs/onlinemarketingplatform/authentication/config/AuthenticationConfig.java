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
package com.mhs.onlinemarketingplatform.authentication.config;

import com.mhs.onlinemarketingplatform.authentication.password.Argon2PasswordEncoderProperties;
import com.mhs.onlinemarketingplatform.authentication.props.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
/**
 * @author Milad Haghighat Shahedi
 */
@Configuration
@EnableConfigurationProperties({
		ApplicationProperties.class,
		OtpCoreProperties.class,
		OtpRateLimitProperties.class,
		OtpRedisProperties.class,
		Argon2PasswordEncoderProperties.class})
public class AuthenticationConfig {

	@Bean
	public SecureRandom secureRandom() {
		return new SecureRandom();
	}

}
