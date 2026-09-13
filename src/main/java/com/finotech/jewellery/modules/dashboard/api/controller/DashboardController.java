package com.finotech.jewellery.modules.dashboard.api.controller;

import com.finotech.jewellery.modules.dashboard.api.response.DashboardSummaryResponse;
import com.finotech.jewellery.modules.dashboard.application.service.DashboardService;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @PreAuthorize}: authentication is enough to call it, and the
 * service decides section by section what the caller may see.
 */
@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Home-screen summary, permission-filtered per section",
            description = "branchId defaults to the X-Branch-Id header, then the caller's "
                    + "primary branch, then their only branch. Sections the caller may not "
                    + "see are omitted. Cached for 30 seconds per user and branch; pass "
                    + "refresh=true to bypass.")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> summary(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.summary(branchId, refresh)));
    }
}
