package me.camm.productions.fortressguns.Handlers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import me.camm.productions.fortressguns.Artillery.Entities.Abstract.Construct;
import org.bukkit.Chunk;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;



import java.util.*;

/**
 * @author CAMM
 */
public class ChunkLoader implements Listener
{
    private final static Table<Integer, Integer, Map<String, Set<Construct>>> pieces;

    static {
      pieces = HashBasedTable.create();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event){

        Chunk chunk = event.getChunk();
        int x, z;
        x = chunk.getX();
        z = chunk.getZ();

        if (!pieces.contains(x,z))
            return;

        Map<String, Set<Construct>> map = pieces.get(x,z);
        String name = event.getWorld().getName();

        if (!map.containsKey(name))
            return;

        Set<Construct> set = map.get(name);


        for (Construct next : set) {

            Set<Chunk> loaders = next.getLoaders();
            boolean loaded = loaders.stream().allMatch(Chunk::isLoaded);

            if (loaded) {
                next.spawn();
                next.setChunkLoaded(true);
            }
        }
    }

    public void add(Chunk chunk, Construct construct){

        int x = chunk.getX();
        int z = chunk.getZ();

        Set<Construct> set;
        Map<String, Set<Construct>> map;
        String name = chunk.getWorld().getName();
        if (pieces.contains(x,z)) {


            map = pieces.get(x,z);
            if (! map.containsKey(name)) {
                set = new HashSet<>();
                set.add(construct);
                map.put(name, set);
            }
            else {
                set = map.get(name);
                set.add(construct);
            }
        }
        else
        {
            set = new HashSet<>();
            map = new HashMap<>();
            set.add(construct);
            map.put(name,set);
            pieces.put(x,z,map);
        }

    }

    //remove an artillery from the set
    public void remove(Set<Chunk> chunks, Construct construct){


        for (Chunk chunk : chunks) {
            int x = chunk.getX();
            int z = chunk.getZ();

            if (!pieces.contains(x, z))
                continue;

            Map<String, Set<Construct>> map = pieces.get(x, z);

            String name = chunk.getWorld().getName();

            if (!map.containsKey(name))
                continue;

            Set<Construct> set = map.get(name);
            set.remove(construct);
        }

    }


    //should be good
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event){

        Chunk chunk = event.getChunk();
        int x = chunk.getX();
        int z = chunk.getZ();
        String name = chunk.getWorld().getName();

        if (!pieces.contains(x,z)) {
            return;
        }

        Map<String, Set<Construct>> map = pieces.get(x,z);

        if (!map.containsKey(name)) {
            return;
        }

        Set<Construct> set = map.get(name);


        for (Construct next : set) {
            next.unload(false, false);
            next.setChunkLoaded(false);
        }
    }
}
