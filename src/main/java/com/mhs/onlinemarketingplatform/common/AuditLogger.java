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
package com.mhs.onlinemarketingplatform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @author Milad Haghighat Shahedi
 */

@Component
public class AuditLogger {

	private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT_LOGGER");

	public void log(String action, String resource, String details) {
		AUDIT_LOG.info("Action = {} | Resource = {} | Details = {}", action, resource, details);
	}

}
