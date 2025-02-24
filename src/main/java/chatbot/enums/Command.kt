package chatbot.enums

import chatbot.enums.Command.Feature.*
import java.util.*

public enum class Command {
    NAMMERS(NO_ARGS),
    NAMPING(NO_ARGS),

    NAMES(ONLINE, SELF, OTHERS),

    NAMREFRESH(ADMIN_ONLY),
    NAMCOMMANDS(ADMIN_ONLY),

    NAMCHOOSE(NO_ARGS),

    NAM(SELF, OTHERS),

    LASTMESSAGE(OTHERS, OPT_OUT),
    LM(LASTMESSAGE),

    FIRSTMESSAGE(SELF, OTHERS, OPT_OUT),
    FM(FIRSTMESSAGE),

    LOG(ONLINE, SELF, OTHERS),
    LOGS(LOG),

    RQ(SELF, OTHERS, OPT_OUT),
    RS(SELF, OPT_OUT),

    ADDDISABLED(SELF),
    REMDISABLED(SELF),

    FS,

    SEARCH(NO_ARGS),
    SEARCHUSER(SELF, OTHERS),

    ADDALT,
    NAMBAN,

    SC(ADMIN_ONLY),

    LASTSEEN(ONLINE, OTHERS),
    LS(LASTSEEN),

    MCOUNT(ONLINE, SELF, OTHERS),
    ;


    val isOnlineAllowed: Boolean
    val isSelfAllowed: Boolean
    val isOthersAllowed: Boolean
    val isAdminOnly: Boolean
    val canOptOut: Boolean
    val isNoArgs: Boolean

    constructor(vararg features: Feature) {
        var featureSet: EnumSet<Feature> = EnumSet.noneOf(Feature::class.java)

        if (!features.isEmpty()) {
            featureSet = EnumSet.copyOf<Feature>(listOf<Feature>(*features))
        }

        this.isOnlineAllowed = featureSet.contains(ONLINE)
        this.isSelfAllowed = featureSet.contains(SELF)
        this.isOthersAllowed = featureSet.contains(OTHERS)
        this.isAdminOnly = featureSet.contains(ADMIN_ONLY)
        this.canOptOut = featureSet.contains(OPT_OUT)
        this.isNoArgs = featureSet.contains(NO_ARGS)
    }

    constructor(alias: Command) {
        this.isOnlineAllowed = alias.isOnlineAllowed
        this.isSelfAllowed = alias.isSelfAllowed
        this.isOthersAllowed = alias.isOthersAllowed
        this.isAdminOnly = alias.isAdminOnly
        this.canOptOut = alias.canOptOut
        this.isNoArgs = alias.isNoArgs
    }

    fun isOptedOut(username: String, optOutList: MutableSet<String>): Boolean {
        return canOptOut && optOutList.contains(username)
    }

    fun isUserCommandSpecified(userPermissionMap: MutableMap<String, Boolean>): Boolean? {
        return userPermissionMap.getOrDefault(toString().lowercase(), null)
    }

    internal enum class Feature {
        ONLINE,
        SELF,
        OTHERS,
        ADMIN_ONLY,
        OPT_OUT,
        NO_ARGS,
    }
}
