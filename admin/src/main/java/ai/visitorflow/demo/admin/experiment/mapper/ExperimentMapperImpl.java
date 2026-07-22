package ai.visitorflow.demo.admin.experiment.mapper;

import ai.visitorflow.demo.admin.experiment.dto.request.CreateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.UpdateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.VariantRequest;
import ai.visitorflow.demo.admin.experiment.dto.response.DeleteExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentSummaryResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.PagedResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.VariantResponse;
import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.experiment.model.AssignmentStrategy;
import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ExperimentMapperImpl implements ExperimentMapper {
  private final ModelMapper modelMapper;

  @Override
  public ExperimentEntity toEntity(CreateExperimentRequest request) {
    ExperimentEntity entity = ExperimentEntity.builder().tenantId(RequestContextHolder.tenantId()).build();
    modelMapper.map(request, entity);
    return entity;
  }

  @Override
  public void update(UpdateExperimentRequest request, ExperimentEntity experiment) {
    modelMapper.map(request, experiment);
  }

  @Override
  public VariantEntity toVariant(VariantRequest request, Long experimentId) {
    VariantEntity variant = VariantEntity.builder()
      .tenantId(RequestContextHolder.tenantId())
      .experimentId(experimentId)
      .build();
    modelMapper.map(request, variant);
    return variant;
  }

  @Override
  public void update(VariantRequest request, VariantEntity variant) {
    modelMapper.map(request, variant);
  }

  @Override
  public ExperimentResponse toResponse(ExperimentEntity experiment, List<VariantEntity> variants) {
    List<VariantResponse> variantResponses = variants.stream()
      .map(variant -> modelMapper.map(variant, VariantResponse.class))
      .toList();
    ExperimentView source = new ExperimentView(
      experiment.getId(), experiment.getName(), experiment.getStrategy(), variantResponses
    );
    return modelMapper.map(source, ExperimentResponse.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public PagedResponse<ExperimentSummaryResponse> toPage(Page<ExperimentEntity> experiments) {
    List<ExperimentSummaryResponse> items = experiments.getContent().stream()
      .map(experiment -> modelMapper.map(experiment, ExperimentSummaryResponse.class))
      .toList();
    ExperimentPage source = new ExperimentPage(
      items, experiments.getNumber(), experiments.getSize(),
      experiments.getTotalElements(), experiments.getTotalPages()
    );
    return modelMapper.map(source, PagedResponse.class);
  }

  @Override
  public DeleteExperimentResponse toDeleteResponse(Long experimentId) {
    return modelMapper.map(new DeleteResult(experimentId, true), DeleteExperimentResponse.class);
  }

  private record ExperimentView(
    Long id, String name, AssignmentStrategy strategy, List<VariantResponse> variants
  ) {
  }

  private record ExperimentPage(
    List<ExperimentSummaryResponse> items, int page, int size, long totalElements, int totalPages
  ) {
  }

  private record DeleteResult(Long experimentId, boolean deleted) {
  }
}
