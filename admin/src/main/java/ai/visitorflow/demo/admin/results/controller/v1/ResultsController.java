package ai.visitorflow.demo.admin.results.controller.v1;

import ai.visitorflow.demo.admin.results.dto.response.ExperimentResultsResponse;
import ai.visitorflow.demo.admin.results.service.ResultsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/experiments/{experimentId}/results")
@RequiredArgsConstructor
public class ResultsController {
  private final ResultsService resultsService;

  @GetMapping
  public ExperimentResultsResponse get(@PathVariable Long experimentId) {
    return resultsService.get(experimentId);
  }
}
