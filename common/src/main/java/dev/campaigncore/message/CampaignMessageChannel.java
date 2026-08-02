package dev.campaigncore.message;

import java.util.Locale;

public enum CampaignMessageChannel {
    STORY, OBJECTIVE, QUEST, AMBIENT, WARNING, COMPLETION, ANNOUNCEMENT, CHAT;
    public static CampaignMessageChannel parse(String value){
        try{return valueOf(value.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException ex){throw new IllegalArgumentException("unknown message channel '"+value+"'");}
    }
}
