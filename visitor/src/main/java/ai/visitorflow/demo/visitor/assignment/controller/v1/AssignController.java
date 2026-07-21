package ai.visitorflow.demo.visitor.assignment.controller.v1;

import ai.visitorflow.demo.visitor.assignment.dto.response.AssignResponse;
import ai.visitorflow.demo.visitor.assignment.service.AssignmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/assign")
@RequiredArgsConstructor
public class AssignController {
  private final AssignmentService assignmentService;

  @GetMapping
  public AssignResponse assign(@RequestParam("experiments") List<Long> experimentIds) {
    return assignmentService.assign(experimentIds);
  }
}
