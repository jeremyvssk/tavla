// Brand listing and admin creation.
package com.iloveshopping.catalog;

import com.iloveshopping.catalog.dto.BrandRequest;
import com.iloveshopping.catalog.dto.BrandResponse;
import com.iloveshopping.catalog.exception.CatalogConflictException;
import com.iloveshopping.catalog.exception.InvalidReferenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @Transactional(readOnly = true)
    public List<BrandResponse> list() {
        return brandRepository.findAllByOrderByNameAsc().stream().map(BrandService::toResponse).toList();
    }

    @Transactional
    public BrandResponse create(BrandRequest request) {
        if (brandRepository.existsBySlug(request.slug())) {
            throw new CatalogConflictException("slug_already_exists");
        }
        Brand brand = new Brand();
        brand.setName(request.name().strip());
        brand.setSlug(request.slug());
        brand.setLogoUrl(request.logoUrl());
        try {
            return toResponse(brandRepository.saveAndFlush(brand));
        } catch (DataIntegrityViolationException e) {
            throw new CatalogConflictException("slug_already_exists");
        }
    }

    Brand require(Integer id) {
        return brandRepository.findById(id).orElseThrow(() -> new InvalidReferenceException("brandId"));
    }

    static BrandResponse toResponse(Brand b) {
        return b == null ? null : new BrandResponse(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl());
    }
}
