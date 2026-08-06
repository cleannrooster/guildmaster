package dev.campaigncore.config;

import dev.architectury.platform.Platform;
import dev.campaigncore.CampaignCore;
import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/** Normal server/common config loaded before any world-level campaign generation events. */
public final class CampaignServerConfig {
    private static boolean generateInitialActLayout=true;
    private static boolean preferSculkAndScavengeEncounters=true;
    private static boolean preferRpgMinibossesSettlementRaid=true;
    private CampaignServerConfig(){}
    public static boolean generateInitialActLayout(){return generateInitialActLayout;}
    public static boolean preferSculkAndScavengeEncounters(){return preferSculkAndScavengeEncounters;}
    public static boolean preferRpgMinibossesSettlementRaid(){return preferRpgMinibossesSettlementRaid;}
    public static void load(){
        Path path=Platform.getConfigFolder().resolve("campaign_core-server.properties");
        Properties properties=new Properties();
        if(Files.exists(path))try(Reader reader=Files.newBufferedReader(path)){properties.load(reader);}
        catch(IOException ex){CampaignCore.LOGGER.warn("server_config_read_failed path={}",path,ex);}
        generateInitialActLayout=Boolean.parseBoolean(properties.getProperty("generateInitialActLayout","true"));
        preferSculkAndScavengeEncounters=Boolean.parseBoolean(
                properties.getProperty("preferSculkAndScavengeEncounters","true"));
        preferRpgMinibossesSettlementRaid=Boolean.parseBoolean(
                properties.getProperty("preferRpgMinibossesSettlementRaid","true"));
        properties.setProperty("generateInitialActLayout",Boolean.toString(generateInitialActLayout));
        properties.setProperty("preferSculkAndScavengeEncounters",
                Boolean.toString(preferSculkAndScavengeEncounters));
        properties.setProperty("preferRpgMinibossesSettlementRaid",
                Boolean.toString(preferRpgMinibossesSettlementRaid));
        try{
            Files.createDirectories(path.getParent());
            try(Writer writer=Files.newBufferedWriter(path)){properties.store(writer,"Campaign Core server settings");}
            CampaignCore.LOGGER.info(
                    "server_config_loaded path={} generateInitialActLayout={} preferSculkAndScavengeEncounters={} preferRpgMinibossesSettlementRaid={}",
                    path.toAbsolutePath(),generateInitialActLayout,preferSculkAndScavengeEncounters,preferRpgMinibossesSettlementRaid);
        }catch(IOException|SecurityException ex){CampaignCore.LOGGER.warn("server_config_write_failed path={}",path,ex);}
    }
}
