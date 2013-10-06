package li.cil.oc.common.tileentity

import java.util.concurrent.atomic.AtomicBoolean
import li.cil.oc.api.network.Message
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.server.component
import li.cil.oc.server.component.RedstoneEnabled
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.ForgeDirection

class Computer(isClient: Boolean) extends Rotatable with component.Computer.Environment with ComponentInventory with RedstoneEnabled {
  def this() = this(false)

  // ----------------------------------------------------------------------- //

  private val hasChanged = new AtomicBoolean(true) // For `markChanged`.

  private var isRunning = false

  // ----------------------------------------------------------------------- //
  // General
  // ----------------------------------------------------------------------- //

  def turnOn() = computer.start()

  def turnOff() = computer.stop()

  def isOn = isRunning

  def isOn_=(value: Boolean) = {
    isRunning = value
    worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord)
    this
  }

  override def readFromNBT(nbt: NBTTagCompound) = {
    super.readFromNBT(nbt)
    load(nbt.getCompoundTag("data"))
  }

  override def writeToNBT(nbt: NBTTagCompound) = {
    super.writeToNBT(nbt)

    val dataNbt = new NBTTagCompound
    save(dataNbt)
    nbt.setCompoundTag("data", dataNbt)
  }

  override def updateEntity() = if (!worldObj.isRemote) {
    computer.update()
    if (hasChanged.get)
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
    if (isRunning != computer.isRunning)
      ServerPacketSender.sendComputerState(this, computer.isRunning)
    isRunning = computer.isRunning
  }

  override def validate() = {
    super.validate()
    if (worldObj.isRemote)
      ClientPacketSender.sendComputerStateRequest(this)
  }

  // ----------------------------------------------------------------------- //
  // Computer.Environment
  // ----------------------------------------------------------------------- //

  override protected val computer = if (isClient) null else new component.Computer(this)

  def world = worldObj

  def markAsChanged() = hasChanged.set(true)

  // ----------------------------------------------------------------------- //
  // IInventory
  // ----------------------------------------------------------------------- //

  override def isUseableByPlayer(player: EntityPlayer) =
    worldObj.getBlockTileEntity(xCoord, yCoord, zCoord) == this &&
      player.getDistanceSq(xCoord + 0.5, yCoord + 0.5, zCoord + 0.5) < 64

  // ----------------------------------------------------------------------- //
  // RedstoneEnabled
  // ----------------------------------------------------------------------- //

  def input(side: ForgeDirection): Int = worldObj.isBlockProvidingPowerTo(
    xCoord + side.offsetX, yCoord + side.offsetY, zCoord + side.offsetZ, side.getOpposite.ordinal)

  // TODO output
}