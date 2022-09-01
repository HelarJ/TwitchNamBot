package ChatBot.StaticUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {
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

    public static String getWordList(String msg) {
        String phrase = msg.replaceAll(" \uDB40\uDC00", "");
        List<String> phraseList = new ArrayList<>();
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(phrase);
        while (m.find()) {
            phraseList.add(m.group(1));
        }

        return phraseList.toString();
    }

    public static String getSolrPattern(String msg) {
        String phrase = msg.replaceAll(" \uDB40\uDC00", "");
        phrase = phrase.replaceAll("[:!()^||&&]", "");
        List<String> phraseList = new ArrayList<>();
        Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(phrase);
        while (m.find()) {
            phraseList.add(m.group(1));
        }
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

        System.out.println(sb);
        return sb.toString();
    }

    public static String cleanName(String from, String name) {
        name = name.replaceFirst("@", "");
        if (name.equals("me")) {
            return from;
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
