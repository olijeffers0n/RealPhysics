package com.jeff.realphysics;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.jeff.realphysics.utils.GarbageCollector;
import com.jeff.realphysics.utils.Metrics;
import lombok.Data;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * A Custom TNT & Explosion Physics Plugin
 *
 * Credits - olijeffers0n AKA jeffmcjeffers0n
 */

public class RealPhysics extends JavaPlugin implements Listener {

    private ProtocolManager protocolManager;
    private FallingBlockPacketInterceptor packetInterceptor;
    public MidAirVelocityChangePacketInterceptor MidAirInterceptor;
    private NamespacedKey key;

    public int timeUntilGarbageCollector;
    private int garbageCollectorDelay;

    private Set<Material> blockedBlockTypes;
    public final Set<Integer> blockIDs = new HashSet<>();
    public Set<Integer> blockIDsInAir = new HashSet<>();

    @Override
    public void onEnable() {

        // Starts Bstats
        new Metrics(this, 11442);

        // Initialises the garbage collector
        int delay = getConfig().getInt("garbageCollectionDelay");
        if(delay < (getConfig().getInt("upperTickBound") / 20)) delay = getConfig().getInt("upperTickBound") / 20;
        this.timeUntilGarbageCollector = delay;
        this.garbageCollectorDelay = delay;


        new GarbageCollector(this, delay);


        // Serialises the Blocks in the 'blockedBlocks.yml' to the set
        loadBlocks();

        // Loads the Config.yml
        saveDefaultConfig();

        // Creates the NamespacedKey
        this.key = new NamespacedKey(this,"TOBEDESTROYED");


        // Deals with all the ProtocolLib stuff
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.MidAirInterceptor = new MidAirVelocityChangePacketInterceptor(this);
        this.packetInterceptor = new FallingBlockPacketInterceptor(this);
        this.protocolManager.addPacketListener(this.packetInterceptor);
        this.protocolManager.addPacketListener(this.MidAirInterceptor);

        // Registers Events
        Bukkit.getPluginManager().registerEvents(this, this);

        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[RealPhysics] Plugin Is Enabled");
    }

    /*
    Creates all the 'FallingBlocks' when there is an explosion
     */

    @EventHandler
    public void onExplosion(final EntityExplodeEvent event) {

        // Resets Garbage Collector
        this.timeUntilGarbageCollector = this.garbageCollectorDelay;

        final int maxBlocksPerBlock = this.getConfig().getInt("blockMultiplier");
        final int minimumTicks = this.getConfig().getInt("lowerTickBound");
        final int maximumTicks = this.getConfig().getInt("upperTickBound");

        // Deals with more than one TNT causing chain vectors

            List<Entity> entities = event.getEntity().getNearbyEntities(10, 10, 10);
            for (Entity entity : entities) {
                if (entity instanceof FallingBlock) {
                    PersistentDataContainer container = entity.getPersistentDataContainer();
                    if (container.has(this.key, PersistentDataType.STRING)) {
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            this.MidAirInterceptor.addOneMoreBlock(entity.getEntityId());
                        },2L);
                    }
                }
            }

        for (final Block exploded : event.blockList()) {

            if(this.blockedBlockTypes.contains(exploded.getType())) continue;

            BlockData data = exploded.getBlockData();

            for(int i = 1; i <= maxBlocksPerBlock; i++) {

                Vector newV = makeVector(event.getLocation(), exploded.getLocation());

                PendingBlock pending = new PendingBlock(this.key, data, exploded.getLocation(), newV);
                pending.create();
                this.blockIDs.add(pending.id);

                double gaussianOutput = (Math.min(Math.max((ThreadLocalRandom.current().nextGaussian() * 20) + minimumTicks, 1), maximumTicks));
                long delay = (long) gaussianOutput;

                Bukkit.getScheduler().runTaskLater(this, pending::destroy, delay);

            }
        }
    }

    /*
    Used to make the vector for the falling blocks
     */

    private Vector makeVector(Location tntLoc, Location blockLoc) {

        final double Max = 0.75;
        final double Min = 0.25;
        final double x = Math.random() * (Max - Min) + Min;
        final double y = Math.random() * (Max - Min) + Min + 0.3;
        final double z = Math.random() * (Max - Min) + Min;

        Vector difference = blockLoc.add(0.5, 2.5, 0.5).toVector().subtract(tntLoc.toVector()).normalize();

        return difference.multiply(new Vector(x,y,z));
    }

    /*
    Listener for the landing of the falling blocks, checking for both the id, and if that fails the PDC.
    This makes sure they do not form solid blocks
     */

    @EventHandler
    public void onBlockChange(final EntityChangeBlockEvent event) {

        final Entity entity = event.getEntity();
        final int id = entity.getEntityId();

        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (this.blockIDs.remove(id)) {
            event.setCancelled(true);

        }else if(container.has(this.key, PersistentDataType.STRING)){
            event.setCancelled(true);
            this.blockIDs.remove(id);
        }
    }

    /*
    Class used to represent a falling block
    Has the methods 'create' and 'destroy' - Making it easier to use the lambda to destroy it after a set amount of ticks
     */

    @Data
    public class PendingBlock {

        private final NamespacedKey key;
        private final BlockData blockData;
        private final Location location;
        private final Vector vector;

        private int id;

        public void create() {

            final FallingBlock fallingBlock = this.location.getWorld().spawnFallingBlock(this.location, this.blockData);

            this.id = fallingBlock.getEntityId();

            fallingBlock.setHurtEntities(false);
            fallingBlock.setDropItem(false);
            fallingBlock.setVelocity(this.vector);

            fallingBlock.getPersistentDataContainer().set(key, PersistentDataType.STRING, "TOBEDESTROYED");

            blockIDs.add(this.id);
            packetInterceptor.setBlockedOnce(this.id);

            blockIDsInAir.add(this.id);
            MidAirInterceptor.addOneMoreBlock(this.id);

        }

        public void destroy() {
            blockIDs.remove(this.id);
            blockIDsInAir.remove(this.id);
            MidAirInterceptor.removeBlocker(this.id);

            final PacketContainer destroyPacket = new PacketContainer(Server.ENTITY_DESTROY);
            destroyPacket.getIntegerArrays().write(0, new int[]{this.id});
            Bukkit.getOnlinePlayers().forEach(player -> {
                try {
                    RealPhysics.this.protocolManager.sendServerPacket(player, destroyPacket);
                } catch (final InvocationTargetException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    /*
    This is the packet interceptor for the Destroy Packets. It also removes the velocity of the blocks when they land
     */

    private class FallingBlockPacketInterceptor extends PacketAdapter {

        private final Set<Integer> blockedIDs = new HashSet<>();

        public FallingBlockPacketInterceptor(final Plugin plugin) {
            super(plugin, Server.ENTITY_DESTROY);
        }

        public void setBlockedOnce(final int id) {
            this.blockedIDs.add(id);
        }

        @Override
        public void onPacketSending(final PacketEvent event) {
            final PacketContainer packet = event.getPacket();
            final StructureModifier<int[]> arrayMod = packet.getIntegerArrays();
            final int[] ids = arrayMod.read(0);
            for (int index = 0; index < ids.length; index++) {
                final int id = ids[index];
                if (this.blockedIDs.remove(id)) {
                    cancelVelocity(ids[index]);
                    ids[index] = 0;
                }
            }
        }

        /*
        Sends a packet with 0 velocity to the Entity
         */

        public void cancelVelocity(int id) {
            final PacketContainer veloctiyPacket = new PacketContainer(Server.ENTITY_VELOCITY);
            veloctiyPacket.getIntegers()
                    .write(0, id)
                    .write(1, (int) (0 * 8000.0D))
                    .write(2, (int) (0 * 8000.0D))
                    .write(3, (int) (0 * 8000.0D));
            veloctiyPacket.setMeta("CUSTOM", 1);
            Bukkit.getOnlinePlayers().forEach(player -> {
                try {
                    RealPhysics.this.protocolManager.sendServerPacket(player, veloctiyPacket);
                } catch (final InvocationTargetException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    /*
    The Interceptor to stop weird lagging midair
     */

    public class MidAirVelocityChangePacketInterceptor extends PacketAdapter {

        private final Map<Integer, Integer> blockedIDs = new HashMap<>();

        public MidAirVelocityChangePacketInterceptor(final Plugin plugin) {
            super(plugin, Server.ENTITY_VELOCITY);
        }

        public void addOneMoreBlock(final int id) {
            if(!this.blockedIDs.containsKey(id)){
                this.blockedIDs.put(id,1);
            }else{
                int total = this.blockedIDs.get(id);
                this.blockedIDs.replace(id,total+1);
            }
        }

        public void clearBlocker() {
            this.blockedIDs.clear();
        }

        public void removeBlocker(final int id) {
            this.blockedIDs.remove(id);
        }

        @Override
        public void onPacketSending(final PacketEvent event) {
            PacketContainer packet = event.getPacket();
            final int id = packet.getIntegers().read(0);

            if(packet.getMeta("CUSTOM").isPresent()) return;

            if (blockIDsInAir.contains(id)) {

               if (this.blockedIDs.containsKey(id)) {

                   int totalBlocked = this.blockedIDs.get(id);
                   if (totalBlocked == 1) {
                       this.blockedIDs.remove(id);
                   } else {
                       this.blockedIDs.replace(id,totalBlocked - 1);
                   }
                   event.setCancelled(true);
               }
            }
        }
    }

    /*
    Serialises the blocks
     */

    private void loadBlocks(){
        File blockFile = new File(this.getDataFolder(), "blockedBlocks.yml");

        if(!blockFile.exists()) {
            this.saveResource("blockedBlocks.yml", false);
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "blockedBlocks.yml"));

        List<String> blocks = configuration.getStringList("names");

        Set<Material> materials = new HashSet<>();

        for(String block : blocks) {
            try {
                materials.add(Material.valueOf(block));
            }catch (IllegalArgumentException ignored){}
        }
        this.blockedBlockTypes = new HashSet<>(materials);
    }
    
}
