package li.cil.oc.common.tileentity

import li.cil.oc.{Settings, api}
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.{Slot, UpgradeContainer}
import li.cil.oc.api.network.{SidedEnvironment, Visibility}
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.InventorySlots.Tier
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.ItemUtils
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import cpw.mods.fml.relauncher.{Side, SideOnly}
import net.minecraftforge.common.ForgeDirection

class RobotAssembler extends traits.Environment with traits.PowerAcceptor with traits.Inventory with traits.Rotatable with SidedEnvironment {
  val node = api.Network.newNode(this, Visibility.None).
    withConnector(Settings.get.bufferConverter).
    create()

  var robot: Option[ItemStack] = None

  var totalRequiredEnergy = 0.0

  var requiredEnergy = 0.0

  // ----------------------------------------------------------------------- //

  @SideOnly(Side.CLIENT)
  override def canConnect(side: ForgeDirection) = side != ForgeDirection.UP

  override def sidedNode(side: ForgeDirection) = if (side != ForgeDirection.UP) node else null

  @SideOnly(Side.CLIENT)
  override protected def hasConnector(side: ForgeDirection) = canConnect(side)

  override protected def connector(side: ForgeDirection) = Option(if (side != ForgeDirection.UP) node else null)

  // ----------------------------------------------------------------------- //

  def isAssembling = requiredEnergy > 0

  def progress = (1 - requiredEnergy / totalRequiredEnergy) * 100

  // ----------------------------------------------------------------------- //

  def complexity = items.drop(1).foldLeft(0)((acc, stack) => acc + (Option(api.Driver.driverFor(stack.orNull)) match {
    case Some(driver: UpgradeContainer) => (1 + driver.tier(stack.get)) * 2
    case Some(driver) => 1 + driver.tier(stack.get)
    case _ => 0
  }))

  def maxComplexity = {
    val caseTier = ItemUtils.caseTier(items(0).orNull)
    if (caseTier >= 0) Settings.robotComplexityByTier(caseTier) else 0
  }

  def start() {
    if (!isAssembling && robot.isEmpty && complexity <= maxComplexity) {
      for (slot <- 0 until getSizeInventory) {
        val stack = getStackInSlot(slot)
        if (stack != null && !isItemValidForSlot(slot, stack)) return
      }
      val data = new ItemUtils.RobotData()
      data.tier = ItemUtils.caseTier(items(0).get)
      data.name = ItemUtils.RobotData.randomName
      data.robotEnergy = 50000
      data.totalEnergy = data.robotEnergy
      data.containers = items.take(4).drop(1).collect {
        case Some(item) => item
      }
      data.components = items.drop(4).collect {
        case Some(item) => item
      }
      val stack = api.Items.get("robot").createItemStack(1)
      data.save(stack)
      robot = Some(stack)
      totalRequiredEnergy = math.max(1, Settings.get.robotBaseCost + complexity * Settings.get.robotComplexityCost)
      requiredEnergy = totalRequiredEnergy
      ServerPacketSender.sendRobotAssembling(this, assembling = true)

      for (slot <- 0 until getSizeInventory) items(slot) = None
      onInventoryChanged()
    }
  }

  // ----------------------------------------------------------------------- //

  override def canUpdate = isServer

  override def updateEntity() {
    super.updateEntity()
    if (robot.isDefined && world.getWorldTime % Settings.get.tickFrequency == 0) {
      val want = math.max(1, math.min(requiredEnergy, Settings.get.assemblerTickAmount * Settings.get.tickFrequency))
      val success = node.tryChangeBuffer(-want)
      if (success) {
        requiredEnergy -= want
      }
      if (requiredEnergy <= 0) {
        setInventorySlotContents(0, robot.get)
        robot = None
        requiredEnergy = 0
      }
      ServerPacketSender.sendRobotAssembling(this, success && robot.isDefined)
    }
  }

  override def readFromNBT(nbt: NBTTagCompound) {
    super.readFromNBT(nbt)
    if (nbt.hasKey(Settings.namespace + "robot")) {
      robot = Option(ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(Settings.namespace + "robot")))
    }
    totalRequiredEnergy = nbt.getDouble(Settings.namespace + "total")
    requiredEnergy = nbt.getDouble(Settings.namespace + "remaining")
  }

  override def writeToNBT(nbt: NBTTagCompound) {
    super.writeToNBT(nbt)
    robot.foreach(stack => nbt.setNewCompoundTag(Settings.namespace + "robot", stack.writeToNBT))
    nbt.setDouble(Settings.namespace + "total", totalRequiredEnergy)
    nbt.setDouble(Settings.namespace + "remaining", requiredEnergy)
  }

  @SideOnly(Side.CLIENT) override
  def readFromNBTForClient(nbt: NBTTagCompound) {
    super.readFromNBTForClient(nbt)
    requiredEnergy = nbt.getDouble("remaining")
  }

  override def writeToNBTForClient(nbt: NBTTagCompound) {
    super.writeToNBTForClient(nbt)
    nbt.setDouble("remaining", requiredEnergy)
  }

  // ----------------------------------------------------------------------- //

  override def getInvName = Settings.namespace + "container.RobotAssembler"

  override def getSizeInventory = InventorySlots.assembler(0).length

  override def getInventoryStackLimit = 1

  override def isItemValidForSlot(slot: Int, stack: ItemStack) =
    if (slot == 0) {
      val descriptor = api.Items.get(stack)
      !isAssembling &&
        (descriptor == api.Items.get("case1") ||
          descriptor == api.Items.get("case2") ||
          descriptor == api.Items.get("case3"))
    }
    else {
      val caseTier = ItemUtils.caseTier(items(0).orNull)
      caseTier != Tier.None && {
        val info = InventorySlots.assembler(caseTier)(slot)
        Option(Driver.driverFor(stack)) match {
          case Some(driver) if info.slot != Slot.None && info.tier != Tier.None => driver.slot(stack) == info.slot && driver.tier(stack) <= info.tier
          case _ => false
        }
      }
    }
}
