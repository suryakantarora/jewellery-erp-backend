package com.finotech.jewellery.modules.notification.api.controller;

import com.finotech.jewellery.modules.notification.api.request.TemplateRequest;
import com.finotech.jewellery.modules.notification.api.response.NotificationResponse;
import com.finotech.jewellery.modules.notification.api.response.NotificationResponse.TemplateResponse;
import com.finotech.jewellery.modules.notification.application.service.NotificationInboxService;
import com.finotech.jewellery.modules.notification.application.service.TemplateService;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationChannel;
import com.finotech.jewellery.modules.notification.domain.enums.NotificationStatus;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationRepository;
import com.finotech.jewellery.shared.common.ApiResponse;
import com.finotech.jewellery.shared.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final String VIEW = "hasAuthority('NOTIFICATION_VIEW')";
    private static final String MANAGE = "hasAuthority('NOTIFICATION_MANAGE')";

    private final NotificationRepository notificationRepository;
    private final NotificationInboxService inboxService;
    private final TemplateService templateService;

    @Operation(summary = "Search queued and delivered notifications")
    @GetMapping
    @PreAuthorize(VIEW)
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> search(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) UUID recipientId,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
                notificationRepository.search(status, channel, eventType, recipientId, pageable),
                NotificationResponse::from)));
    }

    @Operation(summary = "The signed-in user's own notifications",
            description = "Scoped to the caller from the security context. Returns messages "
                    + "addressed to them by id plus branch-wide operational broadcasts. Needs no "
                    + "special permission: every authenticated user may read their own mail.")
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> mine(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(
                inboxService.inbox(unreadOnly, pageable), NotificationResponse::from)));
    }

    @Operation(summary = "Unread count for the signed-in user")
    @GetMapping("/mine/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount() {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.unreadCount()));
    }

    @Operation(summary = "Mark one of the caller's notifications read")
    @PostMapping("/mine/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(
                ApiResponse.ok(NotificationResponse.from(inboxService.markRead(id))));
    }

    @Operation(summary = "Mark every unread notification read")
    @PostMapping("/mine/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllRead() {
        return ResponseEntity.ok(ApiResponse.ok(inboxService.markAllRead()));
    }

    @Operation(summary = "List notification templates")
    @GetMapping("/templates")
    @PreAuthorize(VIEW)
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> listTemplates() {
        return ResponseEntity.ok(ApiResponse.ok(templateService.list()));
    }

    @Operation(summary = "Create a notification template",
            description = "Use {{placeholder}} markers to pull values from the event payload.")
    @PostMapping("/templates")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<TemplateResponse>> createTemplate(
            @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(templateService.create(request)));
    }

    @Operation(summary = "Update a notification template")
    @PutMapping("/templates/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<TemplateResponse>> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.update(id, request)));
    }

    @Operation(summary = "Enable or disable a template")
    @PostMapping("/templates/{id}/active")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ApiResponse<TemplateResponse>> setActive(
            @PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.ok(templateService.setActive(id, active)));
    }
}
