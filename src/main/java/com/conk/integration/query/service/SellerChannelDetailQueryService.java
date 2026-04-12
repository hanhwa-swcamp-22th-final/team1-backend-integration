package com.conk.integration.query.service;

import com.conk.integration.common.SellerIdValidator;
import com.conk.integration.common.channel.ChannelConnectionVerifier;
import com.conk.integration.common.channel.ChannelKeyResolver;
import com.conk.integration.common.channel.dto.ChannelConnectionInfo;
import com.conk.integration.common.channel.dto.SellerChannelDetailDto;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// 채널 상세 연결 정보를 조회하고 필요 시 실연결 여부를 검증한다.
@Service
@RequiredArgsConstructor
public class SellerChannelDetailQueryService {

    private final ChannelApiQueryService channelApiQueryService;
    private final List<ChannelConnectionVerifier> connectionVerifiers;

    /**
     * 셀러의 특정 채널 연결 상세를 조회한다.
     * SHOPIFY 채널은 실제 API ping 성공 시에만 연결된 상태로 본다.
     *
     * @param sellerId 셀러 식별자
     * @param channelKey 채널 코드
     * @return 연결된 채널 상세 정보
     */
    public SellerChannelDetailDto getChannelDetail(String sellerId, String channelKey) {
        SellerIdValidator.requireValid(sellerId);
        String normalizedChannelKey = ChannelKeyResolver.normalize(channelKey, ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);

        ChannelConnectionInfo connectionInfo = channelApiQueryService.findChannelConnectionInfo(sellerId, normalizedChannelKey);
        ensureConnected(connectionInfo);

        return new SellerChannelDetailDto(
                connectionInfo.getChannelName(),
                connectionInfo.getStoreName(),
                connectionInfo.getChannelApi(),
                connectionInfo.getConnectedAt());
    }

    /**
     * SHOPIFY 채널인 경우 실제 ping을 호출해 연결 유효성을 재확인한다.
     *
     * @param connectionInfo 채널 연결 상세 read model
     * @throws BusinessException ping 검증에 실패한 경우 (INT-404)
     */
    private void ensureConnected(ChannelConnectionInfo connectionInfo) {
        Optional<ChannelConnectionVerifier> verifier = connectionVerifiers.stream()
                .filter(candidate -> candidate.supports(connectionInfo.getChannelName()))
                .findFirst();

        if (verifier.isEmpty()) {
            return;
        }

        boolean connected = verifier.get().verify(connectionInfo.getStoreName(), connectionInfo.getChannelApi());
        if (!connected) {
            throw new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
        }
    }
}
