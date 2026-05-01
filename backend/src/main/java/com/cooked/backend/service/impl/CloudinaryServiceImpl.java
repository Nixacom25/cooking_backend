package com.cooked.backend.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.cooked.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    @Value("${cloudinary.cloud.name}")
    private String cloudName;

    @Value("${cloudinary.api.key}")
    private String apiKey;

    @Value("${cloudinary.api.secret}")
    private String apiSecret;

    @Value("${cloudinary.folder}")
    private String folder;

    private Cloudinary getCloudinary() {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true));
    }

    @Override
    public String upload(MultipartFile file) throws IOException {
        Map<String, Object> uploadResult = getCloudinary().uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto"));

        return (String) uploadResult.get("secure_url");
    }

    @Override
    public String uploadUrl(String url) throws IOException {
        Map<String, Object> uploadResult = getCloudinary().uploader().upload(url, ObjectUtils.asMap(
                "folder", folder,
                "resource_type", "auto"));

        return (String) uploadResult.get("secure_url");
    }
}
