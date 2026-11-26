package com.pathpilot.planner;

public enum TaskPriority {
    HIGH(1),
    MEDIUM(2),
    LOW(3);

    private final int rank;

    TaskPriority(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public static TaskPriority fromRank(int rank) {
        return switch (rank) {
            case 1 -> HIGH;
            case 3 -> LOW;
            default -> MEDIUM;
        };
    }
}
