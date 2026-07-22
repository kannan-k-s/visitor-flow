package ai.visitorflow.demo.admin.experiment.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PagedResponse<T> {
  @JsonProperty("items")
  private List<T> items;

  @JsonProperty("page")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private int page;

  @JsonProperty("size")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private int size;

  @JsonProperty("total_elements")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private long totalElements;

  @JsonProperty("total_pages")
  @JsonFormat(shape = JsonFormat.Shape.STRING)
  private int totalPages;
}
