package com.minjikim.codecobainbackend.dto.response;

import java.util.List;

public record PChartResponse(double centerLine, List<Point> points) {
    public record Point(String date, long inspected, long defects, double rate, double ucl, double lcl, List<String> violations) {
    }
}
