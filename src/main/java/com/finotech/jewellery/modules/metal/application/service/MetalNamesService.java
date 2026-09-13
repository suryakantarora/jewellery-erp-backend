package com.finotech.jewellery.modules.metal.application.service;

import com.finotech.jewellery.modules.metal.application.MetalNames;
import com.finotech.jewellery.modules.metal.domain.entity.Metal;
import com.finotech.jewellery.modules.metal.domain.entity.Purity;
import com.finotech.jewellery.modules.metal.infrastructure.repository.MetalRepository;
import com.finotech.jewellery.modules.metal.infrastructure.repository.PurityRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serves {@link MetalNames}; depends on the two master repositories alone. */
@Service
@RequiredArgsConstructor
public class MetalNamesService implements MetalNames {

    private final MetalRepository metalRepository;
    private final PurityRepository purityRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> metalNamesFor(Collection<UUID> metalIds) {
        if (metalIds == null || metalIds.isEmpty()) {
            return Map.of();
        }
        return metalRepository.findAllById(metalIds).stream()
                .collect(Collectors.toMap(Metal::getId, Metal::getName));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> purityCodesFor(Collection<UUID> purityIds) {
        if (purityIds == null || purityIds.isEmpty()) {
            return Map.of();
        }
        return purityRepository.findAllById(purityIds).stream()
                .collect(Collectors.toMap(Purity::getId, Purity::getCode));
    }
}
