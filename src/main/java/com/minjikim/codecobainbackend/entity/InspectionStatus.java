package com.minjikim.codecobainbackend.entity;

public enum InspectionStatus {
    RECEIVED,
    UPLOADED,
    INFERRING,
    DONE,
    NEED_REVIEW,
    FAILED;

    public boolean canTransitionTo(InspectionStatus next) {
        return switch (this) {
            case RECEIVED -> next == UPLOADED || next == FAILED;
            case UPLOADED -> next == INFERRING || next == FAILED;
            case INFERRING -> next == DONE || next == NEED_REVIEW || next == FAILED;
            case FAILED -> next == INFERRING;
            case NEED_REVIEW -> next == DONE;
            case DONE -> false;
        };
    }
}
