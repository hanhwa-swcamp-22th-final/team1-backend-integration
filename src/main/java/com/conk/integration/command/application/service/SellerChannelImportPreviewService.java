package com.conk.integration.command.application.service;

import com.conk.integration.command.application.dto.request.SellerChannelImportPreviewRequest;
import com.conk.integration.command.application.dto.response.SellerChannelImportPreviewResponse;
import com.conk.integration.command.domain.aggregate.enums.OrderChannel;
import com.conk.integration.common.SellerIdValidator;
import com.conk.integration.common.channel.ChannelStrategySelector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// 셀러 채널 주문 가져오기 미리보기를 수행하는 read-only command 서비스다.
@Service
@RequiredArgsConstructor
public class SellerChannelImportPreviewService {

    private final List<ChannelImportPreviewProvider> previewProviders;

    public SellerChannelImportPreviewResponse preview(
            String sellerId,
            OrderChannel orderChannel,
            SellerChannelImportPreviewRequest request) {

        SellerIdValidator.requireValid(sellerId);
        ChannelImportPreviewProvider provider = ChannelStrategySelector.select(
                previewProviders,
                orderChannel,
                "지원하지 않는 주문 동기화 채널입니다: ");
        return provider.preview(sellerId, request);
    }
}
