package me.camm.productions.fortressguns.Artillery.Entities.Abstract;


import me.camm.productions.fortressguns.Artillery.Entities.Abstract.Properties.Rideable;
import me.camm.productions.fortressguns.Artillery.Entities.Components.ArtilleryCore;
import me.camm.productions.fortressguns.Artillery.Entities.Components.ArtilleryPart;
import me.camm.productions.fortressguns.Artillery.Entities.Components.ArtilleryType;
import me.camm.productions.fortressguns.Artillery.Projectiles.ArtilleryProjectile;
import me.camm.productions.fortressguns.Artillery.Projectiles.HeavyShell.ExplosiveHeavyShell;
import me.camm.productions.fortressguns.Artillery.Projectiles.HeavyShell.FlakHeavyShell;
import me.camm.productions.fortressguns.Artillery.Projectiles.HeavyShell.StandardHeavyShell;
import me.camm.productions.fortressguns.Artillery.Projectiles.LightShell.CRAMShell;
import me.camm.productions.fortressguns.Artillery.Projectiles.LightShell.FlakLightShell;
import me.camm.productions.fortressguns.Artillery.Projectiles.LightShell.StandardLightShell;
import me.camm.productions.fortressguns.Artillery.Projectiles.Missile.SimpleMissile;
import me.camm.productions.fortressguns.ArtilleryItems.AmmoItem;
import me.camm.productions.fortressguns.ArtilleryItems.ArtilleryItemHelper;
import me.camm.productions.fortressguns.Inventory.Abstract.ConstructInventory;
import me.camm.productions.fortressguns.Inventory.Abstract.InventoryCategory;
import me.camm.productions.fortressguns.Inventory.Abstract.InventoryGroup;
import me.camm.productions.fortressguns.Util.DamageSource.GunSource;
import me.camm.productions.fortressguns.FortressGuns;
import me.camm.productions.fortressguns.Handlers.ChunkLoader;
import me.camm.productions.fortressguns.Util.ExplosionEffect;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.network.protocol.game.PacketPlayOutPosition;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.player.EntityHuman;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/*
@author CAMM
Abstract class for the artillery pieces
Superclass for all complex entities which are artillery pieces
 */
public abstract class Artillery extends Construct {


    protected int baseLength;

    protected volatile int ammo;
    protected AmmoItem loadedAmmoType;

    protected double vertRotSpeed = 1;
    protected double horRotSpeed = 1;
    protected Plugin plugin;
    protected ArtilleryPart[] barrel;
    protected ArtilleryPart[][] base;

    protected ArtilleryCore pivot;
    protected EulerAngle aim;//
    protected volatile EulerAngle interpolatedAim;
    protected volatile boolean cameraLocked;
    protected volatile boolean interpolating;


    protected final ChunkLoader handler;

    protected volatile double health;//--
    private final Set<Chunk> occupiedChunks;//

    protected Location loc; //
    protected World world;//

    protected boolean dead;//
    protected boolean loaded;

    protected volatile double largeBlockDist;//
    protected volatile boolean lengthChanged;
    protected volatile double smallBlockDist;//

    protected volatile boolean canFire;

    protected int recoilTime = 1;
    protected double barrelRecoverRate = 0.03;

    protected InventoryGroup interactionInv;

    protected long lastFireTime;//

    protected final static double [] offsetX;
    protected final static double [] offsetZ;

    protected double vibrationOffsetY;

    protected static boolean requiresReloading;


    protected final static Set<PacketPlayOutPosition.EnumPlayerTeleportFlags> flags;

    static {

         requiresReloading = false;

        flags = new HashSet<>(Arrays.asList(
                PacketPlayOutPosition.EnumPlayerTeleportFlags.a,
                PacketPlayOutPosition.EnumPlayerTeleportFlags.b,
                PacketPlayOutPosition.EnumPlayerTeleportFlags.c,
                PacketPlayOutPosition.EnumPlayerTeleportFlags.d)
        );

        final int LEN = 12;
        offsetX = new double[LEN];
        offsetZ = new double[LEN];

        int slot = 0;
        for (double rotation = 0; rotation < 2*Math.PI; rotation += (2*Math.PI / LEN)) {
            double z = Math.cos(rotation);
            double x = -Math.sin(rotation);
            offsetX[slot] = x;
            offsetZ[slot] = z;

            slot ++;
        }
    }


    //Enum for damage multipliers
    private enum DamageMultiplier {
        EXPLOSION(2),
        FIRE(1.5),
        GUN(1.2),
        MAGIC(0.01),
        DEFAULT(0.3);

        final double multiplier;

        DamageMultiplier(double mult){
            this.multiplier = mult;
        }
    }


    public Artillery(Location loc, World world, ChunkLoader loader, EulerAngle aim) {

        this.plugin = FortressGuns.getInstance();
        this.loc = loc;
        this.world = world;
        this.lastFireTime = System.currentTimeMillis();
        this.handler = loader;


        this.occupiedChunks = new HashSet<>();
        largeBlockDist = LARGE_BLOCK_LENGTH;
        smallBlockDist = SMALL_BLOCK_LENGTH;
        health = 0;
        dead = false;
        cameraLocked = false;
        interpolating = false;
        this.aim = aim;
        this.interpolatedAim = aim;
        this.canFire = true;
        this.ammo = 0;
        this.vibrationOffsetY = 0;
        lengthChanged = false;
        loadedAmmoType = null;

        initInventories();
    }

    protected synchronized void setCanFire(boolean canFire) {
        this.canFire = canFire;
    }

    protected synchronized boolean getCanFire() {
        return canFire;
    }

    protected abstract void initInventories();


    public boolean requiresReloading() {
        return requiresReloading;
    }


    public int getAmmo() {
        return ammo;
    }

    public synchronized void setInterpolatedAim(EulerAngle angle) {
        double x = angle.getX();
        x = Math.max(getMaxVertAngle(), x);
        x = Math.min(getMinVertAngle(), x);

        final double TWO_PI = 2*Math.PI;
        double y = angle.getY();

        if (x > TWO_PI || x < -TWO_PI) {
            x = x % TWO_PI;
        }

        final double NINETY_DEG = Math.PI / 2;
        if (y > NINETY_DEG || y < -NINETY_DEG) {
            y = y % NINETY_DEG;
        }


        this.interpolatedAim = new EulerAngle(x, y, 0);
    }

    public synchronized EulerAngle getInterpolatedAim() {
        return interpolatedAim;
    }


    public synchronized boolean isCameraLocked() {
        return cameraLocked;
    }

    public synchronized void setCameraLocked(boolean cameraLocked) {
        this.cameraLocked = cameraLocked;
    }

    public static void setRequiresReloading(boolean requiresReloading) {
        Artillery.requiresReloading = requiresReloading;
    }


    public abstract int getMaxAmmo();

    public List<ArtilleryPart> getParts() {
        List<ArtilleryPart> parts = new ArrayList<>(Arrays.asList(barrel));
        for (ArtilleryPart[] segment: base)
            parts.addAll(Arrays.asList(segment));

        return parts;
    }

    protected double getMaxVertAngle() {
        return -1.57;
    }

    protected double getMinVertAngle() {
        return 1.57;
    }

    public synchronized void setAmmo(int ammo) {
    this.ammo = ammo;
    }

    public AmmoItem getLoadedAmmoType() {
        return loadedAmmoType;
    }

    public void setLoadedAmmoType(AmmoItem loadedAmmoType) {
        this.loadedAmmoType = loadedAmmoType;
    }

    public EulerAngle getAim(){
        return aim;
    }

    public Location getLoc(){
        return loc;
    }

    public ArtilleryPart[][] getBase() {
        return base;
    }

    public InventoryGroup getInventoryGroup() {
        return interactionInv;
    }



    public abstract double getVectorPower();

    public int getBaseLength() {
        if (baseLength <= 0 )
            baseLength = base[0].length;
        return baseLength;
    }



    ///called every tick when the player is riding
    public void rideTick(EntityHuman human) {
        pivot(Math.toRadians(human.getXRot()), Math.toRadians(human.getHeadRotation()));
        double x, y;
        x = Math.round(Math.toDegrees(aim.getX()) * 1000d) / 1000d;
        y = Math.round(Math.toDegrees(aim.getY()) * 1000d) / 1000d;
        double roundHealth = Math.round(health * 100d) / 100d;
        Player player = (Player)(human.getBukkitEntity());

        if (canFire()) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GREEN+"Rotation: ["+x +" | "+y+"] Health: "+roundHealth));
        }
        else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Rotation: ["+x+" | "+y+"] Health: " + roundHealth));
        }
    }


    public synchronized void setInterpolating(boolean interpolating) {
        this.interpolating = interpolating;
    }

    public void startPivotInterpolation() {

        if (interpolating) {
            return;
        }

        interpolating = true;
        new BukkitRunnable() {
            @Override
            public void run() {

                if (isCameraLocked()) {
                    cancel();
                    return;
                }

                if (isInvalid() || !chunkLoaded()) {
                    cancel();
                    return;
                }

                double x,y;
                x = interpolatedAim.getX();
                y = interpolatedAim.getY();

                //
                final double POINT_ONE_RAD = 0.0017;   ///0.1 degrees -> rads = ~0.0017


                double currX = aim.getX();
                final double TWO_PI = 2*Math.PI;

                double diffX = Math.abs(x - currX) % TWO_PI;
                double accX = 1 - Math.abs(x - currX) / TWO_PI;

                double diffY = Math.abs((y - aim.getY())) % TWO_PI;
                double accY = 1 - Math.abs(y - aim.getY()) / TWO_PI;

                boolean closeEnough = (diffX < POINT_ONE_RAD && diffY < POINT_ONE_RAD)
                        || (accX < 0.1 && accY < 0.1);

                if (closeEnough && !lengthChanged) {
                    setInterpolating(false);
                    cancel();
                    return;
                }

                pivot(x,y);

                ConstructInventory inv = getInventoryGroup().getInventoryByCategory(InventoryCategory.MENU);
                if (inv != null)
                    inv.updateState();

            }
        }.runTaskTimer(FortressGuns.getInstance(), 0, 1);

    }

    /*
    @param vertAngle, horAngle
    vertAngle: The vertical angle of the aim (in rads)
    horAngle: The horizontal angle of the aim (in rads)

     */
    public synchronized void pivot(double vertAngle, double horAngle) //v = h.xRot  h = h.gHeadRot
    {
        if (dead)
            return;

        if (isInvalid()) {
            remove(false, true);
            dead = true;
            return;
        }


        double currX,currY;
        currX = aim.getX();
        currY = aim.getY();


        ///90 degrees in the up and down directions
        //in radians

        if (vertAngle <= getMaxVertAngle()) {
            vertAngle = Math.max(getMaxVertAngle(), vertAngle);
        }

        if (vertAngle >= getMinVertAngle()) {
            vertAngle = Math.min(vertAngle, getMinVertAngle());
        }

        final double TWO_PI = 2 * Math.PI;
        if (horAngle > TWO_PI || horAngle < -TWO_PI) {
            horAngle = horAngle % TWO_PI;
        }


        if (currX == vertAngle && currY == horAngle && !lengthChanged)
            return;

        lengthChanged = false;

        vertAngle = nextVerticalAngle(currX, vertAngle, vertRotSpeed);


        //don't add PI to give an extra 180 * to the rotation (see StandHelper.getASFace(EntityHuman) )
        //since  -horizontalDistance*Math.sin(horAngle); already takes care of it.
        horAngle = nextHorizontalAngle(currY, horAngle, horRotSpeed);

        if (this instanceof Rideable)
            ((Rideable)this).positionSeat();


        aim = new EulerAngle(vertAngle,horAngle,0);
        pivot.setRotation(aim);

        //for all of the armorstands making up the barrel,
        for (int slot=0;slot< barrel.length;slot++)
        {
            ArtilleryPart barrelComponent = barrel[slot];

            double totalDistance;

            //getting the distance from the pivot
            if (barrelComponent.isSmall())
                totalDistance = (largeBlockDist *0.75 + 0.5* smallBlockDist) + (slot * smallBlockDist);
            else
                totalDistance = (slot+1)* largeBlockDist;


            //height of the aim
            double height = -totalDistance*Math.sin(vertAngle);

            //hor dist of the aim component
            double horizontalDistance = totalDistance*Math.cos(vertAngle);



            //x and z distances relative to the pivot from total hor distance
            double z = horizontalDistance*Math.cos(horAngle);
            double x = -horizontalDistance*Math.sin(horAngle);
            //the - is to account for the 180* between players and armorstands

            //setting the rotation of all of the barrel armorstands.
            barrelComponent.setRotation(aim);

            Location centre = pivot.getLocation(world).clone();

            //teleporting the armorstands to be in line with the pivot
            if (barrelComponent.isSmall()) {
                Location teleport = centre.add(x, height + 0.75, z);
                barrelComponent.teleport(teleport.getX(),teleport.getY(),teleport.getZ());
            }
            else {
                Location teleport = centre.clone().add(x, height, z);
                barrelComponent.teleport(teleport.getX(),teleport.getY(),teleport.getZ());
            }
        }
    }

    public double[] getBasePositions(double radian){
        double distance = LARGE_BLOCK_LENGTH;
        double z = distance*Math.cos(radian);
        double x = -distance*Math.sin(radian);
        return new double[]{x,z};
    }

    public boolean chunkLoaded(){
        return loaded;
    }

    public void setChunkLoaded(boolean loaded){
        this.loaded = loaded;
    }

    public abstract void fire(@Nullable Player shooter);

    public synchronized double getVibrationOffsetY(){
        return vibrationOffsetY;
    }

    public synchronized void setVibrationOffsetY(double vib) {
        this.vibrationOffsetY = vib;
    }





    //to spawn an artillery piece, an external method calls spawn(). In the actual artillery classes however, the artillery
    // is actually created when the spawnParts method is called, and loaded into the world (drawn) when loadPieces() is called
    public final boolean spawn() {
        dead = false;
        loaded = true;

        boolean spawned = spawnParts();
        if (spawned)
            loadPieces();

        //see entity.inBlock()
        /*
        public boolean inBlock() {
        if (this.P) {
            return false;
        } else {
            float f = this.aW.a * 0.8F;
            AxisAlignedBB axisalignedbb = AxisAlignedBB.a(this.bb(), (double)f, 1.0E-6, (double)f);
            return this.t.b(this, axisalignedbb, (iblockdata, blockposition) -> {
                return iblockdata.o(this.t, blockposition);
            }).findAny().isPresent();
        }
    }
         */

        return spawned;
    }

    public abstract ArtilleryType getType();
    public abstract boolean canFire();
    public abstract double getMaxHealth();

    public abstract boolean acceptsAmmo(AmmoItem item);


    protected void vibrateParticles() {
        ArtilleryCore core = this.getPivot();
        Location loc = core.getEyeLocation();
        Location initial = loc.clone().add(0,-LARGE_BLOCK_LENGTH,0);

        for (int len = 0; len < offsetX.length; len ++) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, initial,0,offsetX[len],0,offsetZ[len],0.3f);

        }
    }

    protected List<Player> getVibratedPlayers() {
        Set<Chunk> occupied = getOccupiedChunks();

        final List<Player> vibrateFor = new LinkedList<>();
        for (Chunk c: occupied) {
            org.bukkit.entity.Entity[] entities = c.getEntities();

            for (org.bukkit.entity.Entity e: entities) {
                if (e instanceof Player && e.getVehicle() == null)
                    vibrateFor.add((Player)e);
            }
        }
        return vibrateFor;
    }

    protected void vibrateAnimation(List<Player> vibrateFor, int ticks, double mult) {
        double offset = Math.sin(Math.PI + (0.5d * ticks * Math.PI)) * Math.max(-0.12 * ticks + 1, 0) * mult;
        if (offset != 0) {
            setVibrationOffsetY(offset);
            for (Player observer : vibrateFor) {

                if (observer.getVehicle() != null)
                    continue;
                //0.017 ==> 1 rad
                EntityPlayer nms = ((CraftPlayer)observer).getHandle();

                PacketPlayOutPosition packet = new PacketPlayOutPosition(0,0,0,
                        0,
                        nms.getXRot() + ( (float) offset),
                        flags, 1, true);

                nms.b.sendPacket(packet);
            }
        }
        else {
            setVibrationOffsetY(0);
        }
    }


    protected void loadPieces() {
        List<ArtilleryPart> parts = getParts();

        net.minecraft.world.level.World nmsWorld = ((CraftWorld)world).getHandle();
        for (ArtilleryPart part: parts) {
            nmsWorld.addEntity(part, CreatureSpawnEvent.SpawnReason.CUSTOM);
        }
    }

    public final boolean isInvalid(){
        return (pivot == null || (!pivot.isAlive()) || health <= 0 || dead);
    }

    public final synchronized void unload(boolean dropItem, boolean exploded) throws IllegalStateException {


        List<ArtilleryPart> components = getParts();
        try {
            components.forEach(Entity::die);
        }
        catch (NullPointerException ignored) {

        }

        Location loc;
        if (pivot != null)
            loc = pivot.getLocation(world).clone();
        else return;

        if (exploded) {
            ExplosionEffect.explodeArtillery(loc, world);
        }

        if (dropItem) {
            ArtilleryItemHelper.packageArtillery(this);
        }

    }

    public final synchronized void remove(boolean dropItem, boolean exploded) throws IllegalStateException
    {
        handler.remove(occupiedChunks, this);
        unload(dropItem, exploded);
    }


    public ArtilleryCore getPivot(){
        return pivot;
    }



    public final void createFlash(Location origin) {


       List<Player> players = world.getPlayers();
       BlockData lightData = Material.LIGHT.createBlockData();
       BlockData airData = Material.AIR.createBlockData();
       final Location flash = origin.clone();

        world.spawnParticle(Particle.FLASH,origin.getX(),origin.getY(), origin.getZ(),1,0,0,0,0.2);


       players.forEach(player -> player.sendBlockChange(flash, lightData));


       new BukkitRunnable() {
           public void run() {
               players.forEach(player -> player.sendBlockChange(flash, airData));

           }
       }.runTaskLater(FortressGuns.getInstance(), 5);


    }


    public void createShotParticles(Location muzzle){
        world.spawnParticle(Particle.SMOKE_LARGE,muzzle.getX(),muzzle.getY(), muzzle.getZ(),30,0,0,0,0.2);
        world.spawnParticle(Particle.FLASH,muzzle.getX(),muzzle.getY(), muzzle.getZ(),1,0,0,0,0.2);

        world.playSound(muzzle, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR,SoundCategory.BLOCKS,2,0.2f);
        world.playSound(muzzle, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,SoundCategory.BLOCKS,0.2f,0.2f);
        world.playSound(muzzle, Sound.ITEM_ARMOR_EQUIP_GENERIC,SoundCategory.BLOCKS,1f,0.2f);

    }


    public World getWorld(){
        return world;
    }

    public final Set<Chunk> getOccupiedChunks(){
        return occupiedChunks;
    }

    public boolean damage(DamageSource source, float damage){

        if (source.isExplosion()) {
            damage *= DamageMultiplier.EXPLOSION.multiplier;
        }
        else if (source.isFire()) {
            damage *= DamageMultiplier.FIRE.multiplier;
        }
        else if (source instanceof GunSource) {
            damage *= DamageMultiplier.GUN.multiplier;
        }
        else if (source.isMagic()) {
            damage *= DamageMultiplier.MAGIC.multiplier;
        }
        else
             damage *= DamageMultiplier.DEFAULT.multiplier;

        setHealth(this.health - damage);
        if (health <= 0) {
            remove(false, true);
            return false;
        }
        return true;
    }

    public void playSound(ArtilleryPart part){
       world.playSound(part.getLocation(world),part.getSoundHurt(), SoundCategory.BLOCKS,1,1);
    }

     public double getHealth(){
        return health;
     }

    public final synchronized void setHealth(double health){
        this.health = health;
    }


    /*
These methods create the artillery components but don't spawn them.
see: loadPieces()
 */
    protected abstract boolean spawnParts();
    protected abstract boolean spawnBaseParts();
    protected abstract boolean spawnTurretParts();


    protected final void calculateLoadedChunks(){
        double totalDistanceBarrel = (LARGE_BLOCK_LENGTH * 0.75 + 0.5 * SMALL_BLOCK_LENGTH) + (barrel.length * SMALL_BLOCK_LENGTH);
        double totalDistanceBase = 0;

        for (EntityArmorStand[] array: base)
            if (array.length > totalDistanceBase)
                totalDistanceBase = array.length;

        double totalDistance = Math.max(totalDistanceBarrel,(LARGE_BLOCK_LENGTH*totalDistanceBase));
        double circle = Math.PI * 2;
        Location loc = pivot.getLocation(world).clone();

        for (double rads=0;rads < circle;rads+= Math.PI/4) {

            double z = totalDistance * Math.cos(rads);
            double x = -totalDistance * Math.sin(rads);

            Chunk chunk = world.getChunkAt((loc.getBlockX()+(int)x) >> 4, (loc.getBlockZ()+(int)z) >> 4);
            occupiedChunks.add(chunk);
        }
    }


    protected @Nullable ArtilleryProjectile createProjectile(net.minecraft.world.level.World world, double x, double y, double z, EntityPlayer shooter, Artillery source) {
        AmmoItem item = getLoadedAmmoType();
        if (item == null) {
            System.out.println("loaded type is null");
            return null;
        }
        ///well I don't really like making a switch for every single flipping thing cause I then gotta
        // go back and change it everytime I make a new thing, but if it's faster than
        //reflection okay sure (mainly cause we might be rapidly creating these things so...)
        //I guess a better way could be to make the specific class determine what to make- especially if there's
        // a lot of options...that might cut the time down
        ArtilleryProjectile projectile;

        switch (item) {
            case STANDARD_HEAVY -> projectile = new StandardHeavyShell(world, x, y, z, shooter, source);
            case EXPLOSIVE_HEAVY -> projectile = new ExplosiveHeavyShell(world, x, y, z, shooter, source);
            case STANDARD_LIGHT -> projectile = new StandardLightShell(world, x, y, z, shooter, source);
            case FLAK_LIGHT -> projectile = new FlakLightShell(world, x, y, z, shooter, source);
            case MISSILE -> projectile = new SimpleMissile(world, x, y, z, shooter, source);
            case FLAK_HEAVY -> projectile = new FlakHeavyShell(world, x, y, z, shooter, source);
            case CRAM -> projectile = new CRAMShell(world, x, y, z, shooter, source);
            default -> {
                try {
                    ///plan b
                    projectile = item.getProjClass()
                            .getConstructor(net.minecraft.world.level.World.class, Double.class, Double.class, Double.class, EntityPlayer.class, Artillery.class)
                            .newInstance(world, x, y, z, shooter, source);
                } catch (Exception e) {
                    FortressGuns.getInstance().getLogger().warning("Could not create artillery projectile: "+item.getName());
                    return null;
                }
            }
        }
        return projectile;

    }


}
