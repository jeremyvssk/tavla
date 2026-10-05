// Admin image upload and removal for a product.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.ProductImageResponse;
import com.iloveshopping.catalog.exception.CatalogNotFoundException;
import com.iloveshopping.catalog.exception.InvalidImageException;
import com.iloveshopping.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class ProductImageService {

    // Matches spring.servlet.multipart.max-file-size and nginx client_max_body_size; checked again
    // here so the limit holds even when the multipart layer is bypassed, as in MockMvc.
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final ProductRepository productRepository;
    private final ProductImageRepository imageRepository;
    private final ImageProcessor imageProcessor;
    private final StorageService storageService;

    public ProductImageService(ProductRepository productRepository, ProductImageRepository imageRepository,
                               ImageProcessor imageProcessor, StorageService storageService) {
        this.productRepository = productRepository;
        this.imageRepository = imageRepository;
        this.imageProcessor = imageProcessor;
        this.storageService = storageService;
    }

    @Transactional
    public ProductImageResponse upload(UUID productId, MultipartFile file, String altText, boolean primary) {
        if (!productRepository.existsById(productId)) {
            throw ProductService.notFound();
        }
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("image_required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidImageException("image_too_large");
        }
        ImageProcessor.ProcessedImage processed;
        try {
            processed = imageProcessor.process(file.getBytes());
        } catch (IOException e) {
            throw new InvalidImageException("invalid_image");
        }

        String url = storageService.store(processed.bytes(), processed.extension());
        // The file is written before the row commits; if the transaction fails, remove the orphan.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storageService.delete(url);
                }
            }
        });

        // The first image of a product is always primary, so a listing always has one to show.
        boolean makePrimary = primary || !imageRepository.existsByProductId(productId);
        if (makePrimary) {
            imageRepository.clearPrimary(productId);
        }
        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setUrl(url);
        image.setAltText(altText);
        image.setDisplayOrder(imageRepository.nextDisplayOrder(productId));
        image.setPrimary(makePrimary);
        return toResponse(imageRepository.save(image));
    }

    @Transactional
    public void delete(UUID productId, Integer imageId) {
        ProductImage image = imageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new CatalogNotFoundException("image_not_found"));
        imageRepository.delete(image);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageService.delete(image.getUrl());
            }
        });
    }

    static ProductImageResponse toResponse(ProductImage i) {
        return new ProductImageResponse(i.getId(), i.getUrl(), i.getAltText(), i.getDisplayOrder(), i.isPrimary(),
                i.getFraming());
    }
}
