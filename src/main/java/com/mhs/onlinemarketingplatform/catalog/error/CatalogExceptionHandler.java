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

import com.mhs.onlinemarketingplatform.common.ErrorLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * @author Milad Haghighat Shahedi
 */

@RestControllerAdvice
public class CatalogExceptionHandler {

	private final ErrorLogger errorLogger;

	public CatalogExceptionHandler(ErrorLogger errorLogger) {
		this.errorLogger = errorLogger;
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return Map.of("error", "Bad Request", "message",
				"Invalid path variable '" + ex.getName() + "': " + ex.getValue());
	}

	@ExceptionHandler(CatalogNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleCatalogNotFound(CatalogNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"CATALOG_NOT_FOUND"
		);
		errorLogger.logError("CATALOG", "CATALOG_NOT_FOUND", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(CatalogAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handleCatalogAlreadyExists(CatalogAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"CATALOG_ALREADY_EXISTS"
		);
		errorLogger.logError("CATALOG", "CATALOG_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

}
