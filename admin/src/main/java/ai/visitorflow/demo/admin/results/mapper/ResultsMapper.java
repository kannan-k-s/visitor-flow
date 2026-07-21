package ai.visitorflow.demo.admin.results.mapper;

import ai.visitorflow.demo.admin.results.dto.response.ExperimentResultsResponse;
import ai.visitorflow.demo.admin.results.dto.response.VariantResultResponse;
import java.util.List;

public interface ResultsMapper {
  VariantResultResponse toVariant(Long variantId, long assigned, long exposed, long converted);
  ExperimentResultsResponse toResponse(
    Long experimentId, List<VariantResultResponse> variants, long orphanConverted
  );
}
