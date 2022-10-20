package chatbot.utils;

import java.util.List;

public class HtmlBuilder {
    private final StringBuilder sb;

    public HtmlBuilder() {
        sb = new StringBuilder("""
                <!DOCTYPE html>
                <html>
                """);

    }

    public HtmlBuilder withTitle(String title) {
        sb.append("""
                <head>
                    <meta charset="utf-8">
                    <title>%s</title>
                </head>
                """.formatted(title));
        return this;
    }

    public HtmlBuilder withBodyIntro(String intro) {
        sb.append("""
                <body>
                =========================================================<br>
                %s
                =========================================================<br>
                <br>
                """.formatted(intro));

        return this;
    }

    public HtmlBuilder withBodyContent(List<String> content) {
        for (String s : content) {
            sb.append(s);
        }
        sb.append("""
                <br>
                END OF FILE<br>
                </body>
                """);
        return this;
    }

    public String build() {
        sb.append("</html>");
        return sb.toString();

    }
}
