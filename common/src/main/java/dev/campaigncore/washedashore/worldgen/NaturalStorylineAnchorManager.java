package dev.campaigncore.washedashore.worldgen;

import dev.campaigncore.washedashore.act.WashedAshoreLayoutGenerator;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;
import dev.campaigncore.CampaignCore;

/** Detects generated anchors near players and hands them to the normal server-thread reinstance path. */
public final class NaturalStorylineAnchorManager {
    private static final Set<Long> checkedChunks=new HashSet<>();
    private static ServerLevel checkedLevel;
    private NaturalStorylineAnchorManager(){}
    public static void tick(ServerLevel level,WashedAshoreSavedData data){
        if(level!=level.getServer().overworld()||level.getGameTime()%40!=0)return;
        if(checkedLevel!=level){checkedLevel=level;checkedChunks.clear();}
        for(ServerPlayer player:level.players()){
            ChunkPos center=player.chunkPosition();
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                ChunkPos chunk=new ChunkPos(center.x+dx,center.z+dz);long key=chunk.toLong();
                if(!level.hasChunk(chunk.x,chunk.z)||!checkedChunks.add(key))continue;
                BlockPos anchor=findAnchor(level,chunk);if(anchor==null||data.naturalAnchorProcessed(anchor))continue;
                data.markNaturalAnchorProcessed(anchor);
                try{
                    String failure=WashedAshoreLayoutGenerator.createReinstance(level,data,anchor,true);
                    if(failure==null)player.sendSystemMessage(Component.translatable("message.campaign_core.natural_anchor_discovered"));
                    else CampaignCore.LOGGER.info("natural_storyline_anchor_rejected pos={} reason={}",anchor,failure);
                }catch(RuntimeException ex){CampaignCore.LOGGER.error("natural_storyline_anchor_failed pos={}",anchor,ex);}
            }
        }
    }
    private static BlockPos findAnchor(ServerLevel level,ChunkPos chunk){
        int minX=chunk.getMinBlockX(),minZ=chunk.getMinBlockZ();
        for(int x=minX;x<minX+16;x++)for(int z=minZ;z<minZ+16;z++){
            BlockPos top=level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE,new BlockPos(x,0,z));
            for(int y=top.getY();y>=top.getY()-5;y--){BlockPos pos=new BlockPos(x,y,z);if(isAnchor(level,pos))return pos;}
        }
        return null;
    }
    private static boolean isAnchor(ServerLevel level,BlockPos pos){
        if(!level.getBlockState(pos).is(Blocks.LODESTONE)||!level.getBlockState(pos.below()).is(Blocks.CHISELED_SANDSTONE))return false;
        BlockPos floor=pos.below(2);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!level.getBlockState(floor.offset(x,0,z)).is(Blocks.SMOOTH_SANDSTONE))return false;
        return true;
    }
}
