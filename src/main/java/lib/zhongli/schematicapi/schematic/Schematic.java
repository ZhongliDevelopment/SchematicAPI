package lib.zhongli.schematicapi.schematic;

import com.cryptomorin.xseries.XMaterial;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import lombok.Getter;
import me.nullicorn.nedit.NBTReader;
import me.nullicorn.nedit.type.NBTCompound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class Schematic {

    private File location; //The absolute location of the .schem / .schematic file.
    @Getter private String id; //Internal ID, used for some GUI stuff and general tracking.
    @Getter private Location max; //The final pasted position of the schem after `paste()` is called.
    @Getter private Location min; //The final pasted position of the schem after `paste()` is called.
    //The materials which will be scanned for when `scan()` is called.
    //@see scan() function.
    @Getter private List<Material> scanIndex;
    //The map of locations to materials which have been scanned.
    //@see scan() function.
    @Getter private Map<Location, Material> scannedMap;
    @Getter private int xOffset;
    @Getter private int yOffset;
    @Getter private int zOffset;
    @Getter private int width;
    @Getter private int height;
    @Getter private int length;

    public Schematic(File location, String id) {
        this.location = location;
        this.id = id;
        this.scanIndex = new ArrayList<>();
        this.scannedMap = new Hashtable<>();
        readFileMetadata();
    }

    /*
    2 functions to define and undefine the parameters used when scanning a structure.
     */
    public void addToScanIndex(XMaterial material) {
        Material mat = material.parseMaterial();
        scanIndex.add(mat);
    }

    public void removeFromScanIndex(XMaterial material) {
        Material mat = material.parseMaterial();
        scanIndex.remove(mat);
    }

    /*
    This function is only to be run after `paste()` has been called.
    @see paste() function.
     */
    public void scan() {
        int     minX = min.getBlockX(),
                minY = min.getBlockY(),
                minZ = min.getBlockZ(),
                maxX = max.getBlockX(),
                maxY = max.getBlockY(),
                maxZ = max.getBlockZ();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    Location location = new Location(min.getWorld(), x, y, z);
                    Material material = location.getBlock().getType();
                    if (scanIndex.contains(material)) {
                        scannedMap.put(location, material);
                    }
                }
            }
        }
    }

    public void readFileMetadata() {
        try {
            NBTCompound compound = NBTReader.readFile(location);
            NBTCompound offsets = compound.getCompound("Metadata");
            xOffset = offsets.getInt("WEOffsetX", 0);
            yOffset = offsets.getInt("WEOffsetY", 0);
            zOffset = offsets.getInt("WEOffsetZ", 0);
            width = compound.getInt("Width", 0);
            height = compound.getInt("Height", 0);
            length = compound.getInt("Length", 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void paste(World world, Location origin, boolean withScan) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByFile(location);
        ClipboardReader reader = format.getReader(new FileInputStream(location));
        Clipboard clipboard = reader.read();
        com.sk89q.worldedit.world.World aWorld = BukkitAdapter.adapt(world);
        EditSession editSession = WorldEdit.getInstance().newEditSession(aWorld);
        Operation operation = new ClipboardHolder(clipboard).createPaste(editSession)
                .to(BukkitAdapter.asBlockVector(origin)).ignoreAirBlocks(false).build();
        try {
            Operations.complete(operation);

            int originX, originY, originZ;
            originX = origin.getBlockX();
            originY = origin.getBlockY();
            originZ = origin.getBlockZ();
            int trueOriginX = originX + xOffset;
            int trueOriginY = originY + yOffset;
            int trueOriginZ = originZ + zOffset;
            int trueMaxX = trueOriginX + clipboard.getWidth();
            int trueMaxY = trueOriginY + clipboard.getHeight();
            int trueMaxZ = trueOriginZ + clipboard.getLength();

            this.min = new Location(world, trueOriginX, trueOriginY, trueOriginZ);
            this.max = new Location(world, trueMaxX, trueMaxY, trueMaxZ);

            editSession.flushQueue();
        } catch (WorldEditException e) {
            Bukkit.getLogger().warning("[MoonLib] Something went wrong with a WorldEdit paste.");
        }

        if (withScan) scan();
    }

}
