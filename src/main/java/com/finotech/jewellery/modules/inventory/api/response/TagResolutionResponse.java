package com.finotech.jewellery.modules.inventory.api.response;

import java.util.List;

/**
 * Which scanned tags matched an item and which did not. Unresolved tags are
 * reported rather than dropped: a tag nobody recognises is exactly what a
 * stock check exists to find.
 */
public record TagResolutionResponse(List<ResolvedTag> resolved, List<String> unresolved) {

    public record ResolvedTag(String tag, JewelleryItemResponse item) {
    }
}
