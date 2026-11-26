package com.pathpilot.planner;

public enum GoalCategory {
    TECHNOLOGIES_TO_LEARN("Technologies to learn", "Study"),
    CODING_PROBLEMS("Coding problems", "Practice"),
    PROJECTS("Projects", "Project"),
    JOB_APPLICATIONS("Job applications", "Job Search"),
    INTERVIEW_PREP("Interview preparation", "Job Search"),
    OTHER("Other", "Personal");

    private final String label;
    private final String colorGroup;

    GoalCategory(String label, String colorGroup) {
        this.label = label;
        this.colorGroup = colorGroup;
    }

    public String getLabel() {
        return label;
    }

    public String getColorGroup() {
        return colorGroup;
    }
}
