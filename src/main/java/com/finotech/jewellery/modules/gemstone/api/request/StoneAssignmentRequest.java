package com.finotech.jewellery.modules.gemstone.api.request;

import com.finotech.jewellery.modules.gemstone.domain.enums.StoneSettingType;
import com.finotech.jewellery.modules.gemstone.domain.enums.StoneShape;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** One stone row to set on a jewellery item. */
public record StoneAssignmentRequest(@NotNull UUID gemstoneId,
                                     UUID certificateId,
                                     @Positive int stoneCount,
                                     @NotNull @DecimalMin(value = "0.0", inclusive = false)
                                     BigDecimal caratWeight,
                                     StoneShape shape,
                                     @Size(max = 30) String cut,
                                     @Size(max = 30) String colour,
                                     @Size(max = 30) String clarity,
                                     StoneSettingType settingType,
                                     @DecimalMin("0.0") BigDecimal ratePerCarat,
                                     @DecimalMin("0.0") BigDecimal stoneValue,
                                     @DecimalMin("0.0") BigDecimal weightGrams,
                                     @Size(max = 255) String notes) {
}
