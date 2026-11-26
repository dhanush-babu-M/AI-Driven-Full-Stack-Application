package com.pathpilot.github;

import com.pathpilot.github.dto.GitHubDtos.GitHubConnectRequest;
import com.pathpilot.github.dto.GitHubDtos.GitHubEventDto;
import com.pathpilot.github.dto.GitHubDtos.GitHubRepoDto;
import com.pathpilot.github.dto.GitHubDtos.GitHubStatusResponse;
import com.pathpilot.github.dto.GitHubDtos.GitHubSyncResponse;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;

    public GitHubController(GitHubService gitHubService) {
        this.gitHubService = gitHubService;
    }

    @PostMapping("/connect")
    public GitHubStatusResponse connect(@Valid @RequestBody GitHubConnectRequest request) {
        return gitHubService.connect(CurrentUser.id(), request);
    }

    @GetMapping("/status")
    public GitHubStatusResponse status() {
        return gitHubService.getStatus(CurrentUser.id());
    }

    @PostMapping("/sync")
    public GitHubSyncResponse sync() {
        return gitHubService.syncForUser(CurrentUser.id());
    }

    @GetMapping("/repos")
    public List<GitHubRepoDto> repos() {
        return gitHubService.getRepos(CurrentUser.id());
    }

    @GetMapping("/events")
    public List<GitHubEventDto> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return gitHubService.listEvents(CurrentUser.id(), from, to);
    }
}
