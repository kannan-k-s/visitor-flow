package ai.visitorflow.demo.visitor.tracking.service;

import ai.visitorflow.demo.visitor.tracking.dto.request.TrackRequest;
import ai.visitorflow.demo.visitor.tracking.dto.response.TrackResponse;

public interface TrackingService {
  TrackResponse track(TrackRequest request);
}
