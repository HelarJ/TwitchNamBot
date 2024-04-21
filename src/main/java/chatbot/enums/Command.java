package chatbot.enums;

import java.util.HashMap;
import java.util.Set;

public enum Command {
  NAMMERS(false, true, true, true, false),
  NAMPING(false, true, true, true, false),
  NAMES(false, true, true, true, false),
  NAMREFRESH(false, false, false, false, false),
  NAMCOMMANDS(false, true, true, true, false),
  NAMCHOOSE(false, true, true, true, false),
  NAM(false, true, true, true, false),
  LASTMESSAGE(false, false, true, true, true),
  LM(false, false, true, true, true),
  FIRSTMESSAGE(false, true, true, true, true),
  FM(false, true, true, true, true),
  LOG(true, true, true, true, false),
  LOGS(true, true, true, true, false),
  RQ(false, true, true, true, true),
  RS(false, true, false, true, true),
  ADDDISABLED(false, true, false, true, false),
  REMDISABLED(false, true, false, true, false),
  FS(false, false, false, false, false),
  SEARCH(false, true, true, true, false),
  SEARCHUSER(false, true, true, true, false),
  ADDALT(false, false, false, true, false),
  NAMBAN(false, false, false, true, false),
  SC(false, false, false, true, false),
  LASTSEEN(true, false, true, true, false),
  LS(true, false, true, true, false),
  MCOUNT(true, true, true, true, false)
  ;


  private final boolean onlineAllowed;
  private final boolean selfAllowed;
  private final boolean othersAllowed;
  private final boolean modsAllowed;
  private final boolean canOptOut;

  /**
   * @param onlineAllowed command is allowed to be used in onlinechat.
   * @param selfAllowed   command can be used with own name as argument.
   * @param othersAllowed command can be used with others name as argument.
   * @param modsAllowed   command can be used by mods.
   */
  Command(boolean onlineAllowed, boolean selfAllowed, boolean othersAllowed, boolean modsAllowed,
      boolean canOptOut) {
    this.onlineAllowed = onlineAllowed;
    this.selfAllowed = selfAllowed;
    this.othersAllowed = othersAllowed;
    this.modsAllowed = modsAllowed;
    this.canOptOut = canOptOut;
  }

  public boolean isOnlineAllowed() {
    return onlineAllowed;
  }

  public boolean isSelfAllowed() {
    return selfAllowed;
  }

  public boolean isOthersAllowed() {
    return othersAllowed;
  }

  public boolean isModsAllowed() {
    return modsAllowed;
  }

  public boolean isOptedOut(String username, Set<String> optOutList) {
    return canOptOut && optOutList.contains(username);
  }

  @Override
  public String toString() {
    return super.toString().toLowerCase();
  }

  public Boolean isUserCommandSpecified(HashMap<String, Boolean> userPermissionMap) {
    return userPermissionMap.getOrDefault(toString(), null);
  }
}
