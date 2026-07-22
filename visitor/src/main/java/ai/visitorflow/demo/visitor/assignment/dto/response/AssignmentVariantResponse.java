package ai.visitorflow.demo.visitor.assignment.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AssignmentVariantResponse {
  @JsonProperty("id")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private Long variantId;

  @JsonProperty("content")
  private String content;
}
