package ai.visitorflow.demo.admin.results.mapper;

import ai.visitorflow.demo.admin.results.dto.response.ExperimentResultsResponse;
import ai.visitorflow.demo.admin.results.dto.response.VariantResultResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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
    VariantResult source = new VariantResult(variantId, assigned, exposed, converted, conversionRate);
    return modelMapper.map(source, VariantResultResponse.class);
  }

  @Override
  public ExperimentResultsResponse toResponse(
    Long experimentId, List<VariantResultResponse> variants, long orphanConverted
  ) {
    ExperimentResult source = new ExperimentResult(experimentId, variants, orphanConverted);
    return modelMapper.map(source, ExperimentResultsResponse.class);
  }

  private record VariantResult(
    Long variantId, long assigned, long exposed, long converted, BigDecimal conversionRate
  ) {
  }

  private record ExperimentResult(
    Long experimentId, List<VariantResultResponse> variants, long orphanConverted
  ) {
  }
}
