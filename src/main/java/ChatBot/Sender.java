package ChatBot;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class Sender implements Runnable{
    private final BufferedWriter bufferedWriter;
    private String lastmessage;
    private final String username;
    private final String uid;
    private final BlockingQueue<String> sendingQueue;
    private BlockingQueue<Command> messageQueue;
    private final String channel;
    private boolean running = true;
    private final char zws1 = '\uDB40';
    private final char zws2 = '\uDC00';
    private List<String> blacklist;
    private List<String> textBlacklist;
    private String replacelist;

    public void shutdown(){
        running = false;
    }

    public void setBlacklist(List<String> blacklist, List<String> textBlacklist, String replacelist){
        this.replacelist = replacelist;
        this.blacklist = blacklist;
        this.textBlacklist = textBlacklist;
    }

    @Override
    public void run() {
        Running.getLogger().info("Sender thread started");
        while (Running.getRunning() && running){
            try {
                String toSend = sendingQueue.poll(3, TimeUnit.SECONDS);
                if (toSend != null){
                    sendToChannel(toSend);
                }
            } catch (InterruptedException e) {
                Running.getLogger().warning("Sender thread interrupted");
            }
        }
        Running.getLogger().info("Sender thread stopped");
    }
    public Sender(Socket socket, BlockingQueue<String> sendingQueue, String channel) throws IOException {
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.lastmessage = "";
        Properties p = Running.getProperties();
        this.username = p.getProperty("twitch.nick");
        this.uid = p.getProperty("twitch.uid");
        this.sendingQueue = sendingQueue;
        this.channel = channel;
    }

    public void setLogQueue(BlockingQueue<Command> messageQueue){
        this.messageQueue = messageQueue;
    }

    public void sendToServer(String msg) throws IOException{
        if (!msg.endsWith("\r\n")){
            msg += "\r\n";
        }
        bufferedWriter.write(msg);
        bufferedWriter.flush();
    }

    public void sendToChannel(String msg){
        msg = cleanMessage(msg);
        if (msg == null){
            return;
        }
        if (!msg.endsWith("\r\n")){
            msg += "\r\n";
        }
        lastmessage = msg;
        if (msg.startsWith("/w ")){
            msg = ("PRIVMSG #jtv :"+msg);
        } else {
            msg = ("PRIVMSG "+channel+" :"+msg);
        }
        try {
            Running.getLogger().info("Sent: "+msg.stripTrailing());
            bufferedWriter.write(msg);
            bufferedWriter.flush();
            Running.addCommandCount();
            messageQueue.add(new Command(username.toLowerCase(), uid, lastmessage, true, false, lastmessage));

        } catch (Exception e){
            Running.getLogger().info("Error sending message: " + msg + " to "+channel+": "+e.getMessage());
        }
    }

    private String cleanMessage(String msg){
        String cleanedMsg = msg;
        /*String[] wordlist = {"NaM",  "cmonBruh", "C9 LUL", "ResidentSleeper", "MLEM", "moon2MLEM" , "mle m",
                "Overwatch", "next game", "new game", "meta", "apex", "MeguFace", "cirSlain", "yiff", "y1ff", "kirby", "s e a l",
                "naroStaryn", "pikagirl", "tdogwiz", "memo", "moon2YE", "stalling", "sta11ing", "seppuku", "cubeworld",
                "cube world", "SEA NINE LUL", "sudoku", "TriHard 7", "swatted", "swat", "swatting", "mxc", "nb3w", "mongoloid",
                "forsena", "https://clips.twitch.tv/FurryCuteSwallowTF2John", "rape", "pika girl", "Dingdingdingding", "seal12MeguSmile"};
        String[] blacklist = { "BroBalt", "FishMoley", "YetiZ", "TaxiBro"};
         */
        for (String phrase : blacklist){
            cleanedMsg = escapeBlacklisted(cleanedMsg, phrase, true);
        }
        for (String phrase : textBlacklist){
            cleanedMsg = escapeBlacklisted(cleanedMsg, phrase, false);
        }
        cleanedMsg = replaceWords(cleanedMsg);

        if (cleanedMsg.length()>=400){ //280
            cleanedMsg=cleanedMsg.substring(0,398);
            cleanedMsg +="..";
        }
        cleanedMsg = cleanSymbols(cleanedMsg);
        return cleanedMsg;
    }
    private String cleanSymbols(String msg){
        int count = 0;
        int latincount = 0;
        for (char c:msg.toCharArray()){
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)){
                count++;
            } else {
                latincount++;
            }
        }
        if (count > 24 && count > latincount*0.30){
            Running.getLogger().info("Too many symbols: "+msg);
            int location = msg.indexOf(":");
            if (msg.startsWith("was", location-3)){
                return msg.substring(0, location) + " not posted due to NOPERS symbol spam NOPERS";
            }
            return msg.substring(0, msg.indexOf(":",20)+1) + " Message not posted. NOPERS symbol spam NOPERS";
        }
        return msg;
    }
    private String replaceWords(String msg){
        String[] bigReplacelist = {
                "\\b[cĆćĈĉČčĊċÇçḈḉȻȼꞒꞓ\uA7C4ꞔƇƈɕ]+[ÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏ0]{2,}[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
                "\\b(?!(?i)night|nogra|ngl)([sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ][a4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ][nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ][dĎďḊḋḐḑD̦d̦ḌḍḒḓḎḏĐđÐðƉɖƊɗᵭᶁᶑȡ])*[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+[❗‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌoÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏІіa4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ]*[\uD83C\uDD71️gǴǵĞğĜĝǦǧĠġG̃g̃ĢģḠḡǤǥꞠꞡƓɠᶃꬶＧｇqꝖꝗꝘꝙɋʠ]+(l[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+t+|[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅa4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ]*[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]*|n[ÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏ0]+[gǴǵĞğĜĝǦǧĠġG̃g̃ĢģḠḡǤǥꞠꞡƓɠᶃꬶＧｇqꝖꝗꝘꝙɋʠ]+|[a4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ]*)*[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
                "[fḞḟƑƒꞘꞙᵮᶂ]+[aÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ4@]+[gǴǵĞğĜĝǦǧĠġG̃g̃ĢģḠḡǤǥꞠꞡƓɠᶃꬶＧｇqꝖꝗꝘꝙɋʠ]+([ÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏ0e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅiÍíi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[tŤťṪṫŢţṬṭȚțṰṱṮṯŦŧȾⱦƬƭƮʈT̈ẗᵵƫȶ]+([rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[yÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+|[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+)?)?[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
                "\\b[tŤťṪṫŢţṬṭȚțṰṱṮṯŦŧȾⱦƬƭƮʈT̈ẗᵵƫȶ]+[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[aÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ4]+[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+([❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+|[yÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+|[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+)[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
                "\\b[kḰḱǨǩĶķḲḳḴḵƘƙⱩⱪᶄꝀꝁꝂꝃꝄꝅꞢꞣ]+[❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊịꞼꞽḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌyÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+[kḰḱǨǩĶķḲḳḴḵƘƙⱩⱪᶄꝀꝁꝂꝃꝄꝅꞢꞣ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]([rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[yÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+|[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[‼️❗❕i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊịꞼꞽḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+)?[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿꟅʂᶊᵴ]*",
                "\\b[cĆćĈĉČčĊċÇçḈḉȻȼꞒꞓ\uA7C4ꞔƇƈɕ]+[hĤĥȞȟḦḧḢḣḨḩḤḥḪḫH̱ẖĦħⱧⱨꞪɦꞕΗНн]+[❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+[kḰḱǨǩĶķḲḳḴḵƘƙⱩⱪᶄꝀꝁꝂꝃꝄꝅꞢꞣ]+[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*"};
        String cleanedMessage = msg;
        int i = 0;
        for (String blacklisted: bigReplacelist){
            String oldMessage = cleanedMessage;
            cleanedMessage = cleanedMessage.replaceAll(blacklisted, "BADWORD");
            if (!oldMessage.equals(cleanedMessage)){
                Running.getLogger().warning("blacklist match "+i+" for message \""+msg+"\"");
            }
            i++;
        }
        cleanedMessage = cleanedMessage.replaceAll("(?i)"+replacelist, "BANME");

        return cleanedMessage;
    }
    private String escapeBlacklisted(String msg, String phrase, boolean onlyWord){
        String replacedMsg = msg + " ";
        String regex;
        if (onlyWord){
            regex = "\\b(?i)"+phrase+"\\b";
        } else {
            regex = "(?i)"+phrase;
        }
        replacedMsg = replacedMsg.replaceAll(regex, phrase.substring(0,1) + zws1 + zws2 +phrase.substring(1));
        return replacedMsg.stripTrailing();
    }
    public BlockingQueue<String> getSendingQueue(){
        return sendingQueue;
    }
}
