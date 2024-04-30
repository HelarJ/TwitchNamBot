package chatbot.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    /**
     * Converts time in seconds to an easy-to-read format. Example: 504 000 seconds would turn into
     * 5d20h (minutes and seconds would not be included in this case as they would be 0)
     *
     * @param seconds time in seconds
     * @return string representation of time
     */
    public static String convertTime(long seconds) {
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;

        String formattedTime = "";
        if (days > 0) {
            formattedTime += String.format("%dd", days);
        }
        if (hours > 0) {
            formattedTime += String.format("%dh", hours);
        }
        if (minutes > 0) {
            formattedTime += String.format("%dm", minutes);
        }
        if (remainingSeconds > 0 || formattedTime.isEmpty()) {
            formattedTime += String.format("%ds", remainingSeconds);
        }
        return formattedTime;
    }

    /**
     * Function for getting a List of words separated by a space or a phrase surrounded with quotes.
     * Example: 'this "sentence is"' would return ["this", "sentence is"]
     *
     * @param msg message to make into a list
     * @return string representation of the found wordlist.
     */
    public static List<String> getWordList(final String msg) {
        String phrase = msg.replaceAll(" \uDB40\uDC00", "");
        phrase = phrase.replaceAll("[:~!()^||&&]", "");
        List<String> phraseList = new ArrayList<>();
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(phrase);
        while (m.find()) {
            phraseList.add(m.group(1).strip());
        }
        return phraseList;
    }

    /**
     * Function for getting a Solr query compliant message from a string. Example: 'this message'
     * would turn into "message:this AND message:message"
     *
     * @param msg message in string form
     * @return string that can be used in a Solr query
     */
    public static String getSolrPattern(final String msg) {
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
     * Cleans the username from a leading @ symbol, zerowhitespace characters, and replaces "me" with
     * the actual username.
     *
     * @param from username who sent the message
     * @param name username whose name is cleaned, or "me"
     * @return cleaned username.
     */
    public static String cleanName(final String from, final String name) {
        String temp = name.replaceFirst("@", "");
        if (temp.equals("me")) {
            return from;
        }
        return temp.toLowerCase(Locale.ROOT);
    }

    public static String addZws(String word) {
        final char zws1 = '\uDB40';
        final char zws2 = '\uDC00';
        return word.substring(0, 1) + zws1 + zws2 + word.substring(1);
    }

    /**
     * Gets an argument from a space separated message.
     *
     * @param args     String to get the argument from.
     * @param position Which position argument to get.
     * @return Argument as a single word or null if not found.
     */
    public static String getArg(final String args, int position) {
        String[] split = args.strip().split(" ");
        if (split.length >= position + 1) {
            if (split[position].isEmpty()) {
                return null;
            }
            return split[position];
        }
        return null;
    }

    public static String getYear(String year) {
        try {
            if (Integer.parseInt(year) > 2000) {
                return "[" + year + "-01-01T00:00:00Z TO " + year + "-12-31T23:59:59Z]";
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    public static String formatNammerList(List<String> nammerList) {
        StringBuilder sb = new StringBuilder();
        sb.append("Top NaMmers: ");
        for (String nammer : nammerList) {
            sb.append("%s | ".formatted(nammer));
            if (sb.length() >= 270) {
                break;
            }
        }
        return sb.toString();
    }
}
