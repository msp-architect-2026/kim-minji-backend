package com.minjikim.codecobainbackend.controller;

import com.minjikim.codecobainbackend.dto.request.SimulationRequest;
import com.minjikim.codecobainbackend.dto.response.SimulationResponse;
import com.minjikim.codecobainbackend.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/dev/simulate")
    public SimulationResponse simulate(@RequestBody(required = false) SimulationRequest request) {
        return simulationService.simulate(request == null ? new SimulationRequest(null, null, null, null, null) : request);
    }
}
