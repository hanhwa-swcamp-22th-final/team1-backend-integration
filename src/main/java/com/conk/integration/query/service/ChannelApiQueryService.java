package com.conk.integration.query.service;

import com.conk.integration.common.channel.dto.ChannelConnectionInfo;
import com.conk.integration.common.channel.dto.ChannelCredential;
import com.conk.integration.common.exception.BusinessException;
import com.conk.integration.common.exception.ErrorCode;
import com.conk.integration.query.mapper.ChannelApiMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 채널 API 자격증명과 연결 상세 정보를 query side에서 조회하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class ChannelApiQueryService {

    private final ChannelApiMapper channelApiMapper;

    /**
     * sellerId와 channelName으로 채널 자격증명을 조회한다.
     *
     * @param sellerId 셀러 식별자
     * @param channelName 정규화된 채널 코드
     * @return 공통 채널 자격증명 DTO
     * @throws BusinessException 자격증명이 등록되지 않은 경우 (INT-103)
     */
    public ChannelCredential findChannelCredential(String sellerId, String channelName) {
        ChannelCredential credential = channelApiMapper.findChannelCredential(sellerId, channelName);
        if (credential == null) {
            throw new BusinessException(
                    ErrorCode.CHANNEL_CREDENTIALS_NOT_FOUND,
                    "채널 자격증명을 찾을 수 없습니다: sellerId=" + sellerId + ", channelName=" + channelName);
        }
        return credential;
    }

    /**
     * sellerId와 channelName으로 채널 연결 상세를 조회한다.
     *
     * @param sellerId 셀러 식별자
     * @param channelName 채널 코드
     * @return 채널 연결 상세 read model
     * @throws BusinessException 연결 정보가 등록되지 않은 경우
     */
    public ChannelConnectionInfo findChannelConnectionInfo(String sellerId, String channelName) {
        ChannelConnectionInfo connectionInfo = channelApiMapper.findConnectionInfo(sellerId, channelName);
        if (connectionInfo == null) {
            throw new BusinessException(ErrorCode.CHANNEL_CONNECTION_NOT_FOUND);
        }
        return connectionInfo;
    }
}
