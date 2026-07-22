package ai.visitorflow.demo.admin.experiment.service;

import ai.visitorflow.demo.admin.experiment.dto.request.CreateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.UpdateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.response.DeleteExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentSummaryResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface ExperimentService {
  ExperimentResponse create(CreateExperimentRequest request);
  PagedResponse<ExperimentSummaryResponse> list(Pageable pageable);
  ExperimentResponse get(Long experimentId);
  ExperimentResponse update(Long experimentId, UpdateExperimentRequest request);
  DeleteExperimentResponse delete(Long experimentId);
}
