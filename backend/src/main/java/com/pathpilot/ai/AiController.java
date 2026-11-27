package com.pathpilot.ai;

import com.pathpilot.ai.dto.AiDtos.AgentChatRequest;
import com.pathpilot.ai.dto.AiDtos.AgentChatResponse;
import com.pathpilot.ai.dto.AiDtos.AiProposalDto;
import com.pathpilot.ai.dto.AiDtos.CreateBatchTaskProposalsRequest;
import com.pathpilot.ai.dto.AiDtos.CreateTaskProposalRequest;
import com.pathpilot.ai.dto.AiDtos.DailyRecommendationResponse;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisRequest;
import com.pathpilot.ai.dto.AiDtos.JdAnalysisResponse;
import com.pathpilot.ai.dto.AiDtos.MonthlySummaryResponse;
import com.pathpilot.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final DailyRecommendationService recommendationService;
    private final JdAnalysisService jdAnalysisService;
    private final MonthlySummaryService monthlySummaryService;
    private final AiProposalService proposalService;
    private final AgentService agentService;

    public AiController(
            DailyRecommendationService recommendationService,
            JdAnalysisService jdAnalysisService,
            MonthlySummaryService monthlySummaryService,
            AiProposalService proposalService,
            AgentService agentService
    ) {
        this.recommendationService = recommendationService;
        this.jdAnalysisService = jdAnalysisService;
        this.monthlySummaryService = monthlySummaryService;
        this.proposalService = proposalService;
        this.agentService = agentService;
    }

    @GetMapping("/recommendation")
    public DailyRecommendationResponse getDailyRecommendation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return recommendationService.getDailyRecommendation(CurrentUser.id(), date, refresh);
    }

    @PostMapping("/jd-analysis")
    public JdAnalysisResponse analyzeJobDescription(@Valid @RequestBody JdAnalysisRequest request) {
        return jdAnalysisService.analyze(CurrentUser.id(), request);
    }

    @GetMapping("/jd-analysis/{jobApplicationId}")
    public JdAnalysisResponse getSavedJdAnalysis(@PathVariable Long jobApplicationId) {
        return jdAnalysisService.getSavedAnalysis(CurrentUser.id(), jobApplicationId);
    }

    @PostMapping("/monthly-summary/{yearMonth}")
    public MonthlySummaryResponse generateMonthlySummary(
            @PathVariable String yearMonth,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return monthlySummaryService.generateMonthlySummary(CurrentUser.id(), yearMonth, refresh);
    }

    @GetMapping("/proposals")
    public List<AiProposalDto> listProposals(@RequestParam(required = false) String status) {
        return proposalService.listProposals(CurrentUser.id(), status);
    }

    @PostMapping("/proposals/task")
    @ResponseStatus(HttpStatus.CREATED)
    public AiProposalDto createTaskProposal(@Valid @RequestBody CreateTaskProposalRequest request) {
        return proposalService.createTaskProposal(CurrentUser.id(), request);
    }

    @PostMapping("/proposals/tasks/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<AiProposalDto> createBatchTaskProposals(@Valid @RequestBody CreateBatchTaskProposalsRequest request) {
        return proposalService.createBatchTaskProposals(CurrentUser.id(), request);
    }

    @PostMapping("/proposals/{id}/approve")
    public AiProposalDto approveProposal(@PathVariable Long id) {
        return proposalService.approveProposal(CurrentUser.id(), id);
    }

    @PostMapping("/proposals/{id}/reject")
    public AiProposalDto rejectProposal(@PathVariable Long id) {
        return proposalService.rejectProposal(CurrentUser.id(), id);
    }

    @PostMapping("/agent")
    public AgentChatResponse runAgent(@Valid @RequestBody AgentChatRequest request) {
        return agentService.runAgent(CurrentUser.id(), request);
    }
}
