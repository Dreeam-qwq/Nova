package xyz.xenondevs.nova.util

import org.bukkit.Axis
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockFace
import org.bukkit.block.BlockFace.*
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.tileentity.TileEntityManager
import xyz.xenondevs.nova.tileentity.vanilla.VanillaTileEntityManager
import kotlin.math.max
import kotlin.math.min

val CUBE_FACES = listOf(NORTH, EAST, SOUTH, WEST, UP, DOWN)

val Location.blockLocation: Location
    get() = Location(world, blockX.toDouble(), blockY.toDouble(), blockZ.toDouble())

fun Location.dropItems(items: Iterable<ItemStack>) {
    val world = world!!
    items.forEach { world.dropItemNaturally(this, it) }
}

fun Location.removeOrientation() {
    yaw = 0f
    pitch = 0f
}

fun Location.advance(blockFace: BlockFace, stepSize: Double = 1.0) =
    add(
        blockFace.modX.toDouble() * stepSize,
        blockFace.modY.toDouble() * stepSize,
        blockFace.modZ.toDouble() * stepSize
    )

fun Location.getNeighboringTileEntities(): Map<BlockFace, TileEntity> {
    return getNeighboringTileEntitiesOfType()
}

inline fun <reified T> Location.getNeighboringTileEntitiesOfType(): Map<BlockFace, T> {
    val tileEntities = HashMap<BlockFace, T>()
    CUBE_FACES.forEach {
        val location = blockLocation.advance(it)
        val tileEntity = TileEntityManager.getTileEntityAt(location)
            ?: VanillaTileEntityManager.getTileEntityAt(location)
        if (tileEntity != null && tileEntity is T) tileEntities[it] = tileEntity as T
    }
    
    return tileEntities
}

fun Location.castRay(stepSize: Double, maxDistance: Double, run: (Location) -> Boolean) {
    val vector = direction.multiply(stepSize)
    val location = clone()
    var distance = 0.0
    while (run(location)) {
        location.add(vector)
        distance += stepSize
        if (distance > maxDistance) break
    }
}

fun Chunk.getSurroundingChunks(range: Int, includeCurrent: Boolean): List<Chunk> {
    val chunks = ArrayList<Chunk>()
    val world = world
    for (chunkX in (x - range)..(x + range)) {
        for (chunkZ in (z - range)..(z + range)) {
            val chunk = world.getChunkAt(chunkX, chunkZ)
            if (chunk != this || includeCurrent)
                chunks += world.getChunkAt(chunkX, chunkZ)
        }
    }
    
    return chunks
}

fun Location.untilHeightLimit(includeThis: Boolean, run: (Location) -> Boolean) {
    val heightLimit = world!!.maxHeight
    val location = clone().apply { if (!includeThis) add(0.0, 1.0, 0.0) }
    while (location.y < heightLimit) {
        if (!run(location)) break
        
        location.add(0.0, 1.0, 0.0)
    }
}

fun Location.positionEquals(other: Location) =
    world == other.world
        && x == other.x
        && y == other.y
        && z == other.z

fun Location.center() = add(0.5, 0.0, 0.5)

fun Location.setCoordinate(axis: Axis, coordinate: Double) =
    when (axis) {
        Axis.X -> x = coordinate
        Axis.Y -> y = coordinate
        Axis.Z -> z = coordinate
    }

fun Location.getCoordinate(axis: Axis): Double =
    when (axis) {
        Axis.X -> x
        Axis.Y -> y
        Axis.Z -> z
    }

fun Location.getStraightLine(axis: Axis, to: Int): List<Location> {
    val min = min(getCoordinate(axis).toInt(), to)
    val max = max(getCoordinate(axis).toInt(), to)
    
    return (min..max).map { coordinate -> clone().apply { setCoordinate(axis, coordinate.toDouble()) } }
}

fun Location.getRectangle(to: Location, omitCorners: Boolean): Map<Axis, List<Location>> {
    val rectangle = HashMap<Axis, List<Location>>()
    val listX = ArrayList<Location>().also { rectangle[Axis.X] = it }
    val listZ = ArrayList<Location>().also { rectangle[Axis.Z] = it }
    
    val modifier = if (omitCorners) 1 else 0
    
    val minX = min(blockX, to.blockX)
    val minZ = min(blockZ, to.blockZ)
    val maxX = max(blockX, to.blockX)
    val maxZ = max(blockZ, to.blockZ)
    
    clone()
        .apply { x = (minX + modifier).toDouble() }
        .getStraightLine(Axis.X, (maxX - modifier))
        .forEach { location ->
            listX += location.clone().apply { z = minZ.toDouble() }
            listX += location.clone().apply { z = maxZ.toDouble() }
        }
    
    clone()
        .apply { z = (minZ + modifier).toDouble() }
        .getStraightLine(Axis.Z, (maxZ - modifier))
        .forEach { location ->
            listZ += location.clone().apply { x = minX.toDouble() }
            listZ += location.clone().apply { x = maxX.toDouble() }
        }
    
    return rectangle
}

fun World.dropItemsNaturally(location: Location, items: Iterable<ItemStack>) =
    items.forEach { dropItemNaturally(location, it) }

object LocationUtils {
    
    fun getTopBlocksBetween(
        world: World,
        minX: Int, minY: Int, minZ: Int,
        maxX: Int, maxY: Int, maxZ: Int
    ): List<Location> {
        val locations = ArrayList<Location>()
        
        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                for (y in maxY downTo minY) {
                    val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
                    if (!location.block.type.isTraversable()) {
                        locations += location
                        break
                    }
                }
            }
        }
        
        return locations
    }
    
}