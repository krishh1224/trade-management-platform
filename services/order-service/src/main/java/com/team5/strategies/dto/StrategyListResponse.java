package com.team5.strategies.dto;

import java.util.List;

public record StrategyListResponse(int maxStrategies, List<StrategyResponse> strategies) {
}
