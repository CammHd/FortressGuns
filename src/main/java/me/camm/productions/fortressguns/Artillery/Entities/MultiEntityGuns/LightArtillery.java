package me.camm.productions.fortressguns.Artillery.Entities.MultiEntityGuns;

import me.camm.productions.fortressguns.Artillery.Entities.Abstract.FieldArtillery;
import me.camm.productions.fortressguns.Artillery.Entities.Components.ArtilleryPart;
import me.camm.productions.fortressguns.Artillery.Entities.Components.ArtilleryType;
import me.camm.productions.fortressguns.Handlers.ChunkLoader;
import me.camm.productions.fortressguns.Util.StandHelper;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LightArtillery extends FieldArtillery
{

    private static final double HEALTH;
    private static final long FIRE_COOLDOWN;

    static {
        HEALTH = 40;
        FIRE_COOLDOWN = 1000;
    }


    public LightArtillery(Location loc, World world, ChunkLoader loader, EulerAngle aim) {
        super(loc, world,loader,aim);
        barrel = new ArtilleryPart[5];
        base = new ArtilleryPart[3][3];
    }

    public synchronized boolean canFire(){
        return canFire && System.currentTimeMillis()-lastFireTime >= FIRE_COOLDOWN;
    }

    @Override
    public ArtilleryType getType() {
        return ArtilleryType.FIELD_LIGHT;
    }

    @Override
    public double getMaxHealth() {
        return HEALTH;
    }

    @Override
    protected void spawnParts()
    {

        pivot = StandHelper.getCore(loc, BODY, aim, world, this);
        pivot.setRotation(aim);
        rotatingSeat = StandHelper.spawnPart(getSeatSpawnLocation(this),SEAT,new EulerAngle(0, aim.getY(),0),world,this);

        for (int slot=0;slot< barrel.length;slot++)
        {
            boolean small = false;
            double totalDistance;

            if (slot>=1) {
                totalDistance = (LARGE_BLOCK_LENGTH * 0.75 + 0.5 * SMALL_BLOCK_LENGTH) + (slot * SMALL_BLOCK_LENGTH);
                small = true;
            }
            else
                totalDistance = (slot+1)* LARGE_BLOCK_LENGTH;

            double height = -totalDistance*Math.sin(aim.getX());
            double horizontalDistance = totalDistance*Math.cos(aim.getX());

            double z = horizontalDistance*Math.cos(aim.getY());
            double x = -horizontalDistance*Math.sin(aim.getY());

            Location centre = pivot.getLocation(world).clone();
            ArtilleryPart stand;

            //if it is small, add 0.75 so that it is high enough
            if (small) {
                stand = StandHelper.spawnPart(centre.add(x, height + 0.75, z), BARREL, aim, world, this);
                stand.setSmall(true);
            }
            else
                stand = StandHelper.spawnPart(centre.add(x, height, z),BODY,aim,world,this);

            stand.setRotation(aim);
            barrel[slot] = stand;
        }


        double rads = -Math.PI/3;
        int bar = 0;


        //for the base of the artillery
        for (ArtilleryPart[] standRow: base) {
            double[] position = getBasePositions(rads);  //get the x, z values for the base

            int length = 0;

            for (int slot=0;slot<standRow.length;slot++) {
                Location loc = pivot.getLocation(this.world).clone().add((position[0]*LARGE_BLOCK_LENGTH+length*position[0]),
                        -0.75, (LARGE_BLOCK_LENGTH*position[1]+length*position[1]));

                //(World world, Artillery body, double d0, double d1, double d2)
                ArtilleryPart part;

                //if the length is close to base, then give it wheels, else give it
                //supports
                if (length >=1)
                    part = StandHelper.spawnPart(loc, SUPPORT,null,world,this);
                else if (bar!=base.length-1)
                    part = StandHelper.spawnPart(loc, WHEEL,null,world, this);
                else
                    part = StandHelper.spawnPart(loc,BODY,null,world, this);

                length ++;
                standRow[slot] = part;
            }
            bar ++;

            if (bar == base.length-1)
                rads = Math.PI; //so that it is directly behind the cannon (facing south)
            else
                rads += 2 * Math.PI / 3;
        }
        initLoadedChunks();
        if (health <= 0)
            setHealth(HEALTH);
      //  pivot(0,0);

    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return loadingInventory.getInventory();
    }

    @Override
    public List<ArtilleryPart> getParts(){
        List<ArtilleryPart> parts = new ArrayList<>(Arrays.asList(barrel));
        for (ArtilleryPart[] segment: base)
            parts.addAll(Arrays.asList(segment));
        parts.add(pivot);
        parts.add(rotatingSeat);
        return parts;

    }


}
