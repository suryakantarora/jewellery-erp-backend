package com.finotech.jewellery.modules.storefront.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Tenant rows and content documents. JSON crosses this boundary as text. */
@Repository
@RequiredArgsConstructor
public class TenantStore {

    private final JdbcTemplate jdbc;

    public record TenantRow(UUID companyId, String tenantKey, String configJson, boolean active) {
    }

    public Optional<TenantRow> byKey(String tenantKey) {
        return jdbc.query("""
                SELECT company_id, tenant_key, config::text AS config, active
                FROM storefront.tenant WHERE lower(tenant_key) = lower(?)
                """, (rs, n) -> new TenantRow(rs.getObject("company_id", UUID.class),
                rs.getString("tenant_key"), rs.getString("config"), rs.getBoolean("active")),
                tenantKey).stream().findFirst();
    }

    public Optional<TenantRow> byCompany(UUID companyId) {
        return jdbc.query("""
                SELECT company_id, tenant_key, config::text AS config, active
                FROM storefront.tenant WHERE company_id = ?
                """, (rs, n) -> new TenantRow(rs.getObject("company_id", UUID.class),
                rs.getString("tenant_key"), rs.getString("config"), rs.getBoolean("active")),
                companyId).stream().findFirst();
    }

    public boolean keyTakenByOther(String tenantKey, UUID companyId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM storefront.tenant
                WHERE lower(tenant_key) = lower(?) AND company_id <> ?
                """, Integer.class, tenantKey, companyId);
        return count != null && count > 0;
    }

    public void upsert(UUID companyId, String tenantKey, String configJson, boolean active) {
        jdbc.update("""
                INSERT INTO storefront.tenant (company_id, tenant_key, config, active)
                VALUES (?, ?, ?::jsonb, ?)
                ON CONFLICT (company_id) DO UPDATE
                   SET tenant_key = EXCLUDED.tenant_key, config = EXCLUDED.config,
                       active = EXCLUDED.active, updated_at = now()
                """, companyId, tenantKey, configJson, active);
    }

    public boolean anyTenant() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM storefront.tenant", Integer.class);
        return count != null && count > 0;
    }

    /** Companies on the platform, oldest first; only used by the dev bootstrap. */
    public List<CompanyRow> companies() {
        return jdbc.query("""
                SELECT id, name, base_currency, phone, email, address_line
                FROM organization.company ORDER BY created_at, id LIMIT 2
                """, (rs, n) -> new CompanyRow(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("base_currency"), rs.getString("phone"), rs.getString("email"),
                rs.getString("address_line")));
    }

    public Optional<CompanyRow> company(UUID companyId) {
        return jdbc.query("""
                SELECT id, name, base_currency, phone, email, address_line
                FROM organization.company WHERE id = ?
                """, (rs, n) -> new CompanyRow(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("base_currency"), rs.getString("phone"), rs.getString("email"),
                rs.getString("address_line")), companyId).stream().findFirst();
    }

    public record CompanyRow(UUID id, String name, String currency, String phone, String email,
                             String address) {
    }

    public Optional<String> content(UUID companyId, String kind) {
        return jdbc.query("""
                SELECT payload::text FROM storefront.content WHERE company_id = ? AND kind = ?
                """, (rs, n) -> rs.getString(1), companyId, kind).stream().findFirst();
    }

    public void putContent(UUID companyId, String kind, String payloadJson, String updatedBy) {
        jdbc.update("""
                INSERT INTO storefront.content (company_id, kind, payload, updated_by)
                VALUES (?, ?, ?::jsonb, ?)
                ON CONFLICT (company_id, kind) DO UPDATE
                   SET payload = EXCLUDED.payload, updated_at = now(),
                       updated_by = EXCLUDED.updated_by
                """, companyId, kind, payloadJson, updatedBy);
    }
}
