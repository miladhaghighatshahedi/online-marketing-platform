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
package com.mhs.onlinemarketingplatform.catalog.error;

/**
 * @author Milad Haghighat Shahedi
 */

public class CatalogNotFoundException extends RuntimeException {

	private final CatalogErrorCode code;

	public CatalogNotFoundException(String message, CatalogErrorCode code) {
		super(message);
		this.code = code;
	}

	public CatalogErrorCode getCode() {
		return code;
	}

}
