package com.finotech.jewellery.modules.gemstone.api.response;

import com.finotech.jewellery.modules.gemstone.domain.entity.JewelleryStone;
import com.finotech.jewellery.modules.gemstone.domain.enums.StoneSettingType;
import com.finotech.jewellery.modules.gemstone.domain.enums.StoneShape;
import java.math.BigDecimal;
import java.util.UUID;

public record StoneResponse(UUID id, UUID jewelleryItemId, UUID gemstoneId, String gemstoneName,
                            UUID certificateId, String certificateNumber, int stoneCount,
                            BigDecimal caratWeight, StoneShape shape, String cut, String colour,
                            String clarity, StoneSettingType settingType, BigDecimal ratePerCarat,
                            BigDecimal stoneValue, BigDecimal weightGrams, String notes) {

    public static StoneResponse from(JewelleryStone s) {
        return new StoneResponse(s.getId(), s.getJewelleryItemId(), s.getGemstone().getId(),
                s.getGemstone().getName(),
                s.getCertificate() == null ? null : s.getCertificate().getId(),
                s.getCertificate() == null ? null : s.getCertificate().getCertificateNumber(),
                s.getStoneCount(), s.getCaratWeight(), s.getShape(), s.getCut(), s.getColour(),
                s.getClarity(), s.getSettingType(), s.getRatePerCarat(), s.getStoneValue(),
                s.getWeightGrams(), s.getNotes());
    }
}
