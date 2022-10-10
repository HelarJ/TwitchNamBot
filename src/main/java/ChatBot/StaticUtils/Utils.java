package ChatBot.StaticUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
    /**
     * Converts time in seconds to an easy-to-read format.
     * Example: 504 000 seconds would turn into 5d20h (minutes and seconds would not be included in this case as they would be 0)
     *
     * @param seconds time in seconds
     * @return string representation of time
     */
    public static String convertTime(int seconds) {
        StringBuilder sb = new StringBuilder();
        if (seconds >= 60) {
            int minutes = seconds / 60;
            if (minutes >= 60) {
                int hours = minutes / 60;
                if (hours >= 24) {
                    int days = hours / 24;
                    sb.append(days);
                    sb.append("d");
                    sb.append(hours % 24);
                } else {
                    sb.append(hours);
                }
                sb.append("h");
                sb.append(minutes % 60);
            } else {
                sb.append(minutes);
            }
            sb.append("m");
            sb.append(seconds % 60);
        } else {
            sb.append(seconds);
        }

        sb.append("s");

        return sb.toString();
    }

    /**
     * Function for getting a List of words separated by a space or a phrase surrounded with quotes.
     * Example: 'this "sentence is"' would return ["this", "sentence is"]
     *
     * @param msg message to make into a list
     * @return string representation of the found wordlist.
     */
    public static List<String> getWordList(String msg) {
        String phrase = msg.replaceAll(" \uDB40\uDC00", "");
        phrase = phrase.replaceAll("[:!()^||&&]", "");
        List<String> phraseList = new ArrayList<>();
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(phrase);
        while (m.find()) {
            phraseList.add(m.group(1));
        }
        return phraseList;
    }

    /**
     * Function for getting a Solr query compliant message from a string.
     * Example: 'this message' would turn into "message:this AND message:message"
     *
     * @param msg message in string form
     * @return string that can be used in a Solr query
     */
    public static String getSolrPattern(String msg) {
        List<String> phraseList = getWordList(msg);
        StringBuilder sb = new StringBuilder();
        for (String word : phraseList) {

            if (word.startsWith("-")) {
                sb.append("-message:");
                sb.append(word.replaceAll("-", ""));
            } else {
                sb.append("message:");
                sb.append(word);
            }
            sb.append(" AND ");
        }
        if (sb.length() > 5) {
            sb.replace(sb.length() - 5, sb.length(), "");
        } else {
            sb.append("\"\"");
        }

        return sb.toString();
    }

    /**
     * Cleans the username from a leading @ symbol, zerowhitespace characters, and replaces "me" with the actual username.
     *
     * @param from username who sent the message
     * @param name username whose name is cleaned, or "me"
     * @return cleaned username.
     */
    public static String cleanName(String from, String name) {
        name = name.replaceAll(" \uDB40\uDC00", "");
        name = name.replaceFirst("@", "");
        if (name.equals("me")) {
            return from;
        }
        return name.toLowerCase(Locale.ROOT);
    }

    public static String addZws(String word) {
        final char zws1 = '\uDB40';
        final char zws2 = '\uDC00';
        return word.substring(0, 1) + zws1 + zws2 + word.substring(1);
    }
}