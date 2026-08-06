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
    private static boolean showLandmarks=true;
    private static final Properties properties=new Properties();
    private CampaignClientConfig(){}
    public static boolean showAvailableQuestsOnServerJoin(){return showAvailableQuestsOnServerJoin;}
    public static void load(){
        if(Files.exists(PATH))try(Reader reader=Files.newBufferedReader(PATH)){properties.load(reader);}
        catch(IOException ex){CampaignCore.LOGGER.warn("client_config_read_failed path={}",PATH,ex);}
        showAvailableQuestsOnServerJoin=Boolean.parseBoolean(properties.getProperty("showAvailableQuestsOnServerJoin","true"));
        showLandmarks=Boolean.parseBoolean(properties.getProperty("showLandmarks","true"));
        properties.setProperty("showAvailableQuestsOnServerJoin",Boolean.toString(showAvailableQuestsOnServerJoin));
        properties.setProperty("showLandmarks",Boolean.toString(showLandmarks));
        save();
    }
    public static ResourceLocation trackedMarker(){return ResourceLocation.tryParse(properties.getProperty("trackedMarker",""));}
    public static void setTrackedMarker(ResourceLocation id){
        if(id==null)properties.remove("trackedMarker");else properties.setProperty("trackedMarker",id.toString());
        save();
    }
    public static boolean showLandmarks(){return showLandmarks;}
    public static void setShowLandmarks(boolean show){showLandmarks=show;properties.setProperty("showLandmarks",Boolean.toString(show));save();}
    private static void save(){try{
            Files.createDirectories(PATH.getParent());
            try(Writer writer=Files.newBufferedWriter(PATH)){properties.store(writer,"Campaign Core client settings");}
        }catch(IOException ex){CampaignCore.LOGGER.warn("client_config_write_failed path={}",PATH,ex);}
    }
}
