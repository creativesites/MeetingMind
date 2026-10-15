package com.craftflowtechnologies.meetingmind.core.ui.mm.companion.forms

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.craftflowtechnologies.meetingmind.ui.theme.CompanionPalette

/**
 * The T1 volume layer (docs/mvp/ZURI_EXPERIENCE.md §4.2, Z-14): a port of the visual reference's
 * `vol()`. Clipped to a part's silhouette it draws
 *
 * - a radial shading field, light at the upper left (white 26% → clear → black 28% at the far edge;
 *   16% / 42% in dark), which moves against the parallax so the light seems fixed in the room;
 * - a soft specular oval that shifts opposite the look;
 * - a rim light along the lower right edge in `rim`;
 * - ambient occlusion where parts meet (head on body, ears on head, a wing on the body, the fold).
 *
 * Plus a tighter contact shadow under the ground shadow. Everything here is built once per palette;
 * per frame it only translates, scales and clips, so it allocates nothing.
 */
class VolumeKit {
    private var palette: CompanionPalette? = null

    /** The shading field, centred (40, 36), r 60, in the 100-unit box. */
    lateinit var field: Brush; private set
    /** A unit radial highlight at the origin, r 1: drawn scaled and rotated into an oval. */
    lateinit var specular: Brush; private set
    /** A unit radial occlusion at the origin, r 1. */
    lateinit var occlusion: Brush; private set
    /** The contact shadow colour: ink 16% (light) or black 45% (dark). */
    var contact: Color = Color.Transparent; private set

    private val rim = arrayOfNulls<RimBrush>(Silhouette.entries.size)

    /** Fixed silhouettes (circles and ovals), built once. Page's is the live page path. */
    private val paths = Array(Silhouette.entries.size) { i -> Silhouette.entries[i].path() }

    fun ensure(p: CompanionPalette) {
        if (palette == p) return
        palette = p
        val white = p.catchLight
        field = Brush.radialGradient(
            0f to white.copy(alpha = if (p.isDark) 0.16f else 0.26f),
            0.42f to white.copy(alpha = 0f),
            0.78f to Color.Black.copy(alpha = if (p.isDark) 0.20f else 0.12f),
            1f to Color.Black.copy(alpha = if (p.isDark) 0.42f else 0.28f),
            center = Offset(40f, 36f), radius = 60f
        )
        specular = Brush.radialGradient(
            0f to white.copy(alpha = 0.95f), 0.5f to white.copy(alpha = 0.35f), 1f to white.copy(alpha = 0f),
            center = Offset.Zero, radius = 1f
        )
        occlusion = Brush.radialGradient(
            0f to Color.Black.copy(alpha = if (p.isDark) 0.34f else 0.20f), 1f to Color.Black.copy(alpha = 0f),
            center = Offset.Zero, radius = 1f
        )
        contact = p.shadow.copy(alpha = if (p.isDark) 0.45f else 0.16f)
        for (s in Silhouette.entries) rim[s.ordinal] = RimBrush(s.bounds, p.rim)
    }

    fun path(s: Silhouette): Path = paths[s.ordinal]

    fun rim(s: Silhouette): RimBrush = rim[s.ordinal]!!
}

/** The parts that get a volume layer, with their bounds in the 100-unit box (for the rim gradient). */
enum class Silhouette(val bounds: Rect) {
    ZURI_BODY(Rect(20f, 28f, 80f, 88f)),
    NAS_BODY(Rect(32.5f, 63.5f, 67.5f, 90.5f)),
    NAS_HEAD(Rect(29f, 21f, 71f, 63f)),
    WREN_BODY(Rect(24f, 35f, 74f, 85f)),
    /** The page at fold 12; the live path follows the fold, the rim keeps these bounds. */
    PAGE(Rect(28f, 20f, 72f, 82f));

    internal fun path(): Path = Path().apply { if (this@Silhouette != PAGE) addOval(bounds) }
}

/**
 * The rim light: a linear gradient over the part's bounds from (15%, 10%) to (95%, 100%), clear
 * until 55% and `rim` at the end (the reference's objectBoundingBox gradient). Built once per
 * palette. Parallax slides it by 20% of the bounds: the canvas moves and the path moves back, so
 * the stroke stays on the silhouette while the light slides (a shader matrix would copy a Matrix
 * on every change).
 */
class RimBrush(val bounds: Rect, color: Color) : ShaderBrush() {
    private val shader: Shader = LinearGradientShader(
        from = Offset(bounds.left + 0.15f * bounds.width, bounds.top + 0.10f * bounds.height),
        to = Offset(bounds.left + 0.95f * bounds.width, bounds.bottom),
        colors = listOf(color.copy(alpha = 0f), color.copy(alpha = 0f), color),
        colorStops = listOf(0f, 0.55f, 1f)
    )

    override fun createShader(size: Size): Shader = shader
}

/**
 * Draws the volume layer for one part, inside the current body transform. [path] is the part's
 * silhouette (clip and rim); [sx], [sy], [sr] are the specular centre and radius (the reference's
 * `spec`). [strength] scales the shading field (Page's paper stays light, §2.3). [occlude] draws
 * ambient occlusion inside the same clip, before the highlights.
 */
inline fun DrawScope.volumeLayer(
    kit: CompanionDrawKit,
    f: CompanionFrame,
    s: Silhouette,
    path: Path,
    sx: Float, sy: Float, sr: Float,
    strength: Float = 1f,
    occlude: DrawScope.(VolumeKit) -> Unit = {}
) {
    val v = kit.volume
    val px = f.lightX
    val py = f.lightY
    clipPath(path) {
        // The shading field moves against the look, so the light stays put while the body turns.
        translate(-px * 8f, -py * 8f) { drawRect(v.field, topLeft = Offset(-30f, -30f), size = Size(160f, 160f), alpha = strength) }
        occlude(v)
        translate(sx - px * 4f, sy - py * 4f) {
            rotate(-32f, Offset.Zero) { scale(sr, sr * 0.6f, Offset.Zero) { drawCircle(v.specular, radius = 1f, center = Offset.Zero) } }
        }
        val rim = v.rim(s)
        val dx = px * 0.2f * rim.bounds.width
        val dy = py * 0.2f * rim.bounds.height
        if (dx == 0f && dy == 0f) drawPath(path, rim, style = kit.stroke(3.4f))
        else translate(dx, dy) {
            path.translate(Offset(-dx, -dy))
            drawPath(path, rim, style = kit.stroke(3.4f))
            path.translate(Offset(dx, dy))
        }
    }
}

/** A soft occlusion blob centred (x, y) with radii (rx, ry), rotated [deg]. */
fun DrawScope.occlusion(v: VolumeKit, x: Float, y: Float, rx: Float, ry: Float, deg: Float = 0f) {
    translate(x, y) {
        rotate(deg, Offset.Zero) { scale(rx, ry, Offset.Zero) { drawCircle(v.occlusion, radius = 1f, center = Offset.Zero) } }
    }
}

/** The contact shadow: a tighter, darker ellipse under the ground shadow, sliding with the look. */
fun DrawScope.contactShadow(v: VolumeKit, f: CompanionFrame, y: Float) {
    val rx = 10f * (1f - f.pose.hop * 0.4f)
    drawOval(v.contact, topLeft = Offset(50f - f.lightX * 2f - rx, y - 0.4f - 1.6f), size = Size(rx * 2f, 3.2f))
}
