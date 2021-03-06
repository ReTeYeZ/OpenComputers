package li.cil.oc.common.item

import java.util
import li.cil.oc.Settings
import net.minecraft.client.renderer.texture.IconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack

class HardDiskDrive(val parent: Delegator, val tier: Int) extends Delegate {
  val baseName = "HardDiskDrive"
  val unlocalizedName = baseName + tier

  val kiloBytes = Settings.get.hddSizes(tier)

  override def tooltipLines(stack: ItemStack, player: EntityPlayer, tooltip: util.List[String], advanced: Boolean) = {
    if (stack.hasTagCompound) {
      val nbt = stack.getTagCompound
      if (nbt.hasKey(Settings.namespace + "data")) {
        val data = nbt.getCompoundTag(Settings.namespace + "data")
        if (data.hasKey(Settings.namespace + "fs.label")) {
          tooltip.add(data.getString(Settings.namespace + "fs.label"))
        }
        if (advanced && data.hasKey("fs")) {
          val fsNbt = data.getCompoundTag("fs")
          if (fsNbt.hasKey("capacity.used")) {
            val used = fsNbt.getLong("capacity.used")
            tooltip.add("Disk usage: %d/%d Byte".format(used, kiloBytes * 1024))
          }
        }
      }
    }
    super.tooltipLines(stack, player, tooltip, advanced)
  }

  override def displayName(stack: ItemStack) = {
    val localizedName = parent.getItemStackDisplayName(stack)
    Some(if (kiloBytes >= 1024) {
      localizedName + " (%dMB)".format(kiloBytes / 1024)
    }
    else {
      localizedName + " (%dKB)".format(kiloBytes)
    })
  }

  override def registerIcons(iconRegister: IconRegister) {
    super.registerIcons(iconRegister)

    icon = iconRegister.registerIcon(Settings.resourceDomain + ":disk_harddrive" + tier)
  }
}