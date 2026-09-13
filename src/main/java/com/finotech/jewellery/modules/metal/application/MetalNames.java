package com.finotech.jewellery.modules.metal.application;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Batch metal and purity labels for list screens in other modules.
 *
 * <p>{@link MetalRateProvider} values metal; this port only names it. Unknown
 * ids are absent from the result.
 */
public interface MetalNames {

    Map<UUID, String> metalNamesFor(Collection<UUID> metalIds);

    /** Purity codes such as {@code 22K}, which is how staff refer to purity. */
    Map<UUID, String> purityCodesFor(Collection<UUID> purityIds);
}
