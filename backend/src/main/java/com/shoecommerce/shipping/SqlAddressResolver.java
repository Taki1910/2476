package com.shoecommerce.shipping;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shoecommerce.platform.api.InvalidRequestException;

@Service
class SqlAddressResolver implements AddressResolver {
    private final JdbcTemplate jdbc;

    SqlAddressResolver(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional(readOnly = true)
    public List<Reference> provinces() {
        return jdbc.query("SELECT code,display_label FROM geo_province WHERE enabled=1 ORDER BY display_label",
                (rs, row) -> new Reference(rs.getString(1), rs.getString(2)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reference> districts(String provinceCode) {
        province(provinceCode);
        return jdbc.query("SELECT code,display_label FROM geo_district WHERE province_code=? AND enabled=1 ORDER BY display_label",
                (rs, row) -> new Reference(rs.getString(1), rs.getString(2)), normalized(provinceCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reference> wards(String provinceCode, String districtCode) {
        district(provinceCode, districtCode);
        return jdbc.query("SELECT code,display_label FROM geo_ward WHERE province_code=? AND district_code=? AND enabled=1 ORDER BY display_label",
                (rs, row) -> new Reference(rs.getString(1), rs.getString(2)), normalized(provinceCode), normalized(districtCode));
    }

    @Override
    @Transactional(readOnly = true)
    public Location resolveLocation(String provinceCode, String districtCode, String wardCode) {
        Reference province = province(provinceCode);
        Reference district = district(province.code(), districtCode);
        List<Location> rows = jdbc.query("""
                SELECT w.code,w.display_label,w.dataset_version
                FROM geo_ward w
                WHERE w.province_code=? AND w.district_code=? AND w.code=? AND w.enabled=1
                """, (rs, row) -> new Location(province.code(), province.label(), district.code(), district.label(),
                        rs.getString(1), rs.getString(2), rs.getString(3)),
                province.code(), district.code(), normalized(wardCode));
        if (rows.isEmpty()) throw unknown("ADDRESS_WARD_UNKNOWN", "Ward was not found.", "wardCode", "UNKNOWN");
        return rows.getFirst();
    }

    private Reference province(String code) {
        List<Reference> rows = jdbc.query("SELECT code,display_label FROM geo_province WHERE code=? AND enabled=1",
                (rs, row) -> new Reference(rs.getString(1), rs.getString(2)), normalized(code));
        if (rows.isEmpty()) throw unknown("ADDRESS_PROVINCE_UNKNOWN", "Province or city was not found.", "provinceCode", "UNKNOWN");
        return rows.getFirst();
    }

    private Reference district(String provinceCode, String districtCode) {
        province(provinceCode);
        List<Reference> rows = jdbc.query("SELECT code,display_label FROM geo_district WHERE province_code=? AND code=? AND enabled=1",
                (rs, row) -> new Reference(rs.getString(1), rs.getString(2)), normalized(provinceCode), normalized(districtCode));
        if (rows.isEmpty()) throw unknown("ADDRESS_DISTRICT_UNKNOWN", "District was not found in this province.", "districtCode", "UNKNOWN");
        return rows.getFirst();
    }

    private static String normalized(String code) { return code == null ? "" : code.trim(); }
    private static InvalidRequestException unknown(String code, String message, String field, String fieldCode) {
        return new InvalidRequestException(code, message, field, fieldCode);
    }
}
