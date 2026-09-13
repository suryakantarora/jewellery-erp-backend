package com.finotech.jewellery.shared.app;

import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public: the app calls this before it has a token, to decide whether it may
 * start at all. Listed under {@code jewellery.security.public-paths}.
 */
@Tag(name = "App")
@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class AppVersionController {

    private final AppVersionProperties properties;

    @Operation(summary = "Version policy for a mobile platform",
            description = "Pass the running version as ?current=1.2.3 (a +build suffix is "
                    + "tolerated) and forceUpdate is computed server-side.")
    @GetMapping("/version")
    public ResponseEntity<ApiResponse<AppVersionResponse>> version(
            @RequestParam AppPlatform platform,
            @RequestParam(required = false) String current) {
        AppVersionProperties.Platform policy = properties.forPlatform(platform);
        SemanticVersion min = SemanticVersion.parse(policy.minSupported());
        SemanticVersion latest = SemanticVersion.parse(policy.latest());

        boolean forceUpdate = false;
        boolean updateAvailable = false;
        if (current != null && !current.isBlank()) {
            try {
                SemanticVersion running = SemanticVersion.parse(current);
                forceUpdate = running.isOlderThan(min);
                updateAvailable = running.isOlderThan(latest);
            } catch (IllegalArgumentException ex) {
                // An unparseable client version is not a reason to lock the user out.
                forceUpdate = false;
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(new AppVersionResponse(platform,
                min.toString(), latest.toString(), policy.storeUrl(), policy.message(),
                forceUpdate, updateAvailable)));
    }
}
