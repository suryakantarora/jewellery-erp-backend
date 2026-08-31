package com.finotech.jewellery.modules.inventory.api.response;

import com.finotech.jewellery.modules.gemstone.api.response.StoneResponse;
import java.util.List;

/**
 * The digital jewellery passport (section 8): identity, physical detail,
 * stones and the complete lifecycle history in one document.
 */
public record ItemPassportResponse(JewelleryItemResponse item,
                                   List<StoneResponse> stones,
                                   List<LifecycleEventResponse> history) {
}
