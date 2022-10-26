package chatbot.utils;

import static chatbot.utils.Utils.convertTime;
import static chatbot.utils.Utils.getWordList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class UtilsTests {

  static Stream<Arguments> timeStringsforConverTime() {
    return Stream.of(
        Arguments.of("0s", 0),
        Arguments.of("10s", 10),
        Arguments.of("1m", 60),
        Arguments.of("1m1s", 61),
        Arguments.of("1h", 3600),
        Arguments.of("1h1m6s", 3666),
        Arguments.of("1d", 86400),
        Arguments.of("1d1m", 86460),
        Arguments.of("1d1h1s", 90001)
    );
  }

  @ParameterizedTest
  @MethodSource("timeStringsforConverTime")
  public void convertTimeTest(String expected, int input) {
    assertEquals(expected, convertTime(input));
  }

  @Test
  public void getWordListTest() {
    List<String> splitString = Arrays.asList("message", "\"split message\"");
    assertIterableEquals(splitString, getWordList("message \"split message\""));

    splitString = Arrays.asList("message", "\"split message\"", "another", "test");
    assertIterableEquals(splitString, getWordList("message \"split message\"\" another test"),
        "extra quote");

    splitString = Arrays.asList("message", "split", "message", "another");
    assertIterableEquals(splitString, getWordList("message \"split message another"),
        "one quote");

  }
}
