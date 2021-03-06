package li.cil.oc.client.renderer

import java.util.logging.Level
import li.cil.oc.client.Textures
import li.cil.oc.util.{RenderState, PackedColor}
import li.cil.oc.{OpenComputers, Settings}
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import scala.io.Source

// IMPORTANT: we must not use the tessellator here. Doing so can cause
// crashes on certain graphics cards with certain drivers (reported for
// ATI/AMD and Intel chip sets). These crashes have been reported to
// happen I have no idea why, and can only guess that it's related to
// using the VBO/ARB the tessellator uses inside a display list (since
// this stuff is eventually only rendered via display lists).

object MonospaceFontRenderer {
  val (chars, fontWidth, fontHeight) = try {
    val lines = Source.fromInputStream(Minecraft.getMinecraft.getResourceManager.getResource(new ResourceLocation(Settings.resourceDomain, "textures/font/chars.txt")).getInputStream)("UTF-8").getLines()
    val chars = lines.next()
    val (w, h) = if (lines.hasNext) {
      val size = lines.next().split(" ", 2)
      (size(0).toInt, size(1).toInt)
    } else (5, 9)
    (chars, w, h)
  }
  catch {
    case t: Throwable =>
      OpenComputers.log.log(Level.WARNING, "Failed reading font metadata, using defaults.", t)
      ( """☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼ !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~⌂ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■""", 5, 9)
  }

  private var instance: Option[Renderer] = None

  def init(textureManager: TextureManager) = this.synchronized(
    instance = instance.orElse(Some(new Renderer(textureManager))))

  def drawString(x: Int, y: Int, value: Array[Char], color: Array[Short], format: PackedColor.ColorFormat) = this.synchronized(instance match {
    case None => OpenComputers.log.warning("Trying to render string with uninitialized MonospaceFontRenderer.")
    case Some(renderer) => renderer.drawString(x, y, value, color, format)
  })

  private class Renderer(private val textureManager: TextureManager) {
    private val (charWidth, charHeight) = (MonospaceFontRenderer.fontWidth * 2, MonospaceFontRenderer.fontHeight * 2)
    private val cols = 256 / charWidth
    private val uStep = charWidth / 256.0
    private val uSize = uStep
    private val vStep = (charHeight + 1) / 256.0
    private val vSize = charHeight / 256.0
    private val s = Settings.get.fontCharScale
    private val dw = charWidth * s - charWidth
    private val dh = charHeight * s - charHeight

    def drawString(x: Int, y: Int, value: Array[Char], color: Array[Short], format: PackedColor.ColorFormat) = {
      if (color.length != value.length) throw new IllegalArgumentException("Color count must match char count.")

      RenderState.checkError(getClass.getName + ".drawString: entering (aka: wasntme)")

      if (Settings.get.textAntiAlias)
        textureManager.bindTexture(Textures.fontAntiAliased)
      else
        textureManager.bindTexture(Textures.fontAliased)
      GL11.glPushMatrix()
      GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS)

      GL11.glTranslatef(x, y, 0)
      GL11.glScalef(0.5f, 0.5f, 1)
      GL11.glDepthMask(false)
      GL11.glDisable(GL11.GL_TEXTURE_2D)

      RenderState.checkError(getClass.getName + ".drawString: configure state")

      GL11.glBegin(GL11.GL_QUADS)
      // Background first. We try to merge adjacent backgrounds of the same
      // color to reduce the number of quads we have to draw.
      var cbg = 0x000000
      var offset = 0
      var width = 0
      for (col <- color.map(PackedColor.unpackBackground(_, format))) {
        if (col != cbg) {
          draw(cbg, offset, width)
          cbg = col
          offset += width
          width = 0
        }
        width = width + 1
      }
      draw(cbg, offset, width)
      GL11.glEnd()

      RenderState.checkError(getClass.getName + ".drawString: background")

      GL11.glEnable(GL11.GL_TEXTURE_2D)

      if (Settings.get.textLinearFiltering) {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
      }

      // Foreground second. We only have to flush when the color changes, so
      // unless every char has a different color this should be quite efficient.
      GL11.glBegin(GL11.GL_QUADS)
      var cfg = -1
      var posX = 0.0
      for ((ch, col) <- value.zip(color.map(PackedColor.unpackForeground(_, format)))) {
        val index = 1 + (chars.indexOf(ch) match {
          case -1 => chars.indexOf('?')
          case i => i
        })
        if (col != cfg) {
          // Color changed.
          cfg = col
          GL11.glColor3ub(
            ((cfg & 0xFF0000) >> 16).toByte,
            ((cfg & 0x00FF00) >> 8).toByte,
            ((cfg & 0x0000FF) >> 0).toByte)
        }
        if (ch != ' ') {
          // Don't render whitespace.
          val x = (index - 1) % cols
          val y = (index - 1) / cols
          val u = x * uStep
          val v = y * vStep
          GL11.glTexCoord2d(u, v + vSize)
          GL11.glVertex3d(posX - dw, charHeight * s, 0)
          GL11.glTexCoord2d(u + uSize, v + vSize)
          GL11.glVertex3d(posX + charWidth * s, charHeight * s, 0)
          GL11.glTexCoord2d(u + uSize, v)
          GL11.glVertex3d(posX + charWidth * s, -dh, 0)
          GL11.glTexCoord2d(u, v)
          GL11.glVertex3d(posX - dw, -dh, 0)
        }
        posX += charWidth
      }
      GL11.glEnd()

      RenderState.checkError(getClass.getName + ".drawString: foreground")

      GL11.glPopAttrib()
      GL11.glPopMatrix()

      RenderState.checkError(getClass.getName + ".drawString: leaving")
    }

    private def draw(color: Int, offset: Int, width: Int) = if (color != 0 && width > 0) {
      GL11.glColor3ub(((color >> 16) & 0xFF).toByte, ((color >> 8) & 0xFF).toByte, (color & 0xFF).toByte)
      GL11.glVertex3d(charWidth * offset, charHeight, 0)
      GL11.glVertex3d(charWidth * (offset + width), charHeight, 0)
      GL11.glVertex3d(charWidth * (offset + width), 0, 0)
      GL11.glVertex3d(charWidth * offset, 0, 0)
    }
  }

}