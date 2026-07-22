package ai.visitorflow.demo.admin.experiment.controller.v1;

import ai.visitorflow.demo.admin.experiment.dto.request.CreateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.UpdateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.response.DeleteExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentSummaryResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.PagedResponse;
import ai.visitorflow.demo.admin.experiment.service.ExperimentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/experiments")
@RequiredArgsConstructor
public class ExperimentController {
  private final ExperimentService experimentService;

  @PostMapping
  public ExperimentResponse create(@Valid @RequestBody CreateExperimentRequest request) {
    return experimentService.create(request);
  }

  @GetMapping
  public PagedResponse<ExperimentSummaryResponse> list(
    @PageableDefault(size = 20) Pageable pageable
  ) {
    return experimentService.list(pageable);
  }

  @GetMapping("/{experimentId}")
  public ExperimentResponse get(@PathVariable Long experimentId) {
    return experimentService.get(experimentId);
  }

  @PutMapping("/{experimentId}")
  public ExperimentResponse update(
    @PathVariable Long experimentId, @Valid @RequestBody UpdateExperimentRequest request
  ) {
    return experimentService.update(experimentId, request);
  }

  @DeleteMapping("/{experimentId}")
  public DeleteExperimentResponse delete(@PathVariable Long experimentId) {
    return experimentService.delete(experimentId);
  }
}
