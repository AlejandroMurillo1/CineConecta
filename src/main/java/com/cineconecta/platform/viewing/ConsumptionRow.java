package com.cineconecta.platform.viewing;

import com.cineconecta.platform.catalog.ContentType;

import java.math.BigDecimal;

public record ConsumptionRow(int planId, ContentType type, BigDecimal minutes, long distinctProfiles) {}
