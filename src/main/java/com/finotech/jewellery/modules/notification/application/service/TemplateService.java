package com.finotech.jewellery.modules.notification.application.service;

import com.finotech.jewellery.modules.notification.api.request.TemplateRequest;
import com.finotech.jewellery.modules.notification.api.response.NotificationResponse.TemplateResponse;
import com.finotech.jewellery.modules.notification.domain.entity.NotificationTemplate;
import com.finotech.jewellery.modules.notification.infrastructure.repository.NotificationTemplateRepository;
import com.finotech.jewellery.shared.audit.AuditService;
import com.finotech.jewellery.shared.exception.ConflictException;
import com.finotech.jewellery.shared.exception.NotFoundException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TemplateService {

    private final NotificationTemplateRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return repository.findAllByOrderByEventTypeAscChannelAsc().stream()
                .map(TemplateResponse::from).toList();
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("Template code already exists: " + request.code());
        }
        NotificationTemplate template = new NotificationTemplate();
        template.setCode(request.code().trim().toUpperCase());
        apply(template, request);
        NotificationTemplate saved = repository.save(template);
        auditService.record("NOTIFICATION_TEMPLATE_CREATED", "NotificationTemplate", saved.getId(),
                null, TemplateResponse.from(saved));
        return TemplateResponse.from(saved);
    }

    @Transactional
    public TemplateResponse update(UUID id, TemplateRequest request) {
        NotificationTemplate template = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("NotificationTemplate", id));
        TemplateResponse before = TemplateResponse.from(template);
        apply(template, request);
        auditService.record("NOTIFICATION_TEMPLATE_UPDATED", "NotificationTemplate", id, before,
                TemplateResponse.from(template));
        return TemplateResponse.from(template);
    }

    @Transactional
    public TemplateResponse setActive(UUID id, boolean active) {
        NotificationTemplate template = repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("NotificationTemplate", id));
        template.setActive(active);
        return TemplateResponse.from(template);
    }

    private void apply(NotificationTemplate template, TemplateRequest request) {
        template.setEventType(request.eventType().trim().toUpperCase());
        template.setChannel(request.channel());
        template.setSubject(request.subject());
        template.setBody(request.body());
        if (StringUtils.hasText(request.locale())) {
            template.setLocale(request.locale());
        }
    }
}
