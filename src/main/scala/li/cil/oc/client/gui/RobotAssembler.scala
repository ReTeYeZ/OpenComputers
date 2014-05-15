package li.cil.oc.client.gui

import java.util
import li.cil.oc.api
import li.cil.oc.Settings
import li.cil.oc.client.{PacketSender => ClientPacketSender, Textures}
import li.cil.oc.common.container
import li.cil.oc.common.tileentity
import net.minecraft.client.gui.GuiButton
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.util.StatCollector
import org.lwjgl.opengl.GL11
import net.minecraft.client.renderer.Tessellator

class RobotAssembler(playerInventory: InventoryPlayer, val assembler: tileentity.RobotAssembler) extends DynamicGuiContainer(new container.RobotAssembler(playerInventory, assembler)) {
  xSize = 176
  ySize = 192

  private def assemblerContainer = inventorySlots.asInstanceOf[container.RobotAssembler]

  protected var runButton: ImageButton = _

  private val progressX = 28
  private val progressY = 92

  private val progressWidth = 140
  private val progressHeight = 12

  def add[T](list: util.List[T], value: Any) = list.add(value.asInstanceOf[T])

  protected override def actionPerformed(button: GuiButton) {
    if (button.id == 0 && !assemblerContainer.isAssembling && assembler.complexity <= assembler.maxComplexity) {
      ClientPacketSender.sendRobotAssemblerStart(assembler)
    }
  }

  override def drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
    runButton.enabled = assembler.complexity <= assembler.maxComplexity && !assemblerContainer.isAssembling && assembler.isItemValidForSlot(0, assembler.getStackInSlot(0))
    runButton.toggled = !runButton.enabled
    super.drawScreen(mouseX, mouseY, dt)
  }

  override def initGui() {
    super.initGui()
    runButton = new ImageButton(0, guiLeft + 7, guiTop + 89, 18, 18, Textures.guiButtonRun, canToggle = true)
    add(buttonList, runButton)
  }

  override def drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int) = {
    GL11.glPushAttrib(0xFFFFFFFF) // Me lazy... prevents NEI render glitch.
    if (!assemblerContainer.isAssembling) {
      if (!inventorySlots.getSlot(0).getHasStack) {
        val message =
          if (api.Items.get(inventorySlots.getSlot(0).getStack) == api.Items.get("robot"))
            "gui.RobotAssembler.CollectRobot"
          else
            "gui.RobotAssembler.InsertCase"
        fontRenderer.drawString(
          StatCollector.translateToLocal(Settings.namespace + message),
          30, 94, 0x404040)
      }
      else {
        fontRenderer.drawString(
          StatCollector.translateToLocalFormatted(Settings.namespace + "gui.RobotAssembler.Complexity", Int.box(assembler.complexity), Int.box(assembler.maxComplexity)),
          30, 94, if (assembler.complexity <= assembler.maxComplexity) 0x404040 else 0x804040)
      }
    }
    if (runButton.func_82252_a && !assemblerContainer.isAssembling) {
      val tooltip = new java.util.ArrayList[String]
      tooltip.add(StatCollector.translateToLocal(Settings.namespace + "gui.RobotAssembler.Run"))
      drawHoveringText(tooltip, mouseX - guiLeft, mouseY - guiTop, fontRenderer)
    }
    GL11.glPopAttrib()
  }

  override def drawGuiContainerBackgroundLayer(dt: Float, mouseX: Int, mouseY: Int) {
    GL11.glColor3f(1, 1, 1) // Required under Linux.
    super.drawGuiContainerBackgroundLayer(dt, mouseX, mouseY)
    mc.renderEngine.bindTexture(Textures.guiRobotAssembler)
    drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize)
    if (assemblerContainer.isAssembling) {
      drawProgress()
    }
  }

  override def doesGuiPauseGame = false

  private def drawProgress() {
    val level = assemblerContainer.assemblyProgress / 100.0

    val u0 = 0
    val u1 = progressWidth / 256.0 * level
    val v0 = 1 - progressHeight / 256.0
    val v1 = 1
    val x = guiLeft + progressX
    val y = guiTop + progressY
    val w = progressWidth * level

    val t = Tessellator.instance
    t.startDrawingQuads()
    t.addVertexWithUV(x, y, zLevel, u0, v0)
    t.addVertexWithUV(x, y + progressHeight, zLevel, u0, v1)
    t.addVertexWithUV(x + w, y + progressHeight, zLevel, u1, v1)
    t.addVertexWithUV(x + w, y, zLevel, u1, v0)
    t.draw()
  }
}