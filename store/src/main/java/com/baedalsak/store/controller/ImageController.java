package com.baedalsak.store.controller;

import com.baedalsak.common.auth.CurrentMember;
import com.baedalsak.common.auth.LoginMember;
import com.baedalsak.store.dto.request.ImageUploadUrlRequest;
import com.baedalsak.store.dto.response.ImageUploadUrlResponse;
import com.baedalsak.store.service.ImageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가게·메뉴 사진 업로드 URL (#110). 사장님 본인 가게만
 */
@RestController
@RequestMapping("/v1/stores/{storeId}/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    // 업로드 URL 발급. 프론트가 S3 에 직접 PUT 한 뒤 가게·메뉴 저장 요청에 imageKey 를 넣는다
    @PostMapping("/upload-url")
    public ImageUploadUrlResponse issueUploadUrl(@CurrentMember LoginMember member, @PathVariable Long storeId,
                                                 @Valid @RequestBody ImageUploadUrlRequest request) {
        return imageService.issueUploadUrl(member, storeId, request);
    }
}
