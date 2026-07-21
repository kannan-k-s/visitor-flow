package ai.visitorflow.demo.admin.results.service;

import ai.visitorflow.demo.admin.results.dto.response.ExperimentResultsResponse;
import ai.visitorflow.demo.admin.results.dto.response.VariantResultResponse;
import ai.visitorflow.demo.admin.results.mapper.ResultsMapper;
import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.event.repository.EventRepository;
import ai.visitorflow.demo.data.event.repository.VariantStatusCount;
import ai.visitorflow.demo.data.exception.NotFoundException;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import ai.visitorflow.demo.data.experiment.repository.ExperimentRepository;
import ai.visitorflow.demo.data.experiment.repository.VariantRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ResultsServiceImpl implements ResultsService {
  private final ExperimentRepository experimentRepository;
  private final VariantRepository variantRepository;
  private final EventRepository eventRepository;
  private final ResultsMapper resultsMapper;

  @Override
  @Transactional(readOnly = true)
  public ExperimentResultsResponse get(Long experimentId) {
    Long tenantId = RequestContextHolder.tenantId();
    if (experimentRepository.findByIdAndTenantId(experimentId, tenantId).isEmpty()) {
      throw new NotFoundException("experiment", experimentId);
    }
    Map<Long, Map<String, Long>> counts = new HashMap<>();
    for (VariantStatusCount count : eventRepository.findVariantStatusCounts(experimentId, tenantId)) {
      counts.computeIfAbsent(count.getVariantId(), ignored -> new HashMap<>())
        .put(count.getStatus(), count.getTotal());
    }
    List<VariantEntity> variants = variantRepository
      .findAllByExperimentIdAndTenantIdOrderByIdAsc(experimentId, tenantId);
    List<VariantResultResponse> results = variants.stream().map(variant -> {
      Map<String, Long> variantCounts = counts.getOrDefault(variant.getId(), Map.of());
      return resultsMapper.toVariant(
        variant.getId(), variantCounts.getOrDefault("assigned", 0L),
        variantCounts.getOrDefault("exposed", 0L), variantCounts.getOrDefault("converted", 0L)
      );
    }).toList();
    return resultsMapper.toResponse(
      experimentId, results, eventRepository.countOrphanConversions(experimentId, tenantId)
    );
  }
}
