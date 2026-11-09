package com.minjikim.codecobainbackend.dto.response;

public record SimulationResponse(int lots, int wafers, int excursionLots, String excursionEquipment, String excursionDefect) {
}
