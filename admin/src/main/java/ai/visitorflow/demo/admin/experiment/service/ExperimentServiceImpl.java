package ai.visitorflow.demo.admin.experiment.service;

import ai.visitorflow.demo.admin.experiment.dto.request.CreateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.UpdateExperimentRequest;
import ai.visitorflow.demo.admin.experiment.dto.request.VariantRequest;
import ai.visitorflow.demo.admin.experiment.dto.response.DeleteExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.ExperimentSummaryResponse;
import ai.visitorflow.demo.admin.experiment.dto.response.PagedResponse;
import ai.visitorflow.demo.admin.experiment.mapper.ExperimentMapper;
import ai.visitorflow.demo.data.context.RequestContextHolder;
import ai.visitorflow.demo.data.event.repository.EventRepository;
import ai.visitorflow.demo.data.exception.NotFoundException;
import ai.visitorflow.demo.data.exception.ValidationException;
import ai.visitorflow.demo.data.experiment.cache.ExperimentConfigCache;
import ai.visitorflow.demo.data.experiment.model.ExperimentEntity;
import ai.visitorflow.demo.data.experiment.model.VariantEntity;
import ai.visitorflow.demo.data.experiment.repository.ExperimentRepository;
import ai.visitorflow.demo.data.experiment.repository.VariantRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class ExperimentServiceImpl implements ExperimentService {
  private static final String ANALYTICS_WARNING =
    "Changing variants or allocation after traffic has started may skew analytics.";

  private final ExperimentRepository experimentRepository;
  private final VariantRepository variantRepository;
  private final EventRepository eventRepository;
  private final ExperimentConfigCache experimentConfigCache;
  private final ExperimentMapper experimentMapper;

  @Override
  @Transactional
  public ExperimentResponse create(CreateExperimentRequest request) {
    Long tenantId = RequestContextHolder.tenantId();
    validate(request.getStrategy(), request.getVariants(), true);
    if (experimentRepository.existsByNameAndTenantId(request.getName(), tenantId)) {
      throw new ValidationException("An experiment with this name already exists");
    }
    ExperimentEntity experiment = experimentRepository.save(experimentMapper.toEntity(request));
    List<VariantEntity> variants = request.getVariants().stream()
      .map(variant -> experimentMapper.toVariant(variant, experiment.getId()))
      .toList();
    variants = variantRepository.saveAll(variants);
    experimentConfigCache.clear();
    return experimentMapper.toResponse(experiment, variants, null);
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResponse<ExperimentSummaryResponse> list(Pageable pageable) {
    return experimentMapper.toPage(experimentRepository.findAllByTenantId(
      pageable, RequestContextHolder.tenantId()
    ));
  }

  @Override
  @Transactional(readOnly = true)
  public ExperimentResponse get(Long experimentId) {
    Long tenantId = RequestContextHolder.tenantId();
    ExperimentEntity experiment = find(experimentId, tenantId);
    List<VariantEntity> variants = variantRepository
      .findAllByExperimentIdAndTenantIdOrderByIdAsc(experimentId, tenantId);
    return experimentMapper.toResponse(experiment, variants, null);
  }

  @Override
  @Transactional
  public ExperimentResponse update(Long experimentId, UpdateExperimentRequest request) {
    Long tenantId = RequestContextHolder.tenantId();
    validate(request.getStrategy(), request.getVariants(), false);
    ExperimentEntity experiment = find(experimentId, tenantId);
    if (experimentRepository.existsByNameAndIdNotAndTenantId(request.getName(), experimentId, tenantId)) {
      throw new ValidationException("An experiment with this name already exists");
    }
    List<VariantEntity> existing = variantRepository
      .findAllByExperimentIdAndTenantIdOrderByIdAsc(experimentId, tenantId);
    Map<Long, VariantEntity> existingById = new HashMap<>();
    existing.forEach(variant -> existingById.put(variant.getId(), variant));
    validateVariantOwnership(request.getVariants(), existingById);
    experimentMapper.update(request, experiment);
    Set<Long> requestedIds = new HashSet<>();
    List<VariantEntity> updated = request.getVariants().stream().map(variantRequest -> {
      if (variantRequest.getId() == null) {
        return experimentMapper.toVariant(variantRequest, experimentId);
      }
      requestedIds.add(variantRequest.getId());
      VariantEntity variant = existingById.get(variantRequest.getId());
      experimentMapper.update(variantRequest, variant);
      return variant;
    }).toList();
    existing.stream()
      .filter(variant -> !requestedIds.contains(variant.getId()))
      .forEach(variant -> variantRepository.deleteByIdAndExperimentIdAndTenantId(
        variant.getId(), experimentId, tenantId
      ));
    updated = variantRepository.saveAll(updated);
    String warning = eventRepository.existsByExperimentIdAndTenantId(experimentId, tenantId)
      ? ANALYTICS_WARNING : null;
    experimentConfigCache.clear();
    return experimentMapper.toResponse(experiment, updated.stream()
      .sorted((left, right) -> left.getId().compareTo(right.getId())).toList(), warning);
  }

  @Override
  @Transactional
  public DeleteExperimentResponse delete(Long experimentId) {
    Long tenantId = RequestContextHolder.tenantId();
    find(experimentId, tenantId);
    variantRepository.deleteAllByExperimentIdAndTenantId(experimentId, tenantId);
    experimentRepository.deleteByIdAndTenantId(experimentId, tenantId);
    experimentConfigCache.clear();
    return experimentMapper.toDeleteResponse(experimentId);
  }

  private ExperimentEntity find(Long experimentId, Long tenantId) {
    return experimentRepository.findByIdAndTenantId(experimentId, tenantId)
      .orElseThrow(() -> new NotFoundException("experiment", experimentId));
  }

  private void validate(String strategy, List<VariantRequest> variants, boolean creating) {
    if (!"hash".equals(strategy)) {
      throw new ValidationException("Only the hash assignment strategy is supported in v0");
    }
    BigDecimal total = variants.stream()
      .map(VariantRequest::getAllocationPercentage)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.compareTo(new BigDecimal("100.00")) != 0) {
      throw new ValidationException("Variant allocation must total exactly 100.00");
    }
    if (variants.stream().filter(VariantRequest::isDefaultVariant).count() != 1) {
      throw new ValidationException("Exactly one variant must be the default");
    }
    if (creating && variants.stream().anyMatch(variant -> variant.getId() != null)) {
      throw new ValidationException("Variant ids cannot be supplied when creating an experiment");
    }
    long distinctIds = variants.stream().map(VariantRequest::getId).filter(id -> id != null).distinct().count();
    long suppliedIds = variants.stream().map(VariantRequest::getId).filter(id -> id != null).count();
    if (distinctIds != suppliedIds) {
      throw new ValidationException("Variant ids must be unique");
    }
  }

  private void validateVariantOwnership(
    List<VariantRequest> variants, Map<Long, VariantEntity> existingById
  ) {
    boolean unknownId = variants.stream()
      .map(VariantRequest::getId)
      .filter(id -> id != null)
      .anyMatch(id -> !existingById.containsKey(id));
    if (unknownId) {
      throw new ValidationException("A variant id does not belong to this experiment");
    }
  }
}
