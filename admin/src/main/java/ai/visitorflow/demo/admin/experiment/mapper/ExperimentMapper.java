package ai.visitorflow.demo.admin.experiment.mapper;

import ai.visitorflow.demo.admin.experiment.dto.request.CreateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.UpdateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.VariantRequest;
import ai.visitorflow.demo.admin.experiment.dto.response.DeleteExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentSummaryResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.PagedResponse;
import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import java.util.List;
import org.springframework.data.domain.Page;

public interface ExperimentMapper {
  ExperimentEntity toEntity(CreateExperimentRequest request);
  void update(UpdateExperimentRequest request, ExperimentEntity experiment);
  VariantEntity toVariant(VariantRequest request, Long experimentId);
  void update(VariantRequest request, VariantEntity variant);
  ExperimentResponse toResponse(
    ExperimentEntity experiment, List<VariantEntity> variants, String analyticsWarning
  );
  PagedResponse<ExperimentSummaryResponse> toPage(Page<ExperimentEntity> experiments);
  DeleteExperimentResponse toDeleteResponse(Long experimentId);
}
