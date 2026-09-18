package com.finotech.jewellery.modules.storefront.api;

import com.finotech.jewellery.modules.storefront.application.CustomerAuthService;
import com.finotech.jewellery.modules.storefront.application.TenantService;
import com.finotech.jewellery.shared.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Phone + one-time code sign-in for the customer app. */
@Tag(name = "Storefront (customer sign-in)")
@RestController
@RequestMapping("/api/v1/customer-auth")
@RequiredArgsConstructor
public class CustomerAuthController {

    private final CustomerAuthService auth;
    private final TenantService tenants;

    public record OtpRequest(@NotBlank String phone) {
    }

    public record OtpVerifyRequest(@NotBlank String phone, @NotBlank String code) {
    }

    @Operation(summary = "Send a one-time code to a phone number")
    @PostMapping("/otp/request")
    public ApiResponse<CustomerAuthService.Challenge> request(
            @RequestHeader(value = PublicStorefrontController.TENANT_HEADER, required = false)
            String tenantKey,
            @Valid @RequestBody OtpRequest request) {
        return ApiResponse.ok(auth.requestOtp(tenants.require(tenantKey), request.phone()));
    }

    @Operation(summary = "Exchange a code for a customer token",
            description = "401 when the code is wrong or expired. A number the shop does not "
                    + "know yet becomes a new customer whose name is empty until they set it.")
    @PostMapping("/otp/verify")
    public ApiResponse<CustomerAuthService.Session> verify(
            @RequestHeader(value = PublicStorefrontController.TENANT_HEADER, required = false)
            String tenantKey,
            @Valid @RequestBody OtpVerifyRequest request) {
        return ApiResponse.ok(auth.verifyOtp(tenants.require(tenantKey), request.phone(),
                request.code()));
    }
}
