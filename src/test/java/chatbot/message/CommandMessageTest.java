//package chatbot.message;
//
//import static chatbot.enums.Command.RQ;
//import static chatbot.enums.Command.RS;
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//import chatbot.enums.Command;
//import java.util.stream.Stream;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.params.ParameterizedTest;
//import org.junit.jupiter.params.provider.Arguments;
//import org.junit.jupiter.params.provider.MethodSource;
//
//public class CommandMessageTest {
//
//  static Stream<Arguments> commandMessagesforParse() {
//    return Stream.of(
//        Arguments.of(RQ, "!rq"),
//        Arguments.of(RQ, "!rq "),
//        Arguments.of(RS, "!rs me"),
//        Arguments.of(RS, "!rs tester test"),
//        Arguments.of(RS, "!rs  \uDB40\uDC00"),
//        Arguments.of(null, "!rss"),
//        Arguments.of(null, "!rs*"),
//        Arguments.of(null, "*"),
//        Arguments.of(null, "rq"),
//        Arguments.of(null, ""),
//        Arguments.of(null, "! rs")
//    );
//  }
//
//  static Stream<Arguments> commandMessagesforUsernameParse() {
//    return Stream.of(
//        Arguments.of("hello", "!rq hello"),
//        Arguments.of("tester", "!rq me"),
//        Arguments.of("tester", "!rq @tester"),
//        Arguments.of("tester", "!rq"),
//        Arguments.of("tester", "!rq \uDB40\uDC00"),
//        Arguments.of("tester", "!rq me \uDB40\uDC00"),
//        Arguments.of("*me*", "!rq *me*"),
//        Arguments.of("*", "!rq *")
//    );
//  }
//
//  static Stream<Arguments> commandMessagesforMessageWithoutUsername() {
//    return Stream.of(
//        Arguments.of("", "!rs hello"),
//        Arguments.of("", "!rs"),
//        Arguments.of("", "!rs me"),
//        Arguments.of("", "!rs @tester"),
//        Arguments.of("*", "!rs me *"),
//        Arguments.of("*", "!rs * *"),
//        Arguments.of("yo \uDB40\uDC00", "!rs test yo \uDB40\uDC00"),
//        Arguments.of("this is a longer message", "!rs me this is a longer message"),
//        Arguments.of("\"this message has quotes\"", "!rs coolname \"this message has quotes\""),
//        Arguments.of("me", "!rs me me"),
//        Arguments.of("message", "!rs n\uDB40\uDC00ame message")
//    );
//  }
//
//  static Stream<Arguments> commandMessagesforYearParse() {
//    return Stream.of(
//        Arguments.of("[2022-01-01T00:00:00Z TO 2022-12-31T23:59:59Z]", "!rq hello 2022"),
//        Arguments.of("[2022-01-01T00:00:00Z TO 2022-12-31T23:59:59Z]", "!rq me 2022"),
//        Arguments.of("[2022-01-01T00:00:00Z TO 2022-12-31T23:59:59Z]", "!rq me 2022 \uDB40\uDC00"),
//        Arguments.of(null, "!rq 2022"),
//        Arguments.of(null, "!rq me 1999"),
//        Arguments.of(null, "!rq me 22"),
//        Arguments.of(null, "!rq tester 2")
//    );
//  }
//
//  CommandMessage makeCommandMessage(String message) {
//    return new CommandMessage("tester", message);
//  }
//
//  @ParameterizedTest
//  @MethodSource("commandMessagesforParse")
//  void commandParseTest(Command expected, String input) {
//    assertEquals(expected, makeCommandMessage(input).getCommand());
//  }
//
//  @Test
//  void invalidCommandMessage() {
//    assertEquals("", makeCommandMessage("").getMessageWithoutCommand());
//    assertEquals("no command", makeCommandMessage("no command").getMessageWithoutCommand());
//  }
//
//  @ParameterizedTest
//  @MethodSource("commandMessagesforUsernameParse")
//  void getUsernameTest(String expected, String input) {
//    assertEquals(expected, makeCommandMessage(input).getUsername());
//
//  }
//
//  @ParameterizedTest
//  @MethodSource("commandMessagesforMessageWithoutUsername")
//  void getMessageWithoutUsernameTest(String expected, String input) {
//    assertEquals(expected, makeCommandMessage(input).getMessageWithoutUsername());
//
//  }
//
//  @ParameterizedTest
//  @MethodSource("commandMessagesforYearParse")
//  void getYearTest(String expected, String input) {
//    assertEquals(expected, makeCommandMessage(input).getYear());
//
//  }
//
//  @Test
//  void getSenderTest() {
//    assertEquals("tester", makeCommandMessage("").getSender());
//  }
//
//  @Test
//  void stringMessageTest() {
//    CommandMessage message = makeCommandMessage("!rq me this \"is a message\" with some quotes");
//    assertEquals("!rq me this \"is a message\" with some quotes", message.getStringMessage());
//    message.setResponse("response");
//    assertEquals("response", message.getStringMessage());
//  }
//
//  @Test
//  void toStringTest() {
//    CommandMessage message = makeCommandMessage("!rq me this \"is a message\" with some quotes");
//    assertEquals("tester: !rq me this \"is a message\" with some quotes", message.toString());
//  }
//}
