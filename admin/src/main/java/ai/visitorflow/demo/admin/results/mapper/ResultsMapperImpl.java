package ai.visitorflow.demo.admin.results.mapper;

import ai.visitorflow.demo.admin.results.dto.response.ExperimentResultsResponse;
import ai.visitorflow.demo.admin.results.dto.response.VariantResultResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ResultsMapperImpl implements ResultsMapper {
  private final ModelMapper modelMapper;

  @Override
  public VariantResultResponse toVariant(Long variantId, long assigned, long exposed, long converted) {
    BigDecimal conversionRate = exposed == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(converted)
      .divide(BigDecimal.valueOf(exposed), 4, RoundingMode.HALF_UP);
    VariantResult source = VariantResult.builder()
      .variantId(variantId)
      .assigned(assigned)
      .exposed(exposed)
      .converted(converted)
      .conversionRate(conversionRate)
      .build();
    return modelMapper.map(source, VariantResultResponse.class);
  }

  @Override
  public ExperimentResultsResponse toResponse(
    Long experimentId, List<VariantResultResponse> variants, long orphanConverted
  ) {
    ExperimentResult source = ExperimentResult.builder()
      .experimentId(experimentId)
      .variants(variants)
      .orphanConverted(orphanConverted)
      .build();
    return modelMapper.map(source, ExperimentResultsResponse.class);
  }

  @Getter
  @Builder
  private static class VariantResult {
    private Long variantId;
    private long assigned;
    private long exposed;
    private long converted;
    private BigDecimal conversionRate;
  }

  @Getter
  @Builder
  private static class ExperimentResult {
    private Long experimentId;
    private List<VariantResultResponse> variants;
    private long orphanConverted;
  }
}
