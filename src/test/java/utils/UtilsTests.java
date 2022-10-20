package utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static chatbot.utils.Utils.convertTime;
import static chatbot.utils.Utils.getWordList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

public class UtilsTests {
    @Test
    public void convertTimeTest() {
        assertEquals("10s", convertTime(10));
        assertEquals("1m", convertTime(60));
        assertEquals("1m1s", convertTime(61));
        assertEquals("1h", convertTime(3600));
        assertEquals("1h1m6s", convertTime(3666));
        assertEquals("1d", convertTime(86400));
        assertEquals("1d1m", convertTime(86460));
        assertEquals("1d1h1s", convertTime(90001));
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
