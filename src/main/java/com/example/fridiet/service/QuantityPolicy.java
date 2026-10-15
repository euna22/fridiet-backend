package com.example.fridiet.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class QuantityPolicy {
    private QuantityPolicy() {}
    private static final Properties UNITS = new Properties();
    static {
        try (var stream = QuantityPolicy.class.getResourceAsStream("/quantity_units.properties")) {
            if (stream == null) throw new IOException("Missing shared quantity definitions");
            UNITS.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public record Amount(BigDecimal quantity, String unit) {}
    public static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    public static BigDecimal decimal(Double n) {
        if (n == null || !Double.isFinite(n) || n < 0 || n >= 1e12) throw invalid("올바른 수량을 입력해주세요.");
        return BigDecimal.valueOf(n).setScale(6, RoundingMode.HALF_UP);
    }
    public static Amount normalize(Double n, String unit) {
        String definition = UNITS.getProperty(unit == null ? "" : unit.trim().toLowerCase(Locale.ROOT));
        if (definition == null) throw invalid("수량 단위는 g, 개, mL로 확인해주세요.");
        String[] spec = definition.split(",");
        BigDecimal value = decimal(n).multiply(new BigDecimal(spec[1])).setScale(6, RoundingMode.HALF_UP);
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("1000000000000")) >= 0)
            throw invalid("수량은 0보다 크고 1조 미만이어야 합니다.");
        return new Amount(value, spec[0]);
    }
    public static boolean canonical(String unit) { return Set.of("g", "개", "mL").contains(unit == null ? "" : unit); }
}
