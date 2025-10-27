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

import com.mhs.onlinemarketingplatform.catalog.config.ImagePathProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * @author Milad Haghighat Shahedi
 */
@Service("imageService")
public class ImageService {

	private static final Logger logger = LoggerFactory.getLogger(ImageService.class);

	private final ImagePathProperties properties;

	public ImageService(ImagePathProperties properties) {
		this.properties = properties;
	}

	public Path createMainDirectory(UUID id, String path) {
		logger.info("Main directory with id: {} path {} created",id,path);
		try {
			Path direcetory = Paths.get(path, id.toString()).toAbsolutePath().normalize();
			if(! Files.exists(direcetory)) {
				Files.createDirectories(direcetory);
			}
			return direcetory;
		} catch (Exception e) {
			throw new RuntimeException("");
		}
	}

	public String prepareImageUploadWithoutDirectory(UUID imageId, MultipartFile image, Path directory) {
		logger.info("Saving images into directory directory with id: {} directry {} ",imageId,directory);
		String imageName  =  imageId.toString() + "." + imageExtension(image.getOriginalFilename());
		try {
			Path imagePath = directory.resolve(imageName);
			Files.copy(image.getInputStream(),imagePath,REPLACE_EXISTING);
			return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/advertisements/images/" + imageName).toUriString();
		} catch (Exception e) {
			throw new RuntimeException("");
		}
	}

	public Path resolveFilePath(UUID adId, String imageUrl) {
		try {
			String imageName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
			Path folder = Paths.get(properties.getAdvertisementImagePath(), adId.toString()).toAbsolutePath().normalize();
			return folder.resolve(imageName);
		} catch (Exception e) {
			logger.warn("Cannot resolve file path for url: {}", imageUrl, e);
			return null;
		}
	}

	private String imageExtension(String imageName) {
		return imageName != null && imageName.contains(".") ? imageName.substring(imageName.lastIndexOf(".") + 1) : "png";
	}

    public void removedInconsistentImages(List<Path> tempImagePath) {
	    for (Path p : tempImagePath) {
		    try {
			    Files.deleteIfExists(p);
		    } catch (IOException ex) {
			    logger.warn("Failed to delete file during cleanup: {}", p, ex);
		    }
	    }

    }

}
