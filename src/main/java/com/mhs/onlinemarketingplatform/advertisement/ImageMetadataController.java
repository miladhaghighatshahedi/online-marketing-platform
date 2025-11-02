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

import com.fasterxml.jackson.annotation.JsonInclude;
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
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author Milad Haghighat Shahedi
 */
@Controller("advertisementImageController")
@ResponseBody
class AdvertisementImageController {

	private final ImageMetadataService imageMetadataService;
	private final ImageUploadService imageUploadService;
	private final ImageUploadServiceAsync imageUploadServiceAsync;
	private final ImagePathProperties imagePathProperties;

	public AdvertisementImageController(
			ImageMetadataService imageMetadataService,
			ImageUploadService imageUploadService,
			ImageUploadServiceAsync imageUploadServiceAsync,
			ImagePathProperties imagePathProperties) {
		this.imageMetadataService = imageMetadataService;
		this.imageUploadService = imageUploadService;
		this.imageUploadServiceAsync = imageUploadServiceAsync;
		this.imagePathProperties = imagePathProperties;
	}

	@PostMapping(value = "/api/images/upload/multiple",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<List<ImageMetadataResponse>> uploadMultipleImages(
			@RequestPart("metadataList") List<AddImageMetadataRequest> metadataList,
			@RequestPart("images") List<MultipartFile> images) {

		List<ImageMetadataResponse> imageMetadataResponses = this.imageMetadataService.saveImageMetadata(metadataList, images);

		Path imageBasePath = this.imageUploadService.createMainDirectoryIfNotExists(imageMetadataResponses,imagePathProperties.getAdvertisementImagePath());

		List<byte[]> fileContents = images.stream().map(file -> {try {return file.getBytes();} catch (IOException e) {throw new UncheckedIOException(e);}}).toList();

		this.imageUploadServiceAsync.storeImagesIntoFileSystemAsync(imageMetadataResponses,fileContents,imageBasePath);

		return ResponseEntity.accepted().body(imageMetadataResponses);
	}

	@GetMapping( "/api/images/{id}")
	ResponseEntity<ImageMetadataResponse> findById(@PathVariable("id") UUID id) {
		return ResponseEntity.ok(this.imageMetadataService.findById(id));
	}

	@GetMapping( "/api/images/{id}/advertisement/{advertisementId}")
	ResponseEntity<ImageMetadataResponse> findByIdAndAdvertisementId(@PathVariable("id") UUID id,@PathVariable("advertisementId") UUID advertisementId) {
		return ResponseEntity.ok(this.imageMetadataService.findByIdAndAdvertisementId(id,advertisementId));
	}

	@GetMapping( "/api/images/advertisement/{advertisementId}")
	ResponseEntity<List<ImageMetadataResponse>> findByAdvertisementId(@PathVariable("advertisementId") UUID advertisementId) {
		return ResponseEntity.ok(this.imageMetadataService.findByAdvertisementId(advertisementId));
	}

	@DeleteMapping( "/api/images/{id}/advertisement/{advertisementId}")
	ResponseEntity<List<ImageMetadataResponse>> findMainImageWithAdvertisementId(@PathVariable("id") UUID id,@PathVariable("advertisementId") UUID advertisementId) {
		this.imageMetadataService.delete(id,advertisementId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/images/{advertisementId}/{imageName}")
	ResponseEntity<Resource> getImage(
			@PathVariable("advertisementId") String advertisementId,
			@PathVariable("imageName") String imageName) throws Exception{

		Path imagePath = Paths.get("src/main/resources/image/advertisement").resolve(advertisementId).resolve(imageName).normalize();

		if (!Files.exists(imagePath)) {
			return ResponseEntity.notFound().build();
		}

		String contentType = Files.probeContentType(imagePath);
		if (contentType == null) {
			contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
		}

		Resource resource = new UrlResource(imagePath.toUri());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(contentType))
				.body(resource);
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

	@Transactional
	List<ImageMetadataResponse> saveImageMetadata(List<AddImageMetadataRequest> requests, List<MultipartFile> files) {
		UUID advertisementId = requests.get(0).advertisementId();
		logger.info("Uploading new images for advertisement with the ID: {} request-size: {} image-size: {}", advertisementId, requests.size(), files.size());

		if (files == null || files.isEmpty() || requests == null || requests.isEmpty()) {
			return Collections.emptyList();
		}

		if (requests.size() != files.size()) {
			throw new InconsistentImageDataException(messageSource.getMessage("error.image.inconsistent.image.data", new Object[]{}, LocaleContextHolder.getLocale()), ImageErrorCode.INCONSISTENT_IMAGE_METADATA);
		}

		if (!this.advertisementService.existsById(advertisementId)) {
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{advertisementId},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);}

		List<ImageMetadata> retrievedImageMetataDataList = this.imageMetadataRepository.findByAdvertisementId(advertisementId);
		int noOfRetrievedImages = retrievedImageMetataDataList == null ? 0 : retrievedImageMetataDataList.size();
		if (noOfRetrievedImages + files.size() > MAX_IMAGES_PER_ADVERTISEMENT) {
			throw new TotalNumberOfImagesExceedsException(
					messageSource.getMessage("error.image.total.number.of.imges.exceeded",
							new Object[]{}, LocaleContextHolder.getLocale()),
					ImageErrorCode.TOTAL_NUMBER_OF_IMAGES_EXCEEDS);}

		List<ImageMetadata> savedMetadata;

		if (retrievedImageMetataDataList.stream().noneMatch(ImageMetadata::isMain)) {
			savedMetadata = saveWithOneIsSetToMain(requests, files);
		} else {
			savedMetadata = saveWithoutMain(requests, files);
		}

		return this.imageMetadataMapper.mapRequestListToResponse(savedMetadata);
	}

	ImageMetadataResponse findById(UUID id) {
		logger.info("Looking up for ImageMetadata with the ID: {}",id);
		ImageMetadata retrievedImageMetadata = this.imageMetadataRepository.findById(id).orElseThrow(() ->
				new ImageNotFoundException(
						messageSource.getMessage("error.image.image.with.id.not.found",
								new Object[]{id},
								LocaleContextHolder.getLocale()),
						ImageErrorCode.IMAGE_NOT_FOUND));

		return this.imageMetadataMapper.mapRequestToResponse(retrievedImageMetadata);
	}

	ImageMetadataResponse findByIdAndAdvertisementId(UUID id,UUID advertisementId) {
		logger.info("Looking up for ImageMetadata with the id {} for the advertisement with the ID: {}",id,advertisementId);

		ImageMetadata retrievedImageMetadata = this.imageMetadataRepository.findByIdAndAdvertisementId(id,advertisementId).orElseThrow(() ->
				new ImageNotFoundException(
						messageSource.getMessage("error.image.image.with.id.and.advertisement.id.not.found",
								new Object[]{id,advertisementId},
								LocaleContextHolder.getLocale()),
						ImageErrorCode.IMAGE_NOT_FOUND));

		return this.imageMetadataMapper.mapRequestToResponse(retrievedImageMetadata);
	}

	List<ImageMetadataResponse> findByAdvertisementId(UUID advertisementId) {
		logger.info("Looking up for ImageMetadata for the advertisement with the ID: {}",advertisementId);

		if(!this.advertisementService.existsById(advertisementId)){
			throw new AdvertisementNotFoundException(
					messageSource.getMessage("error.advertisement.advertisement.with.id.not.found",
							new Object[]{advertisementId},
							LocaleContextHolder.getLocale()),
					AdvertisementErrorCode.ADVERTISEMENT_NOT_FOUND);}

		List<ImageMetadata> imageMetadataSet = this.imageMetadataRepository.findByAdvertisementId(advertisementId);
		if(imageMetadataSet.isEmpty()){
			logger.info("Advertisement with the id {} has no images", advertisementId);
		}

		return this.imageMetadataMapper.mapRequestListToResponse(imageMetadataSet);
	}

	void delete(UUID id,UUID advertisementId) {
		ImageMetadata retrievedImageMetadata = this.imageMetadataRepository.findByIdAndAdvertisementId(id,advertisementId).orElseThrow(() ->
				new ImageNotFoundException(
						messageSource.getMessage("error.image.image.with.id.and.advertisement.id.not.found",
								new Object[]{id,advertisementId},
								LocaleContextHolder.getLocale()),
						ImageErrorCode.IMAGE_NOT_FOUND));

		logger.info("Deleting exisiting imageMetatdata with the id: {} and advertisementId: {}",retrievedImageMetadata.id(),advertisementId);
		try {
			String imageAddress = advertisementId.toString() + "/" + id.toString() + ".png";
			Path pathToImage = Paths.get(imagePathProperties.getAdvertisementImagePath(),imageAddress).toAbsolutePath().normalize();

			if(Files.exists(pathToImage)) {
				Files.delete(pathToImage);
			}

			this.imageMetadataRepository.delete(retrievedImageMetadata);
			this.auditLogger.log("IMAGE_METADATA_DELETED", "IMAGE_METADATA", "IMAGE_METADATA for the advertisement with the Id: " + retrievedImageMetadata.advertisementId());

		} catch (Exception e) {
			throw new RuntimeException(e);
		}

	}

	private List<ImageMetadata> saveWithOneIsSetToMain(List<AddImageMetadataRequest> requests,List<MultipartFile> files) {
		List<AddImageMetadataRequest> normalizedRequestsWithOneMain = normalizeRequestWithOneMainImage(requests);
		List<ImageMetadata> savedMetadata = new ArrayList<>(requests.size());
		for (int i = 0; i < files.size(); i++) {
			AddImageMetadataRequest addImageMetadataRequest = normalizedRequestsWithOneMain.get(i);
			ImageMetadata mappedMetaData = this.imageMetadataMapper.mapAddImageMetadataRequestToImageMetadata(addImageMetadataRequest);
			savedMetadata.add(this.imageMetadataRepository.save(mappedMetaData));
			this.auditLogger.log("IMAGE_METADATA_SAVED", "IMAGE_METADATA", "IMAGE_METADATA for the advertisement with the Id: " + mappedMetaData.advertisementId());
		}
		return savedMetadata;
	}

	private List<ImageMetadata> saveWithoutMain(List<AddImageMetadataRequest> requests,List<MultipartFile> files) {
		List<ImageMetadata> savedMetadata = new ArrayList<>(requests.size());
		for (int i = 0; i < files.size(); i++) {
			AddImageMetadataRequest addImageMetadataRequest = requests.get(i);
			ImageMetadata mappedMetaData = this.imageMetadataMapper.mapAddImageMetadataRequestToImageMetadata(addImageMetadataRequest);
			savedMetadata.add(this.imageMetadataRepository.save(mappedMetaData));
			this.auditLogger.log("IMAGE_METADATA_SAVED", "IMAGE_METADATA", "IMAGE_METADATA for advertisement with the Id: " + mappedMetaData.advertisementId());
		}
		return savedMetadata;
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

	private final ImageMetadataMapper imageMetadataMapper;
	private final ImageMetadataRepository imageMetadataRepository;
	private final AuditLogger auditLogger;

	public ImageUploadService(
			ImageMetadataMapper imageMetadataMapper,
			ImageMetadataRepository imageMetadataRepository,
			AuditLogger auditLogger) {
		this.imageMetadataMapper = imageMetadataMapper;
		this.imageMetadataRepository = imageMetadataRepository;
		this.auditLogger = auditLogger;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void updateImageMetatdata(ImageMetadata imageMetadata, String imageUrl) {
//		ImageMetadata mappedMetadata = this.imageMetadataMapper.mapImageMetadataWithUrlAndStatusAndSuccessed(imageMetadata, imageUrl);
		ImageMetadata savedMetadata = this.imageMetadataRepository.save(mappedMetadata);
		this.auditLogger.log("IMAGE_WITH_METADATA_STORED", "IMAGE_METADATA", "Image ID: " + savedMetadata.id());
	}

	Path createMainDirectoryIfNotExists(List<ImageMetadataResponse> imageMetadataResponses, String path) {
		if(imageMetadataResponses == null) {
			throw new RuntimeException("Exception in creating the main directory ");
		}

		UUID advertisementId = imageMetadataResponses.get(0).advertisementId();

		logger.info("Main directory with the id: {} and path: {} created", advertisementId, path);
		try {
			Path direcetory = Paths.get(path, advertisementId.toString()).toAbsolutePath().normalize();
			if (! Files.exists(direcetory)) {
				Files.createDirectories(direcetory);
			}
			return direcetory;
		} catch (Exception e) {
			throw new RuntimeException("Exception in creating a main directory to store image");
		}
	}

	String prepareImageUpload(UUID imageId, byte[] imageFile, Path directory) {
		logger.info("Stroing an image into the directory with id: {} and path: {} ", imageId, directory);
		logger.info("Writing image {} ({} bytes) to {}", imageId, imageFile.length, directory);
		String imageName = imageId.toString() + ".png";
		try {
			Path imagePath = directory.resolve(imageName);
			Files.write(imagePath, imageFile);
			return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/advertisements/images/" + imageName).toUriString();
		} catch (Exception e) {
			throw new RuntimeException("Exception in storing an image into the direcotry");
		}
	}

	String imageExtension(String imageName) {
		return imageName != null && imageName.contains(".") ? imageName.substring(imageName.lastIndexOf(".") + 1) : "png";
	}

}

@Service
class ImageUploadServiceAsync {

	private static final Logger logger = LoggerFactory.getLogger(ImageUploadServiceAsync.class);

	private final ImageUploadService imageUploadService;

	public ImageUploadServiceAsync(ImageUploadService imageUploadService) {
		this.imageUploadService = imageUploadService;
	}

	@Async("imageTaskExecutor")
	public void storeImagesIntoFileSystemAsync(List<ImageMetadataResponse> responses, List<byte[]> fileContents, Path imageBasePath) {
		for (int i = 0; i < responses.size(); i++) {
			ImageMetadataResponse imageMetadataResponse = responses.get(i);
			byte[] file = fileContents.get(i);
			try {
				logger.info("Current thread: {}", Thread.currentThread().getName());
				String imageUrl = this.imageUploadService.prepareImageUpload(imageMetadataResponse.id(), file, imageBasePath);
				this.imageUploadService.updateImageMetatdata(imageMetadata, imageUrl);
			} catch (Exception e) {
				logger.error("Failed to upload images with the id: {} ", imageMetadata, e);
				throw new RuntimeException("Failed to upload advertisement images", e);
			}
		}
	}

}

@Repository
interface ImageMetadataRepository extends CrudRepository<ImageMetadata,UUID> {

	Optional<ImageMetadata> findById(UUID id);

	@Query("SELECT * FROM advertisement_image_metadata WHERE id= :id AND advertisement_id= :advertisementId")
	Optional<ImageMetadata> findByIdAndAdvertisementId(@Param("id") UUID id,@Param("advertisementId") UUID advertisementId);

	@Query("SELECT * FROM advertisement_image_metadata WHERE advertisement_id= :advertisementId")
	List<ImageMetadata> findByAdvertisementId(@Param("advertisementId") UUID advertisementId);

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

@JsonInclude(JsonInclude.Include.NON_NULL)
record ImageMetadataResponse(UUID id,String url, Boolean isMain, LocalDateTime insertedAt,UUID advertisementId) {}

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

