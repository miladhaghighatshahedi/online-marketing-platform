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
package com.mhs.onlinemarketingplatform.advertisement;

import com.github.f4b6a3.uuid.UuidCreator;
import com.mhs.onlinemarketingplatform.advertisement.error.advertisement.AdvertisementErrorCode;
import com.mhs.onlinemarketingplatform.advertisement.error.advertisement.AdvertisementNotFoundException;
import com.mhs.onlinemarketingplatform.advertisement.error.image.ImageErrorCode;
import com.mhs.onlinemarketingplatform.advertisement.error.image.ImageNotFoundException;
import com.mhs.onlinemarketingplatform.advertisement.error.image.TotalNumberOfImagesExceedsException;
import com.mhs.onlinemarketingplatform.common.AuditLogger;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * @author Milad Haghighat Shahedi
 */
class AdvertisementImageController {

}

@Service
class AdvertisementImageService {

	private static final int MAX_IMAGES_PER_ADVERTISEMENT = 5;

	private static final Logger logger = LoggerFactory.getLogger(AdvertisementService.class);
	private final AuditLogger auditLogger;

	private final AdvertisementImageRepository advertisementImageRepository;
	private final AdvertisementService advertisementService;
	private final ImageMapper imageMapper;
	private final MessageSource messageSource;

	public AdvertisementImageService(
			AuditLogger auditLogger,
			AdvertisementImageRepository advertisementImageRepository,
			AdvertisementService advertisementService,
			ImageMapper imageMapper,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.advertisementImageRepository = advertisementImageRepository;
		this.advertisementService = advertisementService;
		this.imageMapper = imageMapper;
		this.messageSource = messageSource;
	}

	ImageResponse add(AddImageRequest addImageRequest) {
		logger.info("Add new image to advertisement with the ID: {}",addImageRequest.advertisementId());

		if(!this.advertisementService.existsBYId(addImageRequest.advertisementId())){
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{addImageRequest.advertisementId()},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);
		}

		Set<AdvertisementImage> retreivedImages = this.advertisementImageRepository.findByAdvertisementId(addImageRequest.advertisementId());
		if(retreivedImages.size() >= MAX_IMAGES_PER_ADVERTISEMENT) {
			throw new TotalNumberOfImagesExceedsException(
					messageSource.getMessage("error.image.total.number.of.imges.exceeded",
							new Object[]{},
							LocaleContextHolder.getLocale()),
					ImageErrorCode.TOTAL_NUMBER_OF_IMAGES_EXCEEDS);
		}

		AdvertisementImage mappedImage = this.imageMapper.mapAddImageRequestToAdvertisementImage(addImageRequest);
		AdvertisementImage storedImage = this.advertisementImageRepository.save(mappedImage);
		this.auditLogger.log("ADVERTISEMENT_IMAGE_CREATED", "ADVERTISEMENT_IMAGE", "AdvertisementiMAGE ID: " + storedImage.id());
        return this.imageMapper.mapAdvertisementImageToImageResponse(storedImage);
	}

	ImageResponse findById(UUID id) {
		logger.info("Looking up advertisement image by ID: {}",id);
		AdvertisementImage retrievedImage = this.advertisementImageRepository.findById(id).orElseThrow(() ->
				new ImageNotFoundException(
						messageSource.getMessage("error.image.image.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						ImageErrorCode.IMAGE_NOT_FOUND));

		return this.imageMapper.mapAdvertisementImageToImageResponse(retrievedImage);
	}

	Set<AdvertisementImage> findByAdvertisementId(UUID advertisementId) {
		logger.info("Looking up advertisement images by ADVERTISEMENT_ID: {}",advertisementId);
		return this.advertisementImageRepository.findByAdvertisementId(advertisementId);
	}

	void delete(UUID id) {
		AdvertisementImage retrievedImage = this.advertisementImageRepository.findById(id).orElseThrow(() ->
				new ImageNotFoundException(
						messageSource.getMessage("error.image.image.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						ImageErrorCode.IMAGE_NOT_FOUND));
		logger.info("Deleting exisiting advertisement image with id: {}",retrievedImage.id());
		this.advertisementImageRepository.delete(retrievedImage);
		this.auditLogger.log("ADVERTISEMENT_IMAGE_DELETED", "ADVERTISEMENT_IMAGE", "AdvertisementImage Id: " + retrievedImage.id());
	}

}

@Repository
interface AdvertisementImageRepository extends CrudRepository<AdvertisementImage,UUID> {

	Optional<AdvertisementImage> findById(UUID id);

	@Query("SELECT * FROM advertisement_images WHERE advertisement_id= :advertisementId")
	Set<AdvertisementImage> findByAdvertisementId(@Param("advertisementId") UUID advertisementId);

}

@Table("advertisement_images")
record AdvertisementImage(
		UUID id,
		String url,
		Boolean isMain,
		LocalDateTime insertedAt,
		UUID advertisementId
) {}

record AddImageRequest(
		String url,
		Boolean isMain,
		LocalDateTime insertedAt,
		UUID advertisementId
) {}

record ImageResponse(
		String url,
		Boolean isMain,
		LocalDateTime insertedAt
){}

@Mapper(componentModel = "spring",nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, imports = {LocalDateTime.class,UuidCreator.class})
interface ImageMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "insertedAt", expression = "java(LocalDateTime.now())")
	AdvertisementImage mapAddImageRequestToAdvertisementImage(AddImageRequest addImageRequest);

	ImageResponse mapAdvertisementImageToImageResponse(AdvertisementImage advertisementImage);
}
