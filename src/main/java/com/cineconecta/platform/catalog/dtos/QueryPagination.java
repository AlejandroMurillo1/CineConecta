package com.cineconecta.platform.catalog.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;

public record QueryPagination(
        @PositiveOrZero int skip,
        @Min(value = 1) int limit
) {
}
