package com.conk.integration.command.application.dto.request;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WMS 내부 송장 일괄 조회 요청 DTO다.
 */
@Getter
@Setter
@NoArgsConstructor
public class InternalLabelBatchQueryRequest {

    private List<String> orderIds;
}
