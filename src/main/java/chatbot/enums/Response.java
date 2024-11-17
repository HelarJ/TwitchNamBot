package chatbot.enums;

public enum Response {
    INTERNAL_ERROR("internal error Deadlole"),
    NO_MESSAGES("no messages found PEEPERS"),
    TIMEOUT("query timeout, try again SLUDGE"),

    ;

    private final String responseString;

    Response(String responseString) {
        this.responseString = responseString;
    }

    @Override
    public String toString() {
        return responseString;
    }
}
