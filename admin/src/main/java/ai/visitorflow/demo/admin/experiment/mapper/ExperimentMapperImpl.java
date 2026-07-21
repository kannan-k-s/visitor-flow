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
import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
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
  public ExperimentResponse toResponse(
    ExperimentEntity experiment, List<VariantEntity> variants, String analyticsWarning
  ) {
    List<VariantResponse> variantResponses = variants.stream()
      .map(variant -> modelMapper.map(variant, VariantResponse.class))
      .toList();
    ExperimentView source = ExperimentView.builder()
      .id(experiment.getId())
      .name(experiment.getName())
      .strategy(experiment.getStrategy())
      .variants(variantResponses)
      .analyticsWarning(analyticsWarning)
      .build();
    return modelMapper.map(source, ExperimentResponse.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public PagedResponse<ExperimentSummaryResponse> toPage(Page<ExperimentEntity> experiments) {
    List<ExperimentSummaryResponse> items = experiments.getContent().stream()
      .map(experiment -> modelMapper.map(experiment, ExperimentSummaryResponse.class))
      .toList();
    ExperimentPage source = ExperimentPage.builder()
      .items(items)
      .page(experiments.getNumber())
      .size(experiments.getSize())
      .totalElements(experiments.getTotalElements())
      .totalPages(experiments.getTotalPages())
      .build();
    return modelMapper.map(source, PagedResponse.class);
  }

  @Override
  public DeleteExperimentResponse toDeleteResponse(Long experimentId) {
    DeleteResult source = DeleteResult.builder().experimentId(experimentId).deleted(true).build();
    return modelMapper.map(source, DeleteExperimentResponse.class);
  }

  @Getter
  @Builder
  private static class ExperimentView {
    private Long id;
    private String name;
    private String strategy;
    private List<VariantResponse> variants;
    private String analyticsWarning;
  }

  @Getter
  @Builder
  private static class ExperimentPage {
    private List<ExperimentSummaryResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
  }

  @Getter
  @Builder
  private static class DeleteResult {
    private Long experimentId;
    private boolean deleted;
  }
}
