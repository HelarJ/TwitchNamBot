package chatbot.enums;

import java.util.Locale;

public enum Command {
    NAMMERS,
    NAMPING,
    NAMES,
    NAMREFRESH,
    NAMCOMMANDS,
    NAMCHOOSE,
    NAM,
    LASTMESSAGE,
    FIRSTMESSAGE,
    LOG,
    LOGS,
    RQ,
    RS,
    ADDDISABLED,
    REMDISABLED,
    FS,
    SEARCH,
    SEARCHUSER,
    ADDALT,
    NAMBAN, STALKLIST;

    @Override
    public String toString() {
        return super.toString().toLowerCase(Locale.ROOT);
    }
}
