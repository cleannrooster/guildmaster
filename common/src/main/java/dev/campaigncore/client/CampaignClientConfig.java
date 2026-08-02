package dev.campaigncore.client;

import dev.architectury.platform.Platform;
import dev.campaigncore.CampaignCore;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import net.minecraft.resources.ResourceLocation;

public final class CampaignClientConfig {
    private static final Path PATH=Platform.getConfigFolder().resolve("campaign_core-client.properties");
    private static boolean showAvailableQuestsOnServerJoin=true;
    private static final Properties properties=new Properties();
    private CampaignClientConfig(){}
    public static boolean showAvailableQuestsOnServerJoin(){return showAvailableQuestsOnServerJoin;}
    public static void load(){
        if(Files.exists(PATH))try(Reader reader=Files.newBufferedReader(PATH)){properties.load(reader);}
        catch(IOException ex){CampaignCore.LOGGER.warn("client_config_read_failed path={}",PATH,ex);}
        showAvailableQuestsOnServerJoin=Boolean.parseBoolean(properties.getProperty("showAvailableQuestsOnServerJoin","true"));
        properties.setProperty("showAvailableQuestsOnServerJoin",Boolean.toString(showAvailableQuestsOnServerJoin));
        save();
    }
    /** Newly discovered POIs are visible by default; an explicit saved toggle always wins. */
    public static boolean markerVisible(ResourceLocation id){return Boolean.parseBoolean(properties.getProperty("marker."+id,"true"));}
    public static void setMarkerVisible(ResourceLocation id,boolean visible){properties.setProperty("marker."+id,Boolean.toString(visible));save();}
    private static void save(){try{
            Files.createDirectories(PATH.getParent());
            try(Writer writer=Files.newBufferedWriter(PATH)){properties.store(writer,"Campaign Core client settings");}
        }catch(IOException ex){CampaignCore.LOGGER.warn("client_config_write_failed path={}",PATH,ex);}
    }
}
