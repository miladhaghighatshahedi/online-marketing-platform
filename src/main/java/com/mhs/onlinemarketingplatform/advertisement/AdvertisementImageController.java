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
import com.mhs.onlinemarketingplatform.advertisement.error.image.InconsistentImageDataException;
import com.mhs.onlinemarketingplatform.advertisement.error.image.TotalNumberOfImagesExceedsException;
import com.mhs.onlinemarketingplatform.catalog.config.ImagePathProperties;
import com.mhs.onlinemarketingplatform.common.AuditLogger;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller("advertisementImageController")
@ResponseBody
class AdvertisementImageController {

	private final AdvertisementImageService advertisementImageService;

	public AdvertisementImageController(AdvertisementImageService advertisementImageService) {
		this.advertisementImageService = advertisementImageService;
	}

	@PostMapping(value = "/api/images/upload/single", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<String> uploadSingleImage(@RequestPart("metadata") AddImageRequest metadata, @RequestPart("image") MultipartFile image) {
		return ResponseEntity.ok(advertisementImageService.uploadSingleImage(metadata, image));
	}

	@PostMapping(value = "/api/images/upload/multiple",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<List<String>> uploadMultipleImages(@RequestPart("metadataList") List<AddImageRequest> metadataList,@RequestPart("images") List<MultipartFile> images) {
		return ResponseEntity.ok(this.advertisementImageService.uploadMultipleImages(metadataList,images));
	}

}

@Service
class AdvertisementImageService {

	private static final int MAX_IMAGES_PER_ADVERTISEMENT = 5;

	private static final Logger logger = LoggerFactory.getLogger(AdvertisementImageService.class);
	private final AuditLogger auditLogger;

	private final AdvertisementImageRepository advertisementImageRepository;
	private final AdvertisementService advertisementService;
	private final ImageService imageService;
	private final ImagePathProperties properties;
	private final ImageMapper imageMapper;
	private final MessageSource messageSource;

	public AdvertisementImageService(
			AuditLogger auditLogger,
			AdvertisementImageRepository advertisementImageRepository,
			AdvertisementService advertisementService,
			ImageService imageService,
			ImagePathProperties properties,
			ImageMapper imageMapper,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.advertisementImageRepository = advertisementImageRepository;
		this.imageService = imageService;
		this.advertisementService = advertisementService;
		this.properties = properties;
		this.imageMapper = imageMapper;
		this.messageSource = messageSource;
	}

	String uploadSingleImage(AddImageRequest addImageRequest,MultipartFile image) {
		UUID advertisementId = addImageRequest.advertisementId();
		logger.info("Add new image to advertisement with the ID: {}",advertisementId);

		if(!this.advertisementService.existsById(advertisementId)){
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{addImageRequest.advertisementId()},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);}

		Set<AdvertisementImage> retreivedImages = this.advertisementImageRepository.findByAdvertisementId(advertisementId);
		int noOfRetrievedImage = retreivedImages == null ? 0 : retreivedImages.size();
		if(noOfRetrievedImage + 1 >= MAX_IMAGES_PER_ADVERTISEMENT) {
			throw new TotalNumberOfImagesExceedsException(
					messageSource.getMessage("error.image.total.number.of.imges.exceeded",
							new Object[]{},
							LocaleContextHolder.getLocale()),
					ImageErrorCode.TOTAL_NUMBER_OF_IMAGES_EXCEEDS);}

		Path imagePath = this.imageService.createMainDirectory(advertisementId,properties.getAdvertisementImagePath());
		String imageUrl = this.imageService.prepareImageUploadWithoutDirectory(advertisementId, image, imagePath);

		AdvertisementImage mappedImage = this.imageMapper.mapAddImageRequestToAdvertisementImage(addImageRequest,imageUrl);
		AdvertisementImage storedImage = this.advertisementImageRepository.save(mappedImage);

		this.auditLogger.log("ADVERTISEMENT_IMAGE_CREATED", "ADVERTISEMENT_IMAGE", "AdvertisementiMAGE ID: " + storedImage.id());
        return imageUrl;
	}

	List<String> uploadMultipleImages(List<AddImageRequest> requests, List<MultipartFile> images) {
		UUID advertisementId = requests.get(0).advertisementId();
		logger.info("Uploading new images for advertisement with the ID: {} request-size: {} image-size: {}",advertisementId,requests.size(),images.size());

		if (images == null || images.isEmpty() || requests == null || requests.isEmpty()) {return Collections.emptyList();}

		if(requests.size() != images.size()) {
			throw new InconsistentImageDataException(
					messageSource.getMessage("error.image.inconsistent.image.data",
							new Object[]{},
							LocaleContextHolder.getLocale()),
					ImageErrorCode.INCONSISTENT_IMAGE_METADATA);}

		if(!this.advertisementService.existsById(advertisementId)) {
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{advertisementId},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);
		}

		Set<AdvertisementImage> retrievedImages = this.advertisementImageRepository.findByAdvertisementId(advertisementId);

		int noOfRetrievedImages = retrievedImages == null ? 0 : retrievedImages.size();

		if(noOfRetrievedImages + images.size() > MAX_IMAGES_PER_ADVERTISEMENT) {
			throw new TotalNumberOfImagesExceedsException(
					messageSource.getMessage("error.image.total.number.of.imges.exceeded",
							new Object[]{},
							LocaleContextHolder.getLocale()),
					ImageErrorCode.TOTAL_NUMBER_OF_IMAGES_EXCEEDS);}

		List<AddImageRequest> normalizedRequestsWithOneMain = normalizeRequestWithOneMainImage(requests);
		List<AdvertisementImage> normalizedAddImageRequestWithId = normalizedRequestsWithOneMain.stream().map(imageMapper::mapAddImageRequestToAdvertisementImage).toList();

		List<String> storedImageUrls = new ArrayList<>(images.size());
		List<AdvertisementImage> imagesToSave = new ArrayList<>(images.size());
		List<Path> tempImagePath = new ArrayList<>(images.size());

		Path imagePath = imageService.createMainDirectory(advertisementId,properties.getAdvertisementImagePath());

		try {
			for (int i = 0; i < images.size(); i++) {
				MultipartFile imageFile = images.get(i);
				AdvertisementImage advertisementImage = normalizedAddImageRequestWithId.get(i);

				String imageUrl = this.imageService.prepareImageUploadWithoutDirectory(advertisementImage.id(), imageFile, imagePath);
				storedImageUrls.add(imageUrl);

				Path writtenPath = imageService.resolveFilePath(advertisementId, imageUrl);
				if (writtenPath != null) {
					tempImagePath.add(writtenPath);
				}

				AdvertisementImage mappedImage = imageMapper.mapAdvertisementImageWithUrl(advertisementImage, imageUrl);
				imagesToSave.add(mappedImage);
			}

			this.advertisementImageRepository.saveAll(imagesToSave);

			this.auditLogger.log("ADVERTISEMENT_IMAGES_ADDED", "ADVERTISEMENT", "Advertisement ID: " + advertisementId);

			return storedImageUrls ;

		} catch (Exception e) {
			logger.error("Failed to upload images for advertisement {}. Cleaning up written files.", advertisementId, e);
			this.imageService.removedInconsistentImages(tempImagePath);
			throw new RuntimeException("Failed to upload advertisement images", e);
		}
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

		if(!this.advertisementService.existsById(advertisementId)){
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{advertisementId},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);
		}

		return this.advertisementImageRepository.findByAdvertisementId(advertisementId);
	}

	AdvertisementImage findMainImageWithAdvertisementId(UUID advertisementId) {
		logger.info("Looking up main advertisement image by ADVERTISEMENT_ID: {}",advertisementId);

		if(!this.advertisementService.existsById(advertisementId)){
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{advertisementId},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);
		}

		return this.advertisementImageRepository.findMainImageWithAdvertisementId(advertisementId).orElseThrow(() ->
					new ImageNotFoundException(
							messageSource.getMessage("error.image.main.image.with.not.found",
									new Object[]{},
									LocaleContextHolder.getLocale()),
							ImageErrorCode.IMAGE_NOT_FOUND));
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

	private  List<AddImageRequest> normalizeRequestWithOneMainImage(List<AddImageRequest> requests) {
		if (requests == null || requests.isEmpty()) return requests;
		List<AddImageRequest> normalized = new ArrayList<>(requests.size());
		boolean mainFound = false;

		for (AddImageRequest request : requests) {
			if (Boolean.TRUE.equals(request.isMain())) {
				if (! mainFound) {
					normalized.add(request);
					mainFound = true;
				} else {
					normalized.add(this.imageMapper.mapAddImageRequestToFalse(request));
				}
			} else {
				normalized.add(request);
			}
		}

		if (!mainFound && !normalized.isEmpty()) {
			AddImageRequest first = normalized.get(0);
			normalized.set(0, this.imageMapper.mapAddImageRequestToTrue(first));
		}

		return normalized;
	}

}

@Repository
interface AdvertisementImageRepository extends CrudRepository<AdvertisementImage,UUID> {

	Optional<AdvertisementImage> findById(UUID id);

	@Query("SELECT * FROM advertisement_images WHERE advertisement_id= :advertisementId")
	Set<AdvertisementImage> findByAdvertisementId(@Param("advertisementId") UUID advertisementId);

	@Query("SELECT * FROM advertisement_images WHERE advertisement_id= :advertisementId AND is_main = TRUE")
	Optional<AdvertisementImage> findMainImageWithAdvertisementId(@Param("advertisementId") UUID advertisementId);

}

@Table("advertisement_images")
record AdvertisementImage (
		@Id
		UUID id,
		@Version
		Integer version,
		String url,
		boolean isMain,
		LocalDateTime insertedAt,
		UUID advertisementId
) {}

record AddImageRequest(
		Boolean isMain,
		UUID advertisementId
)  {}

record ImageResponse(
		String url,
		Boolean isMain,
		LocalDateTime insertedAt
){}

@Mapper(componentModel = "spring",nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, imports = {LocalDateTime.class,UuidCreator.class})
interface ImageMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "url", ignore = true)
	@Mapping(target = "insertedAt", expression = "java(LocalDateTime.now())")
	AdvertisementImage mapAddImageRequestToAdvertisementImage(AddImageRequest addImageRequest);

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "url", source = "url")
	@Mapping(target = "insertedAt", expression = "java(LocalDateTime.now())")
	AdvertisementImage mapAddImageRequestToAdvertisementImage(AddImageRequest addImageRequest,String url);

	@Mapping(target = "id", source = "advertisementImage.id")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "url", source = "url")
	@Mapping(target = "insertedAt", expression = "java(LocalDateTime.now())")
	AdvertisementImage mapAdvertisementImageWithUrl(AdvertisementImage advertisementImage,String url);

	ImageResponse mapAdvertisementImageToImageResponse(AdvertisementImage advertisementImage);

	@Mapping(target = "isMain", expression = "java(Boolean.FALSE)")
	AddImageRequest mapAddImageRequestToFalse(AddImageRequest addImageRequest);

	@Mapping(target = "isMain", expression = "java(Boolean.TRUE)")
	AddImageRequest mapAddImageRequestToTrue(AddImageRequest addImageRequest);

}
