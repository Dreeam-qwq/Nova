package xyz.xenondevs.nova.ui.item

import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import xyz.xenondevs.invui.item.impl.SimpleItem
import xyz.xenondevs.nova.material.CoreGuiMaterial

class BackItem(private val openPrevious: (Player) -> Unit) : SimpleItem(CoreGuiMaterial.ARROW_1_LEFT.clientsideProvider) {
    
    override fun handleClick(clickType: ClickType, player: Player, event: InventoryClickEvent) {
        openPrevious(player)
    }
    
}