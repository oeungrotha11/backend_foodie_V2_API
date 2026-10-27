package com.api.v1.bearerauth.controller;

import com.api.v1.bearerauth.common.response.SuccessResponse;
import com.api.v1.bearerauth.model.request.RewardRuleRequest;
import com.api.v1.bearerauth.model.response.RewardRuleResponse;
import com.api.v1.bearerauth.service.RewardService;
import com.api.v1.bearerauth.util.ApiResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reward-rules")
@RequiredArgsConstructor
public class RewardRuleController {
    private final RewardService rewardService;

    @GetMapping
    public ResponseEntity<SuccessResponse<List<RewardRuleResponse>>> list() {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, rewardService.listRules()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<RewardRuleResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, rewardService.getRule(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<RewardRuleResponse>> create(@Valid @RequestBody RewardRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseUtil.success(HttpStatus.CREATED, rewardService.createRule(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<RewardRuleResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RewardRuleRequest request) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, rewardService.updateRule(id, request)));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<RewardRuleResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, rewardService.activateRule(id)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse<RewardRuleResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseUtil.success(HttpStatus.OK, rewardService.deactivateRule(id)));
    }
}
