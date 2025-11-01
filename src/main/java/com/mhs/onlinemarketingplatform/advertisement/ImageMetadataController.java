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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller("advertisementImageController")
@ResponseBody
class AdvertisementImageController {

	private final ImageMetadataService imageMetadataService;

	public AdvertisementImageController(ImageMetadataService imageMetadataService) {
		this.imageMetadataService = imageMetadataService;
	}

	@PostMapping(value = "/api/images/upload/multiple",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<List<ImageMetadataResponse>> uploadMultipleImages(
			@RequestPart("metadataList") List<AddImageMetadataRequest> metadataList,
			@RequestPart("images") List<MultipartFile> images) {
		return ResponseEntity.ok(this.imageMetadataService.saveImageMetadata(metadataList,images));
	}

}

@Service
class ImageMetadataService {

	private static final int MAX_IMAGES_PER_ADVERTISEMENT = 5;

	private static final Logger logger = LoggerFactory.getLogger(ImageMetadataService.class);
	private final AuditLogger auditLogger;

	private final ImageMetadataRepository imageMetadataRepository;
	private final AdvertisementService advertisementService;
	private final ImageUploadService imageUploadService;
	private final ImagePathProperties imagePathProperties;
	private final ImageMetadataMapper imageMetadataMapper;
	private final MessageSource messageSource;

	public ImageMetadataService(
			AuditLogger auditLogger,
			ImageMetadataRepository imageMetadataRepository,
			AdvertisementService advertisementService,
			ImageUploadService imageUploadService,
			ImagePathProperties imagePathProperties,
			ImageMetadataMapper imageMetadataMapper,
			MessageSource messageSource) {
		this.auditLogger = auditLogger;
		this.imageMetadataRepository = imageMetadataRepository;
		this.advertisementService = advertisementService;
		this.imageUploadService = imageUploadService;
		this.imagePathProperties = imagePathProperties;
		this.imageMetadataMapper = imageMetadataMapper;
		this.messageSource = messageSource;
	}

	List<ImageMetadataResponse> saveImageMetadata(List<AddImageMetadataRequest> requests, List<MultipartFile> files) {
		UUID advertisementId = requests.get(0).advertisementId();
		logger.info("Uploading new images for advertisement with the ID: {} request-size: {} image-size: {}", advertisementId, requests.size(), files.size());

		if (files == null || files.isEmpty() || requests == null || requests.isEmpty()) {
			return Collections.emptyList();
		}

		if (requests.size() != files.size()) {
			throw new InconsistentImageDataException(messageSource.getMessage("error.image.inconsistent.image.data", new Object[]{}, LocaleContextHolder.getLocale()), ImageErrorCode.INCONSISTENT_IMAGE_METADATA);
		}

		if (! this.advertisementService.existsById(advertisementId)) {
			throw new AdvertisementNotFoundException(messageSource.getMessage("error.advertisement.advertisement.with.id.not.found", new Object[]{advertisementId}, LocaleContextHolder.getLocale()), AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);
		}

		Set<ImageMetadata> retrievedImageMetataDataList = this.imageMetadataRepository.findByAdvertisementId(advertisementId);
		int noOfRetrievedImages = retrievedImageMetataDataList == null ? 0 : retrievedImageMetataDataList.size();
		if (noOfRetrievedImages + files.size() > MAX_IMAGES_PER_ADVERTISEMENT) {
			throw new TotalNumberOfImagesExceedsException(messageSource.getMessage("error.image.total.number.of.imges.exceeded", new Object[]{}, LocaleContextHolder.getLocale()), ImageErrorCode.TOTAL_NUMBER_OF_IMAGES_EXCEEDS);
		}

		Path imageBasePath = this.imageUploadService.createMainDirectoryIfNotExists(advertisementId,imagePathProperties.getAdvertisementImagePath());

		List<ImageMetadata> savedMetadata;

		if (retrievedImageMetataDataList.stream().noneMatch(ImageMetadata::isMain)) {
			savedMetadata = saveWithOneIsSetToMain(requests, files);
			this.auditLogger.log("no match","x","x");
		}
		else {
			savedMetadata = saveWithoutMain(requests, files);
			this.auditLogger.log("one match","x","x");
		}

		savedMetadata = saveWithOneIsSetToMain(requests, files);

		for(ImageMetadata imageMetadata:savedMetadata) {
			this.auditLogger.log("one match","x","x"+imageMetadata.id() + "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" + imageMetadata.url());
		}

	    storeImagesIntoFileSystemAsynch(savedMetadata,files,imageBasePath);


		return this.imageMetadataMapper.mapRequestListToResponse(savedMetadata);
	}

	List<ImageMetadata> saveWithOneIsSetToMain(List<AddImageMetadataRequest> requests,List<MultipartFile> files) {
		List<AddImageMetadataRequest> normalizedRequestsWithOneMain = normalizeRequestWithOneMainImage(requests);
		List<ImageMetadata> savedMetadata = new ArrayList<>(requests.size());

		for (int i = 0; i < files.size(); i++) {
			AddImageMetadataRequest addImageMetadataRequest = normalizedRequestsWithOneMain.get(i);
			ImageMetadata mappedMetaData = this.imageMetadataMapper.mapAddImageMetadataRequestToImageMetadata(addImageMetadataRequest);
			savedMetadata.add(this.imageMetadataRepository.save(mappedMetaData));
		}

		return savedMetadata;
	}

	@Async("imageTaskExecutor")
	void storeImagesIntoFileSystemAsynch(List<ImageMetadata> savedMetadat, List<MultipartFile> files, Path imageBasePath){
		for(int i=0 ; i < savedMetadat.size() ; i++) {
			ImageMetadata imageMetadata =  savedMetadat.get(i);
			MultipartFile file = files.get(i);
			try {
				String imageUrl = this.imageUploadService.prepareImageUpload(imageMetadata.id(), file, imageBasePath);
				logger.info("Image with url: {} ", imageUrl);
				ImageMetadata mappedMetadata = this.imageMetadataMapper.mapImageMetadataWithUrlAndStatusAndSuccessed(imageMetadata, imageUrl);
				ImageMetadata savedMetadata = this.imageMetadataRepository.save(mappedMetadata);
				this.auditLogger.log("IMAGE_WITH_METADATA_STORED", "IMAGE_METADATA", "Image ID: "+savedMetadata.id());
			} catch (Exception e) {
				logger.error("Failed to upload images with the id: {} ",imageMetadata,e);
				throw new RuntimeException("Failed to upload advertisement images",e);
			}
		}
	}

	List<ImageMetadata> saveWithoutMain(List<AddImageMetadataRequest> requests,List<MultipartFile> files) {
		List<ImageMetadata> savedMetadata = new ArrayList<>(requests.size());

		for (int i = 0; i < files.size(); i++) {
			AddImageMetadataRequest addImageMetadataRequest = requests.get(i);
			ImageMetadata mappedMetaData = this.imageMetadataMapper.mapAddImageMetadataRequestToImageMetadata(addImageMetadataRequest);
			savedMetadata.add(this.imageMetadataRepository.save(mappedMetaData));
		}

		return savedMetadata;
	}

	ImageMetadataResponse findById(UUID id) {
		logger.info("Looking up advertisement image by ID: {}",id);
		ImageMetadata retrievedImageMetadata = this.imageMetadataRepository.findById(id).orElseThrow(() ->
				new ImageNotFoundException(
						messageSource.getMessage("error.image.image.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						ImageErrorCode.IMAGE_NOT_FOUND));

		return this.imageMetadataMapper.mapRequestToResponse(retrievedImageMetadata);
	}

	Set<ImageMetadata> findByAdvertisementId(UUID advertisementId) {
		logger.info("Looking up advertisement images by ADVERTISEMENT_ID: {}",advertisementId);

		if(!this.advertisementService.existsById(advertisementId)){
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{advertisementId},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);
		}

		return this.imageMetadataRepository.findByAdvertisementId(advertisementId);
	}

	ImageMetadata findMainImageWithAdvertisementId(UUID advertisementId) {
		logger.info("Looking up main advertisement image by ADVERTISEMENT_ID: {}",advertisementId);

		if(!this.advertisementService.existsById(advertisementId)){
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{advertisementId},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);
		}

		return this.imageMetadataRepository.findMainImageWithAdvertisementId(advertisementId).orElseThrow(() ->
					new ImageNotFoundException(
							messageSource.getMessage("error.image.main.image.with.not.found",
									new Object[]{},
									LocaleContextHolder.getLocale()),
							ImageErrorCode.IMAGE_NOT_FOUND));
	}

	void delete(UUID id) {
		ImageMetadata retrievedImage = this.imageMetadataRepository.findById(id).orElseThrow(() ->
				new ImageNotFoundException(
						messageSource.getMessage("error.image.image.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						ImageErrorCode.IMAGE_NOT_FOUND));
		logger.info("Deleting exisiting advertisement image with id: {}",retrievedImage.id());
		this.imageMetadataRepository.delete(retrievedImage);
		this.auditLogger.log("ADVERTISEMENT_IMAGE_DELETED", "ADVERTISEMENT_IMAGE", "AdvertisementImage Id: " + retrievedImage.id());
	}

	private  List<AddImageMetadataRequest> normalizeRequestWithOneMainImage(List<AddImageMetadataRequest> requests) {
		if (requests == null || requests.isEmpty()) return requests;
		List<AddImageMetadataRequest> normalized = new ArrayList<>(requests.size());
		boolean mainFound = false;

		for (AddImageMetadataRequest request : requests) {
			if (Boolean.TRUE.equals(request.isMain())) {
				if (!mainFound) {
					normalized.add(request);
					mainFound = true;
				} else {
					normalized.add(this.imageMetadataMapper.mapAddImageMetadataRequestToFalse(request));
				}
			} else {
				normalized.add(request);
			}
		}

		if (!mainFound && !normalized.isEmpty()) {
			AddImageMetadataRequest first = normalized.get(0);
			normalized.set(0, this.imageMetadataMapper.mapAddImageMetadataRequestToTrue(first));
		}

		return normalized;
	}

}

@Service
class ImageUploadService {
	private static final Logger logger = LoggerFactory.getLogger(ImageUploadService.class);

	private final ImagePathProperties properties;

	public ImageUploadService(ImagePathProperties properties) {
		this.properties = properties;
	}

	Path createMainDirectoryIfNotExists(UUID id, String path) {
		logger.info("Main directory with the id: {} and path: {} created",id,path);
		try {
			Path direcetory = Paths.get(path, id.toString()).toAbsolutePath().normalize();
			if(! Files.exists(direcetory)) {
				Files.createDirectories(direcetory);
			}
			return direcetory;
		} catch (Exception e) {
			throw new RuntimeException("Exception in creating a main directory to store image");
		}
	}

	String prepareImageUpload(UUID imageId, MultipartFile image, Path directory) {
		logger.info("Stroing an image into the directory with id: {} and path: {} ",imageId,directory);
		String imageName  =  imageId.toString() + "." + imageExtension(image.getOriginalFilename());
		try {
			Path imagePath = directory.resolve(imageName);
			Files.copy(image.getInputStream(),imagePath,REPLACE_EXISTING);
			return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/advertisements/images/" + imageName).toUriString();
		} catch (Exception e) {
			throw new RuntimeException("Exception in storing an image into the direcotry");
		}
	}

	Path resolveFilePath(UUID adId, String imageUrl) {
		try {
			String imageName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
			Path folder = Paths.get(properties.getAdvertisementImagePath(), adId.toString()).toAbsolutePath().normalize();
			return folder.resolve(imageName);
		} catch (Exception e) {
			logger.warn("Cannot resolve file path for url: {}", imageUrl, e);
			return null;
		}
	}

	String imageExtension(String imageName) {
		return imageName != null && imageName.contains(".") ? imageName.substring(imageName.lastIndexOf(".") + 1) : "png";
	}

	void removedInconsistentImages(List<Path> tempImagePath) {
		for (Path p : tempImagePath) {
			try {
				Files.deleteIfExists(p);
			} catch (IOException ex) {
				logger.warn("Failed to delete file during cleanup: {}", p, ex);
			}
		}
	}
}

@Repository
interface ImageMetadataRepository extends CrudRepository<ImageMetadata,UUID> {

	Optional<ImageMetadata> findById(UUID id);

	@Query("SELECT * FROM advertisement_image_metadata WHERE advertisement_id= :advertisementId")
	Set<ImageMetadata> findByAdvertisementId(@Param("advertisementId") UUID advertisementId);

	@Query("SELECT * FROM advertisement_image_metadata WHERE advertisement_id= :advertisementId AND is_main = TRUE")
	Optional<ImageMetadata> findMainImageWithAdvertisementId(@Param("advertisementId") UUID advertisementId);

}

@Table("advertisement_image_metadata")
record ImageMetadata(
		@Id
		UUID id,
		@Version
		Integer version,
		String url,
        Status status,
		boolean isMain,
		LocalDateTime insertedAt,
		UUID advertisementId
) {}

record AddImageMetadataRequest(
		Boolean isMain,
		UUID advertisementId
)  {}

record ImageMetadataResponse(
		String url,
		Boolean isMain,
		LocalDateTime insertedAt
){}

enum Status {

	PENDING("PENDING"),
	SUCCESSED("SUCCESSED"),
	FAILED("FAILED");

	final String status;

	Status(String status) {
		this.status = status;
	}

}

@Mapper(componentModel = "spring",nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, imports = {LocalDateTime.class,UuidCreator.class})
interface ImageMetadataMapper {

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "url", ignore = true)
	@Mapping(target = "status", constant = "PENDING")
	@Mapping(target = "insertedAt", expression = "java(LocalDateTime.now())")
	ImageMetadata mapAddImageMetadataRequestToImageMetadata(AddImageMetadataRequest addImageMetadataRequest);

	@Mapping(target = "id", expression = "java(UuidCreator.getTimeOrderedEpoch())")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "url", source = "url")
	@Mapping(target = "status", constant = "PENDING")
	@Mapping(target = "insertedAt", expression = "java(LocalDateTime.now())")
	ImageMetadata mapAddImageMetadataRequestToImageMetadata(AddImageMetadataRequest addImageMetadataRequest, String url);

	@Mapping(target = "url", source = "url")
	@Mapping(target = "status", constant = "SUCCESSED")
	ImageMetadata mapImageMetadataWithUrlAndStatusAndSuccessed(ImageMetadata imageMetadata, String url);

	@Mapping(target = "id", source = "imageMetadata.id")
	@Mapping(target = "version", ignore = true)
	@Mapping(target = "url", source = "url")
	@Mapping(target = "status", constant = "FAILED")
	@Mapping(target = "insertedAt", source = "imageMetadata.insertedAt")
	ImageMetadata mapImageMetadataWithUrlAndStatusAndFailed(ImageMetadata imageMetadata, String url);

	ImageMetadataResponse mapRequestToResponse(ImageMetadata imageMetadata);

	List<ImageMetadataResponse> mapRequestListToResponse(List<ImageMetadata> imageMetadata);

	@Mapping(target = "isMain", expression = "java(Boolean.FALSE)")
	AddImageMetadataRequest mapAddImageMetadataRequestToFalse(AddImageMetadataRequest addImageMetadataRequest);

	@Mapping(target = "isMain", expression = "java(Boolean.TRUE)")
	AddImageMetadataRequest mapAddImageMetadataRequestToTrue(AddImageMetadataRequest addImageMetadataRequest);

}

