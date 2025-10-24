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
package com.mhs.onlinemarketingplatform.advertisement.error;

import com.mhs.onlinemarketingplatform.advertisement.error.advertisement.AdvertisementAlreadyActivatedException;
import com.mhs.onlinemarketingplatform.advertisement.error.advertisement.AdvertisementAlreadyDeactivatedException;
import com.mhs.onlinemarketingplatform.advertisement.error.advertisement.AdvertisementAlreadyExistsException;
import com.mhs.onlinemarketingplatform.advertisement.error.advertisement.AdvertisementNotFoundException;
import com.mhs.onlinemarketingplatform.advertisement.error.category.CategoryNotFoundException;
import com.mhs.onlinemarketingplatform.advertisement.error.image.ImageNotFoundException;
import com.mhs.onlinemarketingplatform.advertisement.error.image.TotalNumberOfImagesExceedsException;
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
public class AdvertisementExceptionHandler {

	private final ErrorLogger errorLogger;

	public AdvertisementExceptionHandler(ErrorLogger errorLogger) {
		this.errorLogger = errorLogger;
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return Map.of("error", "Bad Request", "message",
				"Invalid path variable '" + ex.getName() + "': " + ex.getValue());
	}

	@ExceptionHandler(AdvertisementNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleAdvertisementNotFound(AdvertisementNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"ADVERTISEMENT_NOT_FOUND"
		);
		errorLogger.logError("ADVERTISEMENT","ADVERTISEMENT_NOT_FOUND","Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(AdvertisementAlreadyExistsException.class)
	public ResponseEntity<ApiErrorMessage> handleAdvertisementAlreadyExists(AdvertisementAlreadyExistsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"ADVERTISEMENT_ALREADY_EXISTS"
		);
		errorLogger.logError("ADVERTISEMENT","ADVERTISEMENT_ALREADY_EXISTS", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(AdvertisementAlreadyActivatedException.class)
	public ResponseEntity<ApiErrorMessage> handleAdvertisementAlreadyActivatedException(AdvertisementAlreadyActivatedException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"ADVERTISEMENT_ALREADY_ACTIVATED"
		);
		errorLogger.logError("ADVERTISEMENT","ADVERTISEMENT_ALREADY_ACTIVATED","Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(AdvertisementAlreadyDeactivatedException.class)
	public ResponseEntity<ApiErrorMessage> handleAdvertisementAlreadyDeactivatedException(AdvertisementAlreadyDeactivatedException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.BAD_REQUEST.value(),
				ex.getMessage(),
				"ADVERTISEMENT_ALREADY_DEACTIVATED"
		);
		errorLogger.logError("ADVERTISEMENT","ADVERTISEMENT_ALREADY_DEACTIVATED","Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

	@ExceptionHandler(CategoryNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleCategoryNotFoundException(CategoryNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"CATEGORY_NOT_FOUND"
		);
		errorLogger.logError("CATEGORY_NOT_FOUND", "CATEGORY", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(ImageNotFoundException.class)
	public ResponseEntity<ApiErrorMessage> handleImageNotFoundException(ImageNotFoundException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"IMAGE_NOT_FOUND"
		);
		errorLogger.logError("IMAGE_NOT_FOUND", "IMAGE", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
	}

	@ExceptionHandler(TotalNumberOfImagesExceedsException.class)
	public ResponseEntity<ApiErrorMessage> handleTotalNumberOfImagesExceedsException(TotalNumberOfImagesExceedsException ex) {
		ApiErrorMessage error = new ApiErrorMessage(
				HttpStatus.NOT_FOUND.value(),
				ex.getMessage(),
				"NOF_IMAGES_EXCEEDED"
		);
		errorLogger.logError("NOF_IMAGES_EXCEEDED", "IMAGE", "Error: " + ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
	}

}
