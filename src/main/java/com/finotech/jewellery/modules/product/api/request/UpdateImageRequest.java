package com.finotech.jewellery.modules.product.api.request;

/**
 * Changes how a linked image is shown: promotes it to primary (demoting the
 * previous one), demotes it, or moves it in the gallery order. Either field may
 * be omitted; a null leaves that aspect alone.
 */
public record UpdateImageRequest(Boolean primaryImage, Integer displayOrder) {
}
