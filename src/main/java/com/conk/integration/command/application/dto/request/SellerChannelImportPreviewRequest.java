package com.conk.integration.command.application.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 셀러 채널 주문 가져오기 미리보기 요청 body를 표현한다.
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SellerChannelImportPreviewRequest {

    private String storeAlias;
    private String contactEmail;
    private String syncWindow;
    private Boolean autoImport;
}
