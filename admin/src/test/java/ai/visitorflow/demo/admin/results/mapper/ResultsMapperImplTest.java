package ai.visitorflow.demo.admin.results.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import ai.visitorflow.demo.admin.results.dto.response.VariantResultResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.modelmapper.config.Configuration.AccessLevel;

class ResultsMapperImplTest {
  private ResultsMapperImpl mapper;

  @BeforeEach
  void setUp() {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration().setFieldMatchingEnabled(true).setFieldAccessLevel(AccessLevel.PRIVATE);
    mapper = new ResultsMapperImpl(modelMapper);
  }

  @Test
  void calculatesConversionRateFromExposures() {
    VariantResultResponse result = mapper.toVariant(3L, 20, 8, 3);

    assertThat(result.getConversionRate()).isEqualByComparingTo(new BigDecimal("0.3750"));
  }

  @Test
  void returnsZeroRateWithoutExposure() {
    assertThat(mapper.toVariant(3L, 20, 0, 0).getConversionRate()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
