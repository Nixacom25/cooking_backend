package com.cooked.backend.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface CloudinaryService {
    String upload(MultipartFile file) throws IOException;
    String uploadUrl(String url) throws IOException;
}
