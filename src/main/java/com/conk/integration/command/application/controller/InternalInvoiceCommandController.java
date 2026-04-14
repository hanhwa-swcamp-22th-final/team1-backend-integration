package com.conk.integration.command.application.controller;

import com.conk.integration.command.application.dto.request.InternalLabelBatchQueryRequest;
import com.conk.integration.command.application.dto.request.InternalLabelRequest;
import com.conk.integration.command.application.dto.response.InternalLabelRecommendationResponse;
import com.conk.integration.command.application.dto.response.InternalLabelResponse;
import com.conk.integration.command.application.service.InternalLabelService;
import com.conk.integration.common.ApiResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * WMS 내부 송장 발급 컨트롤러다.
 */
@RestController
@RequestMapping("/integrations/internal/labels")
@RequiredArgsConstructor
public class InternalInvoiceCommandController {

    private final InternalLabelService internalLabelService;

    @PostMapping
    public ResponseEntity<ApiResponse<InternalLabelResponse>> issueLabel(
            @RequestBody InternalLabelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(internalLabelService.issueLabel(request)));
    }

    @PostMapping("/recommendation")
    public ResponseEntity<ApiResponse<InternalLabelRecommendationResponse>> recommendLabel(
            @RequestBody InternalLabelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(internalLabelService.recommendLabel(request)));
    }

    @PostMapping("/batch-query")
    public ResponseEntity<ApiResponse<Map<String, InternalLabelResponse>>> batchQuery(
            @RequestBody InternalLabelBatchQueryRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(internalLabelService.getLabels(request.getOrderIds())));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, InternalLabelResponse>>> getByOrderIds(
            @RequestParam("orderIds") java.util.List<String> orderIds) {
        return ResponseEntity.ok(ApiResponse.ok(internalLabelService.getLabels(orderIds)));
    }
}
