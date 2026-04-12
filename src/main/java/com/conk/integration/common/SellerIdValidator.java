package com.conk.integration.common;

import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;

/**
 * sellerId 입력값에 대한 공통 검증 규칙을 제공한다.
 */
public final class SellerIdValidator {

    private SellerIdValidator() {
    }

    /**
     * sellerId가 null이거나 공백이면 예외를 발생시킨다.
     *
     * @param sellerId 검증할 셀러 식별자
     * @throws BusinessException sellerId가 비어 있는 경우 (INT-001)
     */
    public static void requireValid(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_SELLER_ID);
        }
    }
}
