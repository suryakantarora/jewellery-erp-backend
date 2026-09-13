package com.finotech.jewellery.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finotech.jewellery.shared.config.CorrelationIdFilter;
import com.finotech.jewellery.shared.exception.ApiError;
import com.finotech.jewellery.shared.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the optional {@code X-Branch-Id} header into {@link BranchContext}.
 *
 * <p>Runs after {@link JwtAuthenticationFilter} so the principal is known. A
 * header naming a branch the caller is not assigned to is refused with the
 * same 403 payload the access-denied handler writes, before any controller
 * runs; a super administrator may name any branch. A malformed header is a
 * 400. Anonymous requests carry the header through unchecked: the endpoint
 * itself decides whether it needs authentication.
 *
 * <p>Not a {@code @Component} on purpose: Spring Boot would also register it
 * in the plain servlet chain, ahead of security, where the principal is not
 * yet available. {@code SecurityConfig} adds it to the security chain directly.
 */
@RequiredArgsConstructor
public class BranchContextFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(BranchContext.HEADER);
        if (!StringUtils.hasText(header)) {
            BranchContext.clear();
            chain.doFilter(request, response);
            return;
        }

        UUID branchId;
        try {
            branchId = UUID.fromString(header.trim());
        } catch (IllegalArgumentException ex) {
            write(response, ErrorCode.VALIDATION_FAILED,
                    BranchContext.HEADER + " must be a UUID", request.getRequestURI());
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && !user.hasAccessToBranch(branchId)) {
            write(response, ErrorCode.FORBIDDEN, "No access to branch " + branchId,
                    request.getRequestURI());
            return;
        }

        BranchContext.set(branchId);
        try {
            chain.doFilter(request, response);
        } finally {
            BranchContext.clear();
        }
    }

    private void write(HttpServletResponse response, ErrorCode code, String message, String path)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of(code, message, path, CorrelationIdFilter.current()));
    }
}
