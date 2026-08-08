package com.unique.examine.module.runtime.query;

import java.math.BigDecimal;

public final class GeoHashCodec {
    private static final char[] BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();

    private GeoHashCodec() { }

    public static String encode(BigDecimal latitude, BigDecimal longitude, int precision) {
        if (latitude == null || longitude == null || precision < 1 || precision > 12
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("Invalid WGS84 coordinate or geohash precision");
        }
        var lat = latitude.doubleValue();
        var lng = longitude.doubleValue();
        var latMin = -90.0;
        var latMax = 90.0;
        var lngMin = -180.0;
        var lngMax = 180.0;
        var result = new StringBuilder(precision);
        var even = true;
        var bits = 0;
        var value = 0;
        while (result.length() < precision) {
            value <<= 1;
            if (even) {
                var middle = (lngMin + lngMax) / 2.0;
                if (lng >= middle) {
                    value |= 1;
                    lngMin = middle;
                } else {
                    lngMax = middle;
                }
            } else {
                var middle = (latMin + latMax) / 2.0;
                if (lat >= middle) {
                    value |= 1;
                    latMin = middle;
                } else {
                    latMax = middle;
                }
            }
            even = !even;
            bits++;
            if (bits == 5) {
                result.append(BASE32[value]);
                bits = 0;
                value = 0;
            }
        }
        return result.toString();
    }
}
