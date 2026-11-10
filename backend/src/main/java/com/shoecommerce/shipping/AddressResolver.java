package com.shoecommerce.shipping;

import java.util.List;

public interface AddressResolver {
    List<Reference> provinces();
    List<Reference> districts(String provinceCode);
    List<Reference> wards(String provinceCode, String districtCode);
    Location resolveLocation(String provinceCode, String districtCode, String wardCode);

    default ResolvedAddress resolve(String provinceCode, String districtCode, String wardCode, String addressLine) {
        Location location = resolveLocation(provinceCode, districtCode, wardCode);
        String line = addressLine == null ? "" : addressLine.trim();
        if (line.isEmpty() || line.length() > 500) {
            throw new com.shoecommerce.platform.api.InvalidRequestException(
                    "DELIVERY_ADDRESS_INVALID", "Enter a valid street and house address.", "addressLine", "INVALID");
        }
        String canonical = line + ", " + location.wardLabel() + ", " + location.districtLabel() + ", " + location.provinceLabel();
        if (canonical.length() > 500) {
            throw new com.shoecommerce.platform.api.InvalidRequestException(
                    "DELIVERY_ADDRESS_INVALID", "Enter a shorter street and house address.", "addressLine", "INVALID");
        }
        return new ResolvedAddress(location.provinceCode(), location.provinceLabel(), location.districtCode(),
                location.districtLabel(), location.wardCode(), location.wardLabel(), line, canonical, location.datasetVersion());
    }

    record Reference(String code, String label) { }
    record Location(String provinceCode, String provinceLabel, String districtCode, String districtLabel,
            String wardCode, String wardLabel, String datasetVersion) { }
    record ResolvedAddress(String provinceCode, String provinceLabel, String districtCode, String districtLabel,
            String wardCode, String wardLabel, String addressLine, String canonicalAddress, String datasetVersion) { }
}
