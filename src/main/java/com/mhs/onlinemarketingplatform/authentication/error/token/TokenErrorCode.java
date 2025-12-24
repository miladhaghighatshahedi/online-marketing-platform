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
package com.mhs.onlinemarketingplatform.authentication.error.token;

/**
 * @author Milad Haghighat Shahedi
 */
public enum TokenErrorCode {
	EXPIRED,
	MALFORMED,
	INVALID_SIGNATURE,
	INVALID_TYPE,
	INVALID_ISSUER,
	INVALID_DEVICE_ID,
	INVALID_USER_AGENT,
	INVALID_IP_HASH,
	REPLAY_DETECTED,
	USER_DISABLED,
	DEVICE_MISMATCH,
	REVOKED,
	UNKNOWN
}
