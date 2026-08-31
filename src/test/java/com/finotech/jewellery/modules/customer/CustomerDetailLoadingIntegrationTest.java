package com.finotech.jewellery.modules.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.finotech.jewellery.IntegrationTestBase;
import com.finotech.jewellery.TestSecurity;
import com.finotech.jewellery.modules.customer.api.request.CustomerAddressRequest;
import com.finotech.jewellery.modules.customer.api.request.CustomerDocumentRequest;
import com.finotech.jewellery.modules.customer.api.request.CustomerRequest;
import com.finotech.jewellery.modules.customer.api.request.PreferenceRequest;
import com.finotech.jewellery.modules.customer.application.service.CustomerService;
import com.finotech.jewellery.modules.customer.domain.enums.KycStatus;
import com.finotech.jewellery.modules.supplier.api.request.SupplierBankAccountRequest;
import com.finotech.jewellery.modules.supplier.api.request.SupplierContactRequest;
import com.finotech.jewellery.modules.supplier.api.request.SupplierRequest;
import com.finotech.jewellery.modules.supplier.application.service.SupplierService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Loading an entity that owns several list collections.
 *
 * <p>These cover a real defect: both Customer and Supplier fetched three list
 * collections in one entity graph, which Hibernate rejects at runtime with
 * MultipleBagFetchException. Every detail view and every "add a child" endpoint
 * on both entities failed with a 500, and nothing caught it because the earlier
 * tests only ever read the entity returned from a create call.
 */
class CustomerDetailLoadingIntegrationTest extends IntegrationTestBase {

    @Autowired private CustomerService customerService;
    @Autowired private SupplierService supplierService;

    @BeforeEach
    void authenticate() {
        TestSecurity.authenticateAsSuperAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurity.clear();
    }

    @Test
    @DisplayName("a customer with addresses, documents and preferences loads in one call")
    void customerWithEveryCollectionLoads() {
        UUID customerId = customerService.create(new CustomerRequest(
                null, null, "Detail Test", null, phone(), null, null, null, null, null, null,
                null, null)).id();

        customerService.addAddress(customerId, new CustomerAddressRequest(
                "HOME", "123 Setthathirath Rd", null, "Vientiane", null, null, "Laos", true));
        customerService.addDocument(customerId, new CustomerDocumentRequest(
                "PASSPORT", "P" + UUID.randomUUID(), null, null, LocalDate.now().minusYears(3),
                LocalDate.now().plusYears(5)));
        customerService.setPreference(customerId, new PreferenceRequest(
                "PREFERRED_METAL", "GOLD"));

        var customer = customerService.get(customerId);

        assertThat(customer.addresses()).hasSize(1);
        assertThat(customer.documents()).hasSize(1);
        assertThat(customer.preferences()).containsEntry("PREFERRED_METAL", "GOLD");
        // Adding an identity document puts KYC into review, never straight to verified.
        assertThat(customer.kycStatus()).isEqualTo(KycStatus.PENDING);
    }

    @Test
    @DisplayName("a supplier with contacts, bank accounts and documents loads in one call")
    void supplierWithEveryCollectionLoads() {
        String code = "SUP" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID supplierId = supplierService.create(new SupplierRequest(
                code, "Detail Supplier", null, null, null, null, null, null, null, null,
                "LAK", 30, null, null)).id();

        supplierService.addContact(supplierId, new SupplierContactRequest(
                "Somsak", "Director", "+8562000000000", null, true));
        supplierService.addBankAccount(supplierId, new SupplierBankAccountRequest(
                "BCEL", "Detail Supplier", "1234567890", null, null, "LAK", true));

        var supplier = supplierService.get(supplierId);

        assertThat(supplier.contacts()).hasSize(1);
        assertThat(supplier.bankAccounts()).hasSize(1);
        assertThat(supplier.bankAccounts().get(0).accountNumber()).isEqualTo("1234567890");
    }

    @Test
    @DisplayName("marking one address default clears the previous one")
    void onlyOneDefaultAddress() {
        UUID customerId = customerService.create(new CustomerRequest(
                null, null, "Address Test", null, phone(), null, null, null, null, null, null,
                null, null)).id();

        customerService.addAddress(customerId, new CustomerAddressRequest(
                "HOME", "First address", null, null, null, null, null, true));
        var customer = customerService.addAddress(customerId, new CustomerAddressRequest(
                "OFFICE", "Second address", null, null, null, null, null, true));

        assertThat(customer.addresses()).hasSize(2);
        assertThat(customer.addresses().stream().filter(a -> a.defaultAddress()).count())
                .isEqualTo(1);
    }

    private String phone() {
        return "+856" + (System.nanoTime() % 1_000_000_000L);
    }
}
