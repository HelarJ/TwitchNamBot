import ChatBot.Command;
import ChatBot.Statistics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class tests {

    @Test
    public void testNameParse() {

        Command command = new Command("kroom",
                "1234",
                "!nam cringe test",
                true, true, null);

        assertEquals("nam", command.getName());
        assertEquals("cringe test", command.getArguments());
        assertEquals("!nam cringe test", command.getMessage());

        command = new Command("kroom",
                "1234",
                "!lastmessage me @kroom",
                true, true, null);

        assertEquals("kroom", cleanName(command.getSender(), getArg(command.getArguments(), 0)));

        assertEquals("kroom", cleanName(command.getSender(), getArg(command.getArguments(), 1)));


    }

    private static String getArg(String args, int position){
        String[] split = args.split(" ");
        if (split.length>=position+1){
            return split[position];
        }
        return null;
    }

    public static String cleanName(String from, String name){
        name = name.replaceFirst("@", "");
        if (name.equals("me")){
            return from;
        }
        return name;
    }
}
