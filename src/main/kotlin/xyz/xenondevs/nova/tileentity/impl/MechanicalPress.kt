package xyz.xenondevs.nova.tileentity.impl

import de.studiocode.invui.gui.SlotElement.VISlotElement
import de.studiocode.invui.gui.builder.GUIBuilder
import de.studiocode.invui.gui.builder.GUIType
import de.studiocode.invui.item.Item
import de.studiocode.invui.item.ItemBuilder
import de.studiocode.invui.item.impl.BaseItem
import de.studiocode.invui.virtualinventory.VirtualInventoryManager
import de.studiocode.invui.virtualinventory.event.ItemUpdateEvent
import de.studiocode.invui.window.impl.single.SimpleWindow
import org.bukkit.Sound
import org.bukkit.block.BlockFace
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import xyz.xenondevs.nova.energy.EnergyConnectionType.CONSUME
import xyz.xenondevs.nova.energy.EnergyConnectionType.NONE
import xyz.xenondevs.nova.energy.EnergyNetwork
import xyz.xenondevs.nova.energy.EnergyNetworkManager
import xyz.xenondevs.nova.energy.EnergyStorage
import xyz.xenondevs.nova.material.NovaMaterial
import xyz.xenondevs.nova.recipe.PressRecipe
import xyz.xenondevs.nova.recipe.PressType
import xyz.xenondevs.nova.tileentity.TileEntity
import xyz.xenondevs.nova.ui.EnergyBar
import xyz.xenondevs.nova.ui.OpenSideConfigItem
import xyz.xenondevs.nova.ui.SideConfigGUI
import xyz.xenondevs.nova.ui.item.PressProgressItem
import xyz.xenondevs.nova.util.BlockSide.FRONT
import xyz.xenondevs.nova.util.advance
import xyz.xenondevs.nova.util.novaMaterial
import xyz.xenondevs.nova.util.seed
import xyz.xenondevs.particle.ParticleBuilder
import xyz.xenondevs.particle.ParticleEffect
import xyz.xenondevs.particle.data.color.RegularColor
import java.awt.Color
import java.util.*

private const val MAX_ENERGY = 5_000
private const val ENERGY_PER_TICK = 100
private const val PRESS_TIME = 200

class MechanicalPress(material: NovaMaterial, armorStand: ArmorStand) : TileEntity(material, armorStand, false), EnergyStorage {
    
    private var energy = retrieveData(0, "energy")
    private var type: PressType = retrieveData(PressType.PLATE, "pressType")
    private val inputInv = VirtualInventoryManager.getInstance().getOrCreate(uuid.seed("input"), 1).apply { setItemUpdateHandler(::handleInputUpdate) }
    private val outputInv = VirtualInventoryManager.getInstance().getOrCreate(uuid.seed("output"), 1).apply { setItemUpdateHandler(::handleOutputUpdate) }
    private val gui = MechanicalPressUI()
    private var updateEnergyBar = true
    
    private var pressTime: Int = 0
    private var currentItem: ItemStack? = null
    
    override val networks = EnumMap<BlockFace, EnergyNetwork>(BlockFace::class.java)
    override val configuration = retrieveData(createSideConfig(CONSUME, FRONT), "sideConfig")
    override val providedEnergy = 0
    override val requestedEnergy: Int
        get() = MAX_ENERGY - energy
    
    override fun handleTick() {
        if (energy >= ENERGY_PER_TICK) { // has energy to do anything
            if (pressTime == 0) takeItem()
            if (pressTime != 0) { // is pressing
                pressTime--
                energy -= ENERGY_PER_TICK
                
                if (pressTime == 0) {
                    outputInv.placeOne(null, 0, currentItem)
                }
                
                gui.updateProgress()
                updateEnergyBar = true
            }
        }
        
        if (updateEnergyBar) {
            gui.energyBar.update()
            updateEnergyBar = false
        }
        
        configuration.forEach { (face, _) ->
            val network = networks[face]
            ParticleBuilder(ParticleEffect.REDSTONE, armorStand.location.clone().add(0.0, 0.5, 0.0).advance(face, 0.5))
                .setParticleData(network?.color ?: RegularColor(Color(Color.HSBtoRGB(0f, 0f, 0f))))
                .display()
        }
    }
    
    private fun takeItem() {
        val inputItem = inputInv.getItemStack(0)
        if (inputItem != null) {
            val outputItem = outputInv.getItemStack(0)
            val recipeOutput = PressRecipe.getOutputFor(inputItem.type, type)
            if (outputItem == null || outputItem.novaMaterial == recipeOutput) {
                inputInv.removeOne(null, 0)
                currentItem = recipeOutput.createItemStack()
                pressTime = PRESS_TIME
            }
        }
    }
    
    override fun handleRightClick(event: PlayerInteractEvent) {
        event.isCancelled = true
        gui.openWindow(event.player)
    }
    
    private fun handleInputUpdate(event: ItemUpdateEvent) {
        if (event.player != null && event.newItemStack != null) {
            val material = event.newItemStack.type
            if (!PressRecipe.isPressable(material, type)) {
                event.isCancelled = true
            }
        }
    }
    
    private fun handleOutputUpdate(event: ItemUpdateEvent) {
        if (event.player != null && event.newItemStack != null) event.isCancelled = true
    }
    
    override fun addEnergy(energy: Int) {
        this.energy += energy
        updateEnergyBar = true
    }
    
    override fun removeEnergy(energy: Int) {
        throw UnsupportedOperationException()
    }
    
    override fun saveData() {
        storeData("energy", energy)
        storeData("pressType", pressTime)
        storeData("sideConfig", configuration)
    }
    
    override fun handleInitialized() {
        EnergyNetworkManager.handleStorageAdd(this)
    }
    
    override fun handleRemoved(unload: Boolean) {
        EnergyNetworkManager.handleStorageRemove(this, unload)
    }
    
    private fun getEnergyValues() = energy to MAX_ENERGY
    
    inner class MechanicalPressUI {
        
        private val pressProgress = PressProgressItem()
        private val pressTypeItems = ArrayList<PressTypeItem>()
        private val sideConfigGUI = SideConfigGUI(this@MechanicalPress, NONE, CONSUME) { openWindow(it) }
        private val gui = GUIBuilder(GUIType.NORMAL, 9, 5)
            .setStructure("" +
                "1 - - - - - - - 2" +
                "| p g # i # # . |" +
                "| # # # , # # . |" +
                "| s # # o # # . |" +
                "3 - - - - - - - 4")
            .addIngredient('i', VISlotElement(inputInv, 0))
            .addIngredient('o', VISlotElement(outputInv, 0))
            .addIngredient(',', pressProgress)
            .addIngredient('s', OpenSideConfigItem(sideConfigGUI))
            .addIngredient('p', PressTypeItem(PressType.PLATE).apply(pressTypeItems::add))
            .addIngredient('g', PressTypeItem(PressType.GEAR).apply(pressTypeItems::add))
            .build()
        
        val energyBar = EnergyBar(gui, x = 7, y = 1, height = 3, ::getEnergyValues)
        
        fun updateProgress() {
            pressProgress.percentage = if (pressTime == 0) 0.0 else (PRESS_TIME - pressTime).toDouble() / PRESS_TIME.toDouble()
        }
        
        fun openWindow(player: Player) {
            SimpleWindow(player, "Mechanical Press", gui).show()
        }
        
        private inner class PressTypeItem(private val type: PressType) : BaseItem() {
            
            override fun getItemBuilder(): ItemBuilder {
                return if (type == PressType.PLATE) {
                    if (this@MechanicalPress.type == PressType.PLATE) NovaMaterial.PLATE_OFF_BUTTON.createItemBuilder()
                    else NovaMaterial.PLATE_ON_BUTTON.item.getItemBuilder("Press Plates")
                } else {
                    if (this@MechanicalPress.type == PressType.GEAR) NovaMaterial.GEAR_OFF_BUTTON.createItemBuilder()
                    else NovaMaterial.GEAR_ON_BUTTON.item.getItemBuilder("Press Gears")
                }
            }
            
            override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
                if (this@MechanicalPress.type != type) {
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    this@MechanicalPress.type = type
                    pressTypeItems.forEach(Item::notifyWindows)
                }
            }
            
        }
        
    }
    
}