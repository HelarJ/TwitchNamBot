package chatbot.service;

import chatbot.connector.MessageConnector;
import chatbot.message.LoggableMessage;
import chatbot.message.Message;
import chatbot.message.PoisonMessage;
import chatbot.singleton.SharedStateSingleton;
import chatbot.utils.Config;
import chatbot.utils.Utils;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class SenderService extends AbstractExecutionThreadService {

  private final SharedStateSingleton state = SharedStateSingleton.getInstance();
  private final String username = Config.getTwitchUsername();
  private final String uid = Config.getTwitchUID();
  private final String channel = Config.getChannelToJoin();
  private final MessageConnector messageConnector;

  public SenderService(MessageConnector messageConnector) {
    this.messageConnector = messageConnector;
  }

  @Override
  protected void startUp() {
    log.debug("{} started.", SenderService.class);
  }

  @Override
  protected void shutDown() {
    log.debug("{} stopped.", SenderService.class);
  }

  @Override
  public void run() throws InterruptedException {
    while (state.isBotStillRunning()) {
      Message message = state.sendingBlockingQueue.take();
      if (message instanceof PoisonMessage) {
        log.debug("{} poisoned.", SenderService.class);
        break;
      }
      log.debug("Received message from sendingQueue: {}", message);
      sendToChannel(message);
    }
  }

  public void sendToServer(String msg) {
    if (!msg.endsWith("\r\n")) {
      msg += "\r\n";
    }
    try {
      messageConnector.sendMessage(msg);
    } catch (IOException e) {
      log.error("Error sending message {} to {}: {}", msg, channel, e.getMessage());
      throw new RuntimeException("SenderService error sending message.");
    }
  }

  public void sendToChannel(Message message) {
    String msg = message.getStringMessage();
    msg = cleanMessage(msg);
    if (msg == null) {
      return;
    }
    if (!msg.endsWith("\r\n")) {
      msg += "\r\n";
    }
    String uneditedMessage = msg;
    if (msg.startsWith("/w ")) {
      msg = ("PRIVMSG #jtv :" + msg);
    } else {
      msg = ("PRIVMSG " + channel + " :" + msg);
    }
    log.info("Sent: {}", msg.stripTrailing());
    sendToServer(msg);
    state.increaseSentMessageCount();
    //logs sent bot messages to database.
    state.messageLogBlockingQueue.add(
        new LoggableMessage(username.toLowerCase(), uid, uneditedMessage, true, false,
            "responding-to:" + message.getSender() + ",message:" + uneditedMessage));
  }

  private String cleanMessage(String msg) {
    String cleanedMsg = msg;
    for (String phrase : state.blacklist) {
      cleanedMsg = escapeBlacklisted(cleanedMsg, phrase, true);
    }
    for (String phrase : state.textBlacklist) {
      cleanedMsg = escapeBlacklisted(cleanedMsg, phrase, false);
    }
    cleanedMsg = replaceWords(cleanedMsg);

    if (cleanedMsg.length() >= 400) { //280
      cleanedMsg = cleanedMsg.substring(0, 398);
      cleanedMsg += "..";
    }
    cleanedMsg = cleanSymbols(cleanedMsg);
    return cleanedMsg;
  }

  private String cleanSymbols(String msg) {
    int count = 0;
    int latincount = 0;
    for (char c : msg.toCharArray()) {
      if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
        count++;
      } else {
        latincount++;
      }
    }
    if (count > 24 && count > latincount * 0.30) {
      log.info("Too many symbols: {}", msg);
      int location = msg.indexOf(":");
      if (msg.startsWith("was", location - 3)) {
        return msg.substring(0, location) + " not posted due to NOPERS symbol spam NOPERS";
      }
      return msg.substring(0, msg.indexOf(":", 20) + 1)
          + " Message not posted. NOPERS symbol spam NOPERS";
    }
    return msg;
  }

  private String replaceWords(String msg) {
    String[] bigReplacelist = {
        "\\b[cĆćĈĉČčĊċÇçḈḉȻȼꞒꞓ\uA7C4ꞔƇƈɕ]+[ÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏ0]{2,}[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
        "\\b(?!(?i)night|nogra|ngl)([sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ][a4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ][nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ][dĎďḊḋḐḑD̦d̦ḌḍḒḓḎḏĐđÐðƉɖƊɗᵭᶁᶑȡ])*[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+[❗‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌoÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏІіa4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ]*[\uD83C\uDD71️gǴǵĞğĜĝǦǧĠġG̃g̃ĢģḠḡǤǥꞠꞡƓɠᶃꬶＧｇqꝖꝗꝘꝙɋʠ]+(l[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+t+|[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅa4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ]*[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]*|n[ÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏ0]+[gǴǵĞğĜĝǦǧĠġG̃g̃ĢģḠḡǤǥꞠꞡƓɠᶃꬶＧｇqꝖꝗꝘꝙɋʠ]+|[a4ÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ]*)*[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
        "[fḞḟƑƒꞘꞙᵮᶂ]+[aÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ4@]+[gǴǵĞğĜĝǦǧĠġG̃g̃ĢģḠḡǤǥꞠꞡƓɠᶃꬶＧｇqꝖꝗꝘꝙɋʠ]+([ÓóÒòŎŏÔôỐốỒồỖỗỔổǑǒÖöȪȫŐőÕõṌṍṎṏȬȭȮȯO͘o͘ȰȱØøǾǿǪǫǬǭŌōṒṓṐṑỎỏȌȍȎȏƠơỚớỜờỠỡỞởỢợỌọỘộO̩o̩Ò̩ò̩Ó̩ó̩ƟɵꝊꝋꝌꝍⱺＯｏ0e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅiÍíi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[tŤťṪṫŢţṬṭȚțṰṱṮṯŦŧȾⱦƬƭƮʈT̈ẗᵵƫȶ]+([rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[yÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+|[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+)?)?[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
        "\\b[tŤťṪṫŢţṬṭȚțṰṱṮṯŦŧȾⱦƬƭƮʈT̈ẗᵵƫȶ]+[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[aÁáÀàĂăẮắẰằẴẵẲẳÂâẤấẦầẪẫẨẩǍǎÅåǺǻÄäǞǟÃãȦȧǠǡĄąĄ́ą́Ą̃ą̃ĀāĀ̀ā̀ẢảȀȁA̋a̋ȂȃẠạẶặẬậḀḁȺⱥ\uA7BA\uA7BBᶏẚＡａ4]+[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+([❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+|[yÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+|[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+)[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*",
        "\\b[kḰḱǨǩĶķḲḳḴḵƘƙⱩⱪᶄꝀꝁꝂꝃꝄꝅꞢꞣ]+[❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊịꞼꞽḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌyÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+[kḰḱǨǩĶķḲḳḴḵƘƙⱩⱪᶄꝀꝁꝂꝃꝄꝅꞢꞣ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]([rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[yÝýỲỳŶŷY̊ẙŸÿỸỹẎẏȲȳỶỷỴỵɎɏƳƴỾỿ]+|[rŔŕŘřṘṙŖŗȐȑȒȓṚṛṜṝṞṟR̃r̃ɌɍꞦꞧⱤɽᵲᶉꭉ]+[‼️❗❕i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊịꞼꞽḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[e3ЄєЕеÉéÈèĔĕÊêẾếỀềỄễỂểÊ̄ê̄Ê̌ê̌ĚěËëẼẽĖėĖ́ė́Ė̃ė̃ȨȩḜḝĘęĘ́ę́Ę̃ę̃ĒēḖḗḔḕẺẻȄȅE̋e̋ȆȇẸẹỆệḘḙḚḛɆɇE̩e̩È̩è̩É̩é̩ᶒⱸꬴꬳＥｅ]+)?[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿꟅʂᶊᵴ]*",
        "\\b[cĆćĈĉČčĊċÇçḈḉȻȼꞒꞓ\uA7C4ꞔƇƈɕ]+[hĤĥȞȟḦḧḢḣḨḩḤḥḪḫH̱ẖĦħⱧⱨꞪɦꞕΗНн]+[❗❕‼️i⁉️Ííi̇́Ììi̇̀ĬĭÎîǏǐÏïḮḯĨĩi̇̃ĮįĮ́į̇́Į̃į̇̃ĪīĪ̀ī̀ỈỉȈȉI̋i̋ȊȋỊị\uA7BC\uA7BDḬḭƗɨᶖİiIıＩｉ1lĺľļḷḹl̃ḽḻłŀƚꝉⱡɫɬꞎꬷꬸꬹᶅɭȴＬｌ]+[nŃńǸǹŇňÑñṄṅŅņṆṇṊṋṈṉN̈n̈ƝɲŊŋꞐꞑꞤꞥᵰᶇɳȵꬻꬼИиПпＮｎ]+[kḰḱǨǩĶķḲḳḴḵƘƙⱩⱪᶄꝀꝁꝂꝃꝄꝅꞢꞣ]+[sŚśṤṥŜŝŠšṦṧṠṡŞşṢṣṨṩȘșS̩s̩ꞨꞩⱾȿ\uA7C5ʂᶊᵴ]*"};
    String cleanedMessage = msg;
    for (String blacklisted : bigReplacelist) {
      String oldMessage = cleanedMessage;
      cleanedMessage = cleanedMessage.replaceAll(blacklisted, "BADWORD");
      if (!oldMessage.equals(cleanedMessage)) {
        log.info("blacklist match '{}' for message '{}'", blacklisted, msg);
      }
    }
    cleanedMessage = cleanedMessage.replaceAll("(?i)" + state.replacelist.get(), "BANME");

    return cleanedMessage;
  }

  private String escapeBlacklisted(String msg, String phrase, boolean onlyWord) {
    String replacedMsg = msg + " ";
    String regex;
    if (onlyWord) {
      regex = "\\b(?i)" + phrase + "\\b";
    } else {
      regex = "(?i)" + phrase;
    }
    replacedMsg = replacedMsg.replaceAll(regex, Utils.addZws(phrase));
    return replacedMsg.stripTrailing();
  }

  /**
   * Sends handshake commands to the Twitch IRC server.
   *
   * @throws IOException if there was a general connection issue with the Socket/bufferedwriter.
   */
  public void connect() throws IOException {
    log.info("Attempting to connect.");
    sendToServer("PASS " + Config.getTwitchOauth() + "\r\n");
    sendToServer("NICK " + Config.getTwitchUsername() + "\r\n");
    sendToServer("USER nambot\r\n");
    sendToServer("JOIN " + channel + "\r\n");
    sendToServer("CAP REQ :twitch.tv/membership\r\n");
    sendToServer("CAP REQ :twitch.tv/tags twitch.tv/commands\r\n");
    log.info("Credentials sent.");
  }
}
