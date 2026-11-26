package com.pathpilot.planner;

public enum TaskCategory {
    JAVA_LEARNING("Study", "LEARNING"),
    SPRING_BOOT("Study", "LEARNING"),
    SQL_MYSQL("Study", "LEARNING"),
    REACT_FRONTEND("Study", "LEARNING"),
    CODING_PRACTICE("Practice", "PRACTICE"),
    PROJECT_DEVELOPMENT("Project", "PROJECTS"),
    JOB_APPLICATIONS("Job Search", "APPLICATIONS"),
    INTERVIEW_PREP("Job Search", "APPLICATIONS"),
    MEALS_PERSONAL("Health", "OTHER"),
    OTHER("Personal", "OTHER");

    private final String colorGroup;
    private final String dashboardBucket;

    TaskCategory(String colorGroup, String dashboardBucket) {
        this.colorGroup = colorGroup;
        this.dashboardBucket = dashboardBucket;
    }

    public String getColorGroup() {
        return colorGroup;
    }

    public String getDashboardBucket() {
        return dashboardBucket;
    }
}
