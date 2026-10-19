package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.model.entity.Category;
import com.api.v1.bearerauth.model.entity.Product;
import com.api.v1.bearerauth.model.request.CreateProductRequest;
import com.api.v1.bearerauth.model.request.UpdateProductRequest;
import com.api.v1.bearerauth.model.response.ProductResponse;
import com.api.v1.bearerauth.repository.CategoryRepository;
import com.api.v1.bearerauth.repository.ProductRepository;
import com.api.v1.bearerauth.service.FileStorageService;
import com.api.v1.bearerauth.service.ProductService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ModelMapper mapper;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> list(int page, int size, Sort.Direction direction, String search, String category) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(direction, "productId"));

        String q = search == null ? "" : search.trim().toLowerCase();
        String c = category == null ? "" : category.trim();

        return productRepository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Product, Category> categoryJoin = root.join("category", JoinType.LEFT);

            if (!q.isEmpty()) {
                Predicate productNameLike = cb.like(
                        cb.lower(root.get("productName")), "%" + q + "%");
                // The persistent JPA attribute is "name"; "categoryName" is only a convenience getter.
                Predicate categoryNameLike = cb.like(
                        cb.lower(categoryJoin.get("name")), "%" + q + "%");
                predicates.add(cb.or(productNameLike, categoryNameLike));
            }

            if (!c.isEmpty()) {
                // The admin dashboard sends categoryId. Also accept a category name for API compatibility.
                if (c.matches("\\d+")) {
                    predicates.add(cb.equal(categoryJoin.get("categoryId"), Long.valueOf(c)));
                } else {
                    predicates.add(cb.equal(
                            cb.lower(categoryJoin.get("name")), c.toLowerCase()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        Product product = findProduct(id);
        return toResponse(product);
    }

    @Override
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (!hasImage(request.getImage())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product image is required");
        }

        Category category = findCategory(request.getCategoryId());
        String imageUrl = fileStorageService.store(request.getImage(), "products");

        Product product = new Product();
        product.setProductName(request.getProductName().trim());
        product.setDescription(request.getDescription().trim());
        product.setUnitPrice(request.getUnitPrice());
        product.setRating(request.getRating());
        product.setImageUrl(imageUrl);
        product.setCategory(category);

        return toResponse(productRepository.save(product));
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = findProduct(id);
        Category category = findCategory(request.getCategoryId());

        String oldImageUrl = product.getImageUrl();
        String newImageUrl = null;

        product.setProductName(request.getProductName().trim());
        product.setDescription(request.getDescription().trim());
        product.setUnitPrice(request.getUnitPrice());
        product.setRating(request.getRating());
        product.setCategory(category);

        if (hasImage(request.getImage())) {
            newImageUrl = fileStorageService.store(request.getImage(), "products");
            product.setImageUrl(newImageUrl);
        }

        Product savedProduct = productRepository.save(product);

        if (newImageUrl != null && oldImageUrl != null && !oldImageUrl.equals(newImageUrl)) {
            fileStorageService.delete(oldImageUrl);
        }

        return toResponse(savedProduct);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = findProduct(id);
        String imageUrl = product.getImageUrl();

        productRepository.delete(product);

        if (imageUrl != null) {
            fileStorageService.delete(imageUrl);
        }
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
    }

    private boolean hasImage(MultipartFile image) {
        return image != null && !image.isEmpty();
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse response = mapper.map(product, ProductResponse.class);

        if (product.getCategory() != null) {
            response.setCategoryId(product.getCategory().getCategoryId());
            response.setCategoryName(product.getCategory().getCategoryName());
        }

        return response;
    }
}
