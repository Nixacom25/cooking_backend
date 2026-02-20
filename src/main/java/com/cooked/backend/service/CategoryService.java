package com.cooked.backend.service;

import java.util.List;

import com.cooked.backend.dto.request.CategoryRequest;
import com.cooked.backend.dto.response.CategoryResponse;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    List<CategoryResponse> getAll();
}
