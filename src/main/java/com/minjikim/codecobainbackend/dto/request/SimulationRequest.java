package com.minjikim.codecobainbackend.dto.request;

public record SimulationRequest(Integer lots, Long seed, String excursionEquipment, String excursionDefect) {
}
