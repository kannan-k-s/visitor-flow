package ai.visitorflow.demo.admin.results.service;

import ai.visitorflow.demo.admin.results.dto.response.ExperimentResultsResponse;

public interface ResultsService {
  ExperimentResultsResponse get(Long experimentId);
}
