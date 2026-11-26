package com.pathpilot.progress;

import com.pathpilot.coding.CodingActivity;
import com.pathpilot.coding.CodingActivityRepository;
import com.pathpilot.github.GithubEvent;
import com.pathpilot.github.GithubEventRepository;
import com.pathpilot.job.JobApplication;
import com.pathpilot.job.JobApplicationRepository;
import com.pathpilot.planner.Task;
import com.pathpilot.planner.TaskRepository;
import com.pathpilot.project.Project;
import com.pathpilot.project.ProjectMilestone;
import com.pathpilot.project.ProjectMilestoneRepository;
import com.pathpilot.project.ProjectRepository;
import com.pathpilot.user.User;
import com.pathpilot.user.UserProfile;
import com.pathpilot.user.UserProfileRepository;
import com.pathpilot.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Seeds fictional DEMO-labelled sample records ONLY when the "demo" Spring profile is active.
 * Never executes under local/default/prod profiles.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_EMAIL = "demo@pathpilot.local";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TaskRepository taskRepository;
    private final CodingActivityRepository codingActivityRepository;
    private final GithubEventRepository githubEventRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMilestoneRepository projectMilestoneRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            UserRepository userRepository,
            UserProfileRepository userProfileRepository,
            TaskRepository taskRepository,
            CodingActivityRepository codingActivityRepository,
            GithubEventRepository githubEventRepository,
            ProjectRepository projectRepository,
            ProjectMilestoneRepository projectMilestoneRepository,
            JobApplicationRepository jobApplicationRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.taskRepository = taskRepository;
        this.codingActivityRepository = codingActivityRepository;
        this.githubEventRepository = githubEventRepository;
        this.projectRepository = projectRepository;
        this.projectMilestoneRepository = projectMilestoneRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(DEMO_EMAIL)) {
            return;
        }

        User demoUser = new User();
        demoUser.setEmail(DEMO_EMAIL);
        demoUser.setPhone("9000000000");
        demoUser.setPasswordHash(passwordEncoder.encode("DemoUser123"));
        demoUser.setRole("USER");
        demoUser.setActive(true);
        User savedUser = userRepository.save(demoUser);
        Long userId = savedUser.getId();

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setFullName("Aarav Sharma (DEMO)");
        profile.setTargetRole("Fresher");
        profile.setTimeZone(UserProfile.DEFAULT_TIME_ZONE);
        profile.setGithubUrl("https://github.com/demo-pathpilot");
        profile.setGithubUsername("demo-pathpilot");
        profile.setLinkedinUrl("https://linkedin.com/in/demo-pathpilot");
        profile.setCodingProfileUrl("https://leetcode.com/demo-pathpilot");
        profile.setLeetcodeUrl("https://leetcode.com/demo-pathpilot");
        userProfileRepository.save(profile);

        LocalDate today = LocalDate.now(ZoneId.of(UserProfile.DEFAULT_TIME_ZONE));

        // Today's tasks: 4 completed / 7 planned (1 RESCHEDULED excluded) = 57.1%
        seedTask(userId, "Revise Spring Security JWT filter flow (DEMO)", today, "COMPLETED", "LEARNING", 1, 45);
        seedTask(userId, "Read Java Streams groupingBy notes (DEMO)", today, "PENDING", "LEARNING", 2, 30);
        seedTask(userId, "Solve Two Sum & Sliding Window on LeetCode (DEMO)", today, "COMPLETED", "PRACTICE", 1, 45);
        seedTask(userId, "Dry-run Binary Search edge cases (DEMO)", today, "COMPLETED", "PRACTICE", 2, 30);
        seedTask(userId, "Finish Dashboard ProgressCalculator tests (DEMO)", today, "COMPLETED", "PROJECTS", 1, 60);
        seedTask(userId, "Refactor project README screenshots (DEMO)", today, "SKIPPED", "PROJECTS", 3, 25);
        seedTask(userId, "Tailor resume for backend fresher roles (DEMO)", today, "PENDING", "APPLICATIONS", 2, 35);
        seedTask(userId, "Follow up on recruiter email (DEMO)", today, "RESCHEDULED", "APPLICATIONS", 3, 15);

        // Earlier tasks in the same month (if today > day 2) for realistic monthly bar
        if (today.getDayOfMonth() > 2) {
            seedTask(userId, "Design MySQL ERD tables (DEMO)", today.minusDays(2), "COMPLETED", "PROJECTS", 1, 50);
            seedTask(userId, "Practice SQL joins (DEMO)", today.minusDays(1), "COMPLETED", "PRACTICE", 2, 40);
        }

        // 5 consecutive days of coding practice ending today => streak = 5 days
        for (int i = 0; i < 5; i++) {
            CodingActivity activity = new CodingActivity();
            activity.setUserId(userId);
            activity.setActivityDate(today.minusDays(i));
            activity.setPlatform("LeetCode");
            activity.setProblemsSolved(i == 0 ? 2 : 1);
            activity.setMinutesSpent(45);
            activity.setProblemTitle("DEMO Problem #" + (i + 1));
            activity.setTopic("Arrays");
            activity.setDifficulty("Easy");
            activity.setResult("Solved");
            activity.setDataSource("DEMO");
            codingActivityRepository.save(activity);
        }

        // Sample GitHub events over the last 10 weeks
        List<Integer> offsets = List.of(0, 1, 2, 4, 7, 9, 12, 15, 19, 24, 30, 38, 45, 55, 68);
        for (int idx = 0; idx < offsets.size(); idx++) {
            int offset = offsets.get(idx);
            GithubEvent event = new GithubEvent();
            event.setUserId(userId);
            event.setExternalEventId("demo-evt-" + idx);
            event.setEventType("PushEvent");
            event.setRepoName("demo-pathpilot/career-tracker");
            event.setEventDate(today.minusDays(offset));
            event.setOccurredAtUtc(Instant.now());
            event.setCommitCount(1 + (idx % 3));
            event.setPayloadSummary("Pushed " + (1 + (idx % 3)) + " commit(s) to main");
            event.setHtmlUrl("https://github.com/demo-pathpilot/career-tracker");
            event.setDataSource("DEMO");
            githubEventRepository.save(event);
        }

        // Demo project with 3 of 4 milestones completed => 75%
        Project project = new Project();
        project.setUserId(userId);
        project.setTitle("Career Tracker API (DEMO)");
        project.setStatus("IN_PROGRESS");
        project.setStartDate(today.minusDays(20));
        project.setTargetDate(today.plusDays(15));
        project.setDataSource("DEMO");
        Project savedProject = projectRepository.save(project);

        seedMilestone(userId, savedProject.getId(), "Schema & Flyway setup", "COMPLETED", 1);
        seedMilestone(userId, savedProject.getId(), "BCrypt + JWT auth", "COMPLETED", 2);
        seedMilestone(userId, savedProject.getId(), "ProgressCalculator engine", "COMPLETED", 3);
        seedMilestone(userId, savedProject.getId(), "Deployment checklist", "PENDING", 4);

        // 3 recent fictional applications
        seedApplication(userId, "Northstar Labs (DEMO)", "Junior Java Developer", "INTERVIEW", today.minusDays(2), "Bengaluru");
        seedApplication(userId, "BluePeak Systems (DEMO)", "Associate Backend Engineer", "APPLIED", today.minusDays(5), "Hyderabad");
        seedApplication(userId, "CloudHarbor Tech (DEMO)", "Java Full Stack Trainee", "ASSESSMENT", today.minusDays(9), "Remote");

        log.info("Demo profile active: seeded sample DEMO records for {}", DEMO_EMAIL);
    }

    private void seedTask(
            Long userId,
            String title,
            LocalDate date,
            String status,
            String category,
            int priority,
            int durationMinutes
    ) {
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(title);
        task.setTaskDate(date);
        task.setStatus(status);
        task.setCategory(category);
        task.setPriority(priority);
        task.setDurationMinutes(durationMinutes);
        task.setDataSource("DEMO");
        taskRepository.save(task);
    }

    private void seedMilestone(Long userId, Long projectId, String title, String status, int sortOrder) {
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.setUserId(userId);
        milestone.setProjectId(projectId);
        milestone.setTitle(title);
        milestone.setStatus(status);
        milestone.setSortOrder(sortOrder);
        milestone.setDataSource("DEMO");
        projectMilestoneRepository.save(milestone);
    }

    private void seedApplication(
            Long userId,
            String company,
            String role,
            String status,
            LocalDate appliedDate,
            String location
    ) {
        JobApplication app = new JobApplication();
        app.setUserId(userId);
        app.setCompanyName(company);
        app.setRoleTitle(role);
        app.setStatus(status);
        app.setAppliedDate(appliedDate);
        app.setLocation(location);
        app.setDataSource("DEMO");
        jobApplicationRepository.save(app);
    }
}
