package ai.visitorflow.demo.visitor.tracking.controller.v1;

import ai.visitorflow.demo.visitor.tracking.dto.request.TrackRequest;
import ai.visitorflow.demo.visitor.tracking.dto.response.TrackResponse;
import ai.visitorflow.demo.visitor.tracking.service.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/track")
@RequiredArgsConstructor
public class TrackController {
  private final TrackingService trackingService;

  @PostMapping
  public TrackResponse track(@Valid @RequestBody TrackRequest request) {
    return trackingService.track(request);
  }
}
