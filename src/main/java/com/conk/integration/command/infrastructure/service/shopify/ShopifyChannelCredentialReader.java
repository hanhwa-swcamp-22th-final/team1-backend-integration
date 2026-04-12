package com.conk.integration.command.infrastructure.service.shopify;

import com.conk.integration.common.channel.ChannelCredentialReader;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.query.service.ChannelApiQueryService;
import org.springframework.stereotype.Service;

/**
 * Shopify 채널 자격증명 조회 구현체다.
 */
@Service
public class ShopifyChannelCredentialReader implements ChannelCredentialReader {

    private static final String SHOPIFY = "SHOPIFY";

    private final ChannelApiQueryService channelApiQueryService;

    public ShopifyChannelCredentialReader(ChannelApiQueryService channelApiQueryService) {
        this.channelApiQueryService = channelApiQueryService;
    }

    @Override
    public boolean supports(String channelKey) {
        return SHOPIFY.equalsIgnoreCase(channelKey);
    }

    @Override
    public ChannelCredential read(String sellerId, String channelKey) {
        return channelApiQueryService.findChannelCredential(sellerId, SHOPIFY);
    }
}
