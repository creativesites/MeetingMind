package com.craftflowtechnologies.meetingmind.feature.learning

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.craftflowtechnologies.meetingmind.core.model.NoteBlock
import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.notes.RichText
import com.craftflowtechnologies.meetingmind.core.ui.mm.MMCard
import com.craftflowtechnologies.meetingmind.feature.notes.editor.DiagramBlock as EditorDiagramBlock
import com.craftflowtechnologies.meetingmind.feature.notes.editor.DiagramCanvas
import com.craftflowtechnologies.meetingmind.feature.notes.editor.MermaidDiagramParser
import com.craftflowtechnologies.meetingmind.ui.theme.LearningFixedColors
import com.craftflowtechnologies.meetingmind.ui.theme.MM
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class LearningVisualKind(val label: String) {
    AUTO("Auto"),
    BAR_CHART("Bar Chart"),
    LINE_CHART("Trend Chart"),
    FUNCTION_PLOT("Function Graph"),
    PIE_CHART("Breakdown"),
    TIMELINE("Timeline"),
    MERMAID_DIAGRAM("Flow Diagram"),
    AI_IMAGE("Diagram Image")
}

data class ChartPoint(
    val label: String,
    val value: Double
)

data class ChartSeries(
    val name: String,
    val points: List<ChartPoint>
)

data class TimelineNode(
    val step: String,
    val title: String,
    val detail: String
)

data class LearningVisualSpec(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val kind: LearningVisualKind,
    val xLabel: String = "",
    val yLabel: String = "",
    val series: List<ChartSeries> = emptyList(),
    val equations: List<String> = emptyList(),
    val xMin: Double = -5.0,
    val xMax: Double = 5.0,
    val timeline: List<TimelineNode> = emptyList(),
    val mermaidCode: String = "",
    val imagePath: String? = null,
    val caption: String = ""
)

/**
 * Safe mathematical expression recursive-descent parser.
 * Supports +, -, *, /, ^, parentheses, x, pi, e, sin, cos, tan, sqrt, abs, ln, log, exp.
 * Evaluates without throwing exceptions, returning Double? safely.
 */
object MathExprEvaluator {

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Ident(val name: String) : Token
        object Plus : Token
        object Minus : Token
        object Mul : Token
        object Div : Token
        object Pow : Token
        object LParen : Token
        object RParen : Token
        object Eof : Token
    }

    private fun tokenize(expr: String): List<Token>? {
        val tokens = mutableListOf<Token>()
        var i = 0
        val n = expr.length
        while (i < n) {
            val c = expr[i]
            when {
                c.isWhitespace() -> i++
                c == '+' -> { tokens.add(Token.Plus); i++ }
                c == '-' -> { tokens.add(Token.Minus); i++ }
                c == '*' -> { tokens.add(Token.Mul); i++ }
                c == '/' -> { tokens.add(Token.Div); i++ }
                c == '^' -> { tokens.add(Token.Pow); i++ }
                c == '(' -> {
                    val prev = tokens.lastOrNull()
                    if (prev is Token.Num || prev is Token.Ident || prev is Token.RParen) {
                        tokens.add(Token.Mul)
                    }
                    tokens.add(Token.LParen)
                    i++
                }
                c == ')' -> { tokens.add(Token.RParen); i++ }
                c.isDigit() || c == '.' -> {
                    val start = i
                    var hasDot = (c == '.')
                    i++
                    while (i < n && (expr[i].isDigit() || (!hasDot && expr[i] == '.'))) {
                        if (expr[i] == '.') hasDot = true
                        i++
                    }
                    val num = expr.substring(start, i).toDoubleOrNull() ?: return null
                    val prev = tokens.lastOrNull()
                    if (prev is Token.RParen) {
                        tokens.add(Token.Mul)
                    }
                    tokens.add(Token.Num(num))
                }
                c.isLetter() -> {
                    val start = i
                    while (i < n && (expr[i].isLetter() || expr[i].isDigit())) {
                        i++
                    }
                    val name = expr.substring(start, i).lowercase()
                    val prev = tokens.lastOrNull()
                    if (prev is Token.Num || prev is Token.RParen || (prev is Token.Ident && (prev.name == "x" || prev.name == "pi" || prev.name == "e"))) {
                        tokens.add(Token.Mul)
                    }
                    tokens.add(Token.Ident(name))
                }
                else -> i++
            }
        }
        tokens.add(Token.Eof)
        return tokens
    }

    private class Parser(private val tokens: List<Token>, private val xVal: Double) {
        private var pos = 0
        private fun peek(): Token = tokens.getOrElse(pos) { Token.Eof }
        private fun next(): Token = tokens.getOrElse(pos++) { Token.Eof }

        fun parse(): Double? {
            val res = parseExpr() ?: return null
            if (peek() !is Token.Eof) return null
            return if (res.isNaN() || res.isInfinite()) null else res
        }

        private fun parseExpr(): Double? {
            var left = parseTerm() ?: return null
            while (true) {
                when (peek()) {
                    is Token.Plus -> { next(); val right = parseTerm() ?: return null; left += right }
                    is Token.Minus -> { next(); val right = parseTerm() ?: return null; left -= right }
                    else -> break
                }
            }
            return left
        }

        private fun parseTerm(): Double? {
            var left = parsePower() ?: return null
            while (true) {
                when (peek()) {
                    is Token.Mul -> { next(); val right = parsePower() ?: return null; left *= right }
                    is Token.Div -> {
                        next()
                        val right = parsePower() ?: return null
                        if (right == 0.0) return null
                        left /= right
                    }
                    else -> break
                }
            }
            return left
        }

        private fun parsePower(): Double? {
            val base = parseUnary() ?: return null
            if (peek() is Token.Pow) {
                next()
                val exp = parsePower() ?: return null
                val res = base.pow(exp)
                return if (res.isNaN() || res.isInfinite()) null else res
            }
            return base
        }

        private fun parseUnary(): Double? {
            if (peek() is Token.Plus) {
                next()
                return parseUnary()
            }
            if (peek() is Token.Minus) {
                next()
                val res = parseUnary() ?: return null
                return -res
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double? {
            return when (val t = peek()) {
                is Token.Num -> { next(); t.value }
                is Token.LParen -> {
                    next()
                    val inner = parseExpr() ?: return null
                    if (peek() !is Token.RParen) return null
                    next()
                    inner
                }
                is Token.Ident -> {
                    next()
                    val name = t.name
                    if (peek() is Token.LParen) {
                        next()
                        val arg = parseExpr() ?: return null
                        if (peek() !is Token.RParen) return null
                        next()
                        when (name) {
                            "sin" -> sin(arg)
                            "cos" -> cos(arg)
                            "tan" -> tan(arg)
                            "sqrt" -> if (arg >= 0) sqrt(arg) else null
                            "abs" -> abs(arg)
                            "ln" -> if (arg > 0) ln(arg) else null
                            "log" -> if (arg > 0) log10(arg) else null
                            "exp" -> exp(arg)
                            else -> null
                        }
                    } else {
                        when (name) {
                            "x" -> xVal
                            "pi" -> Math.PI
                            "e" -> Math.E
                            else -> null
                        }
                    }
                }
                else -> null
            }
        }
    }

    fun evaluate(expr: String, x: Double = 0.0): Double? {
        if (expr.isBlank()) return null
        return runCatching {
            val cleaned = expr
                .replace("\\left", "")
                .replace("\\right", "")
                .replace("$", "")
                .replace("\\cdot", "*")
                .replace("\\times", "*")
                .trim()
            val tokens = tokenize(cleaned) ?: return null
            Parser(tokens, x).parse()
        }.getOrNull()
    }
}

/**
 * Renders a Mermaid diagram code block delegating to the Academic diagram block.
 */
@Composable
fun DiagramBlock(
    code: String,
    modifier: Modifier = Modifier
) {
    val parsed = remember(code) { MermaidDiagramParser.parse(code) }
    if (parsed.nodes.isNotEmpty()) {
        DiagramCanvas(
            diagram = parsed,
            modifier = modifier.fillMaxWidth()
        )
    } else {
        val block = remember(code) {
            NoteBlock(
                id = "visual_mermaid",
                noteId = "",
                position = 0,
                type = NoteBlockType.DIAGRAM,
                content = RichText.plain(code),
                payload = mapOf(NoteBlock.PAYLOAD_DIAGRAM_SYNTAX to code)
            )
        }
        EditorDiagramBlock(
            block = block,
            onEdit = {}
        )
    }
}

/**
 * Bar chart canvas renderer using only MM design tokens.
 */
@Composable
fun BarChartCanvas(
    spec: LearningVisualSpec,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val captionStyle = MM.type.caption
    val measurer = rememberTextMeasurer()
    val points = remember(spec.series) { spec.series.firstOrNull()?.points.orEmpty() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(horizontal = MM.space.xs)
    ) {
        if (points.isEmpty()) return@Canvas

        val maxVal = points.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
        val bottomMargin = 32.dp.toPx()
        val topMargin = 20.dp.toPx()
        val chartHeight = size.height - bottomMargin - topMargin
        val n = points.size
        val slotWidth = size.width / n
        val barWidth = (slotWidth * 0.58f).coerceAtMost(56.dp.toPx())

        // Horizontal gridlines
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = topMargin + chartHeight * (i.toFloat() / gridLines)
            drawLine(
                color = colors.lineSoft,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Baseline
        val baselineY = topMargin + chartHeight
        drawLine(
            color = colors.line,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 1.5.dp.toPx()
        )

        // Draw bars and labels
        val barColors = listOf(colors.accent, LearningFixedColors.TealAccent, LearningFixedColors.AmberHighlight, LearningFixedColors.TheoremAccent)
        points.forEachIndexed { idx, pt ->
            val barHeight = ((pt.value / maxVal) * chartHeight).toFloat().coerceAtLeast(4.dp.toPx())
            val centerX = idx * slotWidth + slotWidth / 2f
            val left = centerX - barWidth / 2f
            val top = baselineY - barHeight
            val barColor = barColors[idx % barColors.size]

            drawRoundRect(
                color = barColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )

            // X-axis label
            val textLayout = measurer.measure(
                text = pt.label,
                style = captionStyle.copy(color = colors.inkSecondary)
            )
            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(centerX - textLayout.size.width / 2f, baselineY + 6.dp.toPx())
            )

            // Value label above bar
            val valLayout = measurer.measure(
                text = if (pt.value % 1.0 == 0.0) pt.value.toInt().toString() else String.format("%.1f", pt.value),
                style = captionStyle.copy(color = colors.ink)
            )
            val valY = (top - valLayout.size.height - 3.dp.toPx()).coerceAtLeast(2.dp.toPx())
            drawText(
                textLayoutResult = valLayout,
                topLeft = Offset(centerX - valLayout.size.width / 2f, valY)
            )
        }
    }
}

/**
 * Line chart canvas renderer supporting single or multiple series.
 */
@Composable
fun LineChartCanvas(
    spec: LearningVisualSpec,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val captionStyle = MM.type.caption
    val seriesList = remember(spec.series) { spec.series.ifEmpty { listOf(ChartSeries("Data", emptyList())) } }
    val measurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        // Multi-series legend if more than one series
        if (seriesList.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = MM.space.xs),
                horizontalArrangement = Arrangement.spacedBy(MM.space.m)
            ) {
                val palette = listOf(colors.accent, LearningFixedColors.TealAccent, LearningFixedColors.AmberHighlight, LearningFixedColors.TheoremAccent)
                seriesList.forEachIndexed { idx, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(MM.radius.pill)
                                .background(palette[idx % palette.size])
                        )
                        Spacer(Modifier.width(MM.space.xs))
                        Text(s.name, style = MM.type.caption, color = colors.inkSecondary)
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = MM.space.xs)
        ) {
            val allPoints = seriesList.flatMap { it.points }
            if (allPoints.isEmpty()) return@Canvas

            val maxVal = allPoints.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
            val minVal = (allPoints.minOfOrNull { it.value } ?: 0.0).coerceAtMost(0.0)
            val range = (maxVal - minVal).coerceAtLeast(1.0)

            val bottomMargin = 32.dp.toPx()
            val topMargin = 16.dp.toPx()
            val chartHeight = size.height - bottomMargin - topMargin
            val baselineY = topMargin + chartHeight

            // Gridlines
            for (i in 0..3) {
                val y = topMargin + chartHeight * (i / 3f)
                drawLine(
                    color = colors.lineSoft,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val palette = listOf(colors.accent, LearningFixedColors.TealAccent, LearningFixedColors.AmberHighlight, LearningFixedColors.TheoremAccent)
            seriesList.forEachIndexed { sIdx, series ->
                val pts = series.points
                if (pts.isEmpty()) return@forEachIndexed
                val strokeColor = palette[sIdx % palette.size]
                val stepX = if (pts.size > 1) size.width / (pts.size - 1) else size.width

                val linePath = Path()
                val fillPath = Path()

                pts.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = baselineY - (((pt.value - minVal) / range) * chartHeight).toFloat()
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, baselineY)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo((pts.size - 1) * stepX, baselineY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(strokeColor.copy(alpha = 0.24f), strokeColor.copy(alpha = 0.02f)),
                        startY = topMargin,
                        endY = baselineY
                    )
                )

                drawPath(
                    path = linePath,
                    color = strokeColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Dots on points
                pts.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = baselineY - (((pt.value - minVal) / range) * chartHeight).toFloat()
                    drawCircle(color = strokeColor, radius = 5.dp.toPx(), center = Offset(x, y))
                    drawCircle(color = colors.surface, radius = 2.5.dp.toPx(), center = Offset(x, y))

                    if (sIdx == 0) {
                        val textLayout = measurer.measure(
                            text = pt.label,
                            style = captionStyle.copy(color = colors.inkSecondary)
                        )
                        val textX = (x - textLayout.size.width / 2f).coerceIn(0f, size.width - textLayout.size.width)
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(textX, baselineY + 6.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

/**
 * Continuous function plot canvas supporting safe mathematical formulas.
 */
@Composable
fun FunctionPlotCanvas(
    spec: LearningVisualSpec,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val captionStyle = MM.type.caption
    val equations = remember(spec.equations) { spec.equations.ifEmpty { listOf("sin(x)") } }
    val measurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        // Equation legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MM.space.xs),
            horizontalArrangement = Arrangement.spacedBy(MM.space.m)
        ) {
            val palette = listOf(colors.accent, LearningFixedColors.TealAccent, LearningFixedColors.AmberHighlight)
            equations.forEachIndexed { idx, eq ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(MM.radius.pill)
                            .background(palette[idx % palette.size])
                    )
                    Spacer(Modifier.width(MM.space.xs))
                    Text("f(x) = $eq", style = MM.type.caption, color = colors.ink)
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(horizontal = MM.space.xs)
        ) {
            val xMin = spec.xMin
            val xMax = spec.xMax
            val xRange = (xMax - xMin).coerceAtLeast(0.1)

            // Sample points to establish y bounds
            val samples = 140
            var computedYMin = Double.MAX_VALUE
            var computedYMax = Double.MIN_VALUE

            for (eq in equations) {
                for (i in 0..samples) {
                    val x = xMin + (i.toDouble() / samples) * xRange
                    val y = MathExprEvaluator.evaluate(eq, x)
                    if (y != null && !y.isNaN() && !y.isInfinite() && abs(y) < 100.0) {
                        if (y < computedYMin) computedYMin = y
                        if (y > computedYMax) computedYMax = y
                    }
                }
            }

            if (computedYMin >= computedYMax) {
                computedYMin = -5.0
                computedYMax = 5.0
            }
            // Add margin to y
            val ySpread = (computedYMax - computedYMin).coerceAtLeast(1.0)
            val yMin = computedYMin - ySpread * 0.1
            val yMax = computedYMax + ySpread * 0.1
            val yRange = yMax - yMin

            val plotWidth = size.width
            val plotHeight = size.height

            // Background grid
            for (i in 0..4) {
                val y = plotHeight * (i / 4f)
                drawLine(colors.lineSoft, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1.dp.toPx())
                val x = plotWidth * (i / 4f)
                drawLine(colors.lineSoft, Offset(x, 0f), Offset(x, plotHeight), strokeWidth = 1.dp.toPx())
            }

            // Zero axes
            if (xMin <= 0.0 && xMax >= 0.0) {
                val zeroX = (((0.0 - xMin) / xRange) * plotWidth).toFloat()
                drawLine(colors.line, Offset(zeroX, 0f), Offset(zeroX, plotHeight), strokeWidth = 1.5.dp.toPx())
            }
            if (yMin <= 0.0 && yMax >= 0.0) {
                val zeroY = plotHeight - (((0.0 - yMin) / yRange) * plotHeight).toFloat()
                drawLine(colors.line, Offset(0f, zeroY), Offset(plotWidth, zeroY), strokeWidth = 1.5.dp.toPx())
            }

            // Draw curves
            val palette = listOf(colors.accent, LearningFixedColors.TealAccent, LearningFixedColors.AmberHighlight)
            equations.forEachIndexed { idx, eq ->
                val curveColor = palette[idx % palette.size]
                val path = Path()
                var hasStarted = false

                for (s in 0..samples) {
                    val xVal = xMin + (s.toDouble() / samples) * xRange
                    val yVal = MathExprEvaluator.evaluate(eq, xVal)
                    if (yVal != null && !yVal.isNaN() && !yVal.isInfinite() && abs(yVal) < 100.0) {
                        val px = (s.toFloat() / samples) * plotWidth
                        val py = (plotHeight - (((yVal - yMin) / yRange) * plotHeight).toFloat()).coerceIn(0f, plotHeight)
                        if (!hasStarted) {
                            path.moveTo(px, py)
                            hasStarted = true
                        } else {
                            path.lineTo(px, py)
                        }
                    } else {
                        hasStarted = false
                    }
                }

                drawPath(
                    path = path,
                    color = curveColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            // Axis range indicators
            val minText = measurer.measure("${xMin.toInt()}", style = captionStyle.copy(color = colors.inkMuted))
            drawText(textLayoutResult = minText, topLeft = Offset(4.dp.toPx(), plotHeight - minText.size.height - 2.dp.toPx()))
            val maxText = measurer.measure("${xMax.toInt()}", style = captionStyle.copy(color = colors.inkMuted))
            drawText(textLayoutResult = maxText, topLeft = Offset(plotWidth - maxText.size.width - 4.dp.toPx(), plotHeight - maxText.size.height - 2.dp.toPx()))
        }
    }
}

/**
 * Pie chart breakdown renderer with donut styling and legend.
 */
@Composable
fun PieChartCanvas(
    spec: LearningVisualSpec,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val points = remember(spec.series) { spec.series.firstOrNull()?.points.orEmpty() }
    val total = remember(points) { points.sumOf { it.value }.coerceAtLeast(0.001) }
    val palette = listOf(
        colors.accent,
        LearningFixedColors.TealAccent,
        LearningFixedColors.AmberHighlight,
        LearningFixedColors.TheoremAccent,
        LearningFixedColors.MasteryStrong,
        LearningFixedColors.PitfallAccent
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = MM.space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
        ) {
            if (points.isEmpty()) return@Canvas

            val diameter = size.minDimension * 0.92f
            val radius = diameter / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeW = radius * 0.44f

            var startAngle = -90f
            points.forEachIndexed { idx, pt ->
                val sweep = ((pt.value / total) * 360f).toFloat()
                val sliceColor = palette[idx % palette.size]
                drawArc(
                    color = sliceColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius + strokeW / 2f, center.y - radius + strokeW / 2f),
                    size = Size(diameter - strokeW, diameter - strokeW),
                    style = Stroke(width = strokeW)
                )
                startAngle += sweep
            }
        }

        Spacer(Modifier.width(MM.space.m))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MM.space.xs)
        ) {
            points.take(5).forEachIndexed { idx, pt ->
                val sliceColor = palette[idx % palette.size]
                val pct = (pt.value / total) * 100
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(MM.radius.pill)
                            .background(sliceColor)
                    )
                    Spacer(Modifier.width(MM.space.xs))
                    Text(
                        text = "${pt.label} (${String.format("%.0f%%", pct)})",
                        style = MM.type.caption,
                        color = colors.ink
                    )
                }
            }
        }
    }
}

/**
 * Sequential process and timeline renderer with step connectors.
 */
@Composable
fun TimelineView(
    spec: LearningVisualSpec,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val nodes = remember(spec.timeline) {
        spec.timeline.ifEmpty {
            listOf(
                TimelineNode("1", "Phase 1", "Initial setup and prerequisite conditions."),
                TimelineNode("2", "Phase 2", "Primary mechanism and state transformation."),
                TimelineNode("3", "Phase 3", "System stabilization and observable outcome.")
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MM.space.s)
    ) {
        nodes.forEachIndexed { idx, node ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(36.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(MM.radius.pill)
                            .background(colors.accentWash),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = node.step,
                            style = MM.type.caption,
                            color = colors.accent
                        )
                    }
                    if (idx < nodes.size - 1) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(32.dp)
                                .background(colors.lineSoft)
                        )
                    }
                }

                Spacer(Modifier.width(MM.space.s))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.title,
                        style = MM.type.bodyStrong,
                        color = colors.ink
                    )
                    if (node.detail.isNotBlank()) {
                        Spacer(Modifier.height(MM.space.xs))
                        Text(
                            text = node.detail,
                            style = MM.type.body,
                            color = colors.inkSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean diagram image renderer with tap-to-zoom dialog.
 */
@Composable
fun AiDiagramImageView(
    imagePath: String?,
    caption: String = "",
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    val bitmap = remember(imagePath) {
        imagePath?.let { path ->
            runCatching {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    var showZoom by remember { mutableStateOf(false) }

    if (bitmap != null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MM.radius.card)
                .background(colors.surfaceSunk)
                .clickable { showZoom = true }
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = caption.ifBlank { "Educational diagram image" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(MM.space.s)
                    .clip(MM.radius.pill)
                    .background(colors.surface.copy(alpha = 0.82f))
                    .padding(horizontal = MM.space.s, vertical = MM.space.xs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Fullscreen,
                        contentDescription = "Expand diagram image",
                        tint = colors.ink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(MM.space.xs))
                    Text("Zoom", style = MM.type.caption, color = colors.ink)
                }
            }
        }

        if (showZoom) {
            AlertDialog(
                onDismissRequest = { showZoom = false },
                title = {
                    if (caption.isNotBlank()) {
                        Text(caption, style = MM.type.heading, color = colors.ink)
                    }
                },
                text = {
                    Image(
                        bitmap = bitmap,
                        contentDescription = caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .clip(MM.radius.card)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showZoom = false }) {
                        Text("Close", style = MM.type.bodyStrong, color = colors.accent)
                    }
                }
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(MM.radius.card)
                .background(colors.surfaceSunk),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = colors.inkMuted,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.height(MM.space.xs))
                Text(
                    text = "Diagram image preview unavailable",
                    style = MM.type.secondary,
                    color = colors.inkMuted
                )
            }
        }
    }
}

/**
 * Universal educational visual card renderer.
 */
@Composable
fun LearningVisualCard(
    spec: LearningVisualSpec,
    onShareImage: ((LearningVisualSpec) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = MM.colors
    MMCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = MM.space.m
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = spec.title,
                    style = MM.type.title,
                    color = colors.ink
                )
                if (spec.subtitle.isNotBlank()) {
                    Spacer(Modifier.height(MM.space.xs))
                    Text(
                        text = spec.subtitle,
                        style = MM.type.secondary,
                        color = colors.inkSecondary
                    )
                }
            }
            Spacer(Modifier.width(MM.space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MM.radius.pill,
                    color = colors.accentWash
                ) {
                    Text(
                        text = spec.kind.label,
                        style = MM.type.caption,
                        color = colors.accent,
                        modifier = Modifier.padding(horizontal = MM.space.s, vertical = MM.space.xs)
                    )
                }
                if (onShareImage != null) {
                    Spacer(Modifier.width(MM.space.xs))
                    IconButton(
                        onClick = { onShareImage(spec) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share visual",
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(MM.space.m))

        when (spec.kind) {
            LearningVisualKind.BAR_CHART -> BarChartCanvas(spec = spec)
            LearningVisualKind.LINE_CHART -> LineChartCanvas(spec = spec)
            LearningVisualKind.FUNCTION_PLOT -> FunctionPlotCanvas(spec = spec)
            LearningVisualKind.PIE_CHART -> PieChartCanvas(spec = spec)
            LearningVisualKind.TIMELINE -> TimelineView(spec = spec)
            LearningVisualKind.MERMAID_DIAGRAM -> DiagramBlock(code = spec.mermaidCode)
            LearningVisualKind.AI_IMAGE -> AiDiagramImageView(imagePath = spec.imagePath, caption = spec.caption)
            LearningVisualKind.AUTO -> {
                when {
                    spec.imagePath != null -> AiDiagramImageView(imagePath = spec.imagePath, caption = spec.caption)
                    spec.mermaidCode.isNotBlank() -> DiagramBlock(code = spec.mermaidCode)
                    spec.equations.isNotEmpty() -> FunctionPlotCanvas(spec = spec)
                    spec.timeline.isNotEmpty() -> TimelineView(spec = spec)
                    spec.series.isNotEmpty() -> {
                        val count = spec.series.firstOrNull()?.points?.size ?: 0
                        if (count <= 5) BarChartCanvas(spec = spec) else LineChartCanvas(spec = spec)
                    }
                    else -> DiagramBlock(code = spec.mermaidCode)
                }
            }
        }

        if (spec.caption.isNotBlank()) {
            Spacer(Modifier.height(MM.space.m))
            Text(
                text = spec.caption,
                style = MM.type.caption,
                color = colors.inkSecondary
            )
        }
    }
}

/**
 * Exports and shares a visual spec as PNG via FileProvider and Intent.ACTION_SEND.
 */
fun shareVisualSpecAsPng(context: Context, spec: LearningVisualSpec) {
    val shareDir = File(context.cacheDir, "learning_shares").apply { mkdirs() }
    val outFile = File(shareDir, "visual_${spec.id}_${System.currentTimeMillis()}.png")

    val srcImage = spec.imagePath?.let { File(it) }
    if (srcImage != null && srcImage.exists() && srcImage.length() > 0) {
        srcImage.copyTo(outFile, overwrite = true)
    } else {
        val width = 1200
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply {
            color = android.graphics.Color.rgb(24, 24, 27)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val borderPaint = Paint().apply {
            color = android.graphics.Color.rgb(39, 39, 42)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(RectF(16f, 16f, width - 16f, height - 16f), 32f, 32f, borderPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 42f
            isFakeBoldText = true
        }
        canvas.drawText(spec.title, 64f, 90f, titlePaint)

        if (spec.subtitle.isNotBlank()) {
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(161, 161, 170)
                textSize = 26f
            }
            canvas.drawText(spec.subtitle, 64f, 132f, subPaint)
        }

        val contentLeft = 80f
        val contentTop = 180f
        val contentRight = width - 80f
        val contentBottom = 680f

        val accentColor = LearningFixedColors.SapphireMid.toArgb()
        val tealColor = LearningFixedColors.TealAccent.toArgb()
        val amberColor = LearningFixedColors.AmberHighlight.toArgb()

        when (spec.kind) {
            LearningVisualKind.BAR_CHART, LearningVisualKind.AUTO -> {
                val points = spec.series.firstOrNull()?.points ?: listOf(
                    ChartPoint("A", 25.0), ChartPoint("B", 50.0), ChartPoint("C", 80.0), ChartPoint("D", 65.0)
                )
                val maxVal = points.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    style = Paint.Style.FILL
                }
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(212, 212, 216)
                    textSize = 22f
                    textAlign = Paint.Align.CENTER
                }
                val axisPaint = Paint().apply {
                    color = android.graphics.Color.rgb(63, 63, 70)
                    strokeWidth = 2f
                }
                canvas.drawLine(contentLeft, contentBottom, contentRight, contentBottom, axisPaint)

                val n = points.size
                val slotW = (contentRight - contentLeft) / n
                val barW = slotW * 0.6f

                points.forEachIndexed { idx, pt ->
                    val cx = contentLeft + slotW * idx + slotW / 2f
                    val barH = ((pt.value / maxVal) * (contentBottom - contentTop)).toFloat().coerceAtLeast(8f)
                    val top = contentBottom - barH
                    canvas.drawRoundRect(RectF(cx - barW / 2f, top, cx + barW / 2f, contentBottom), 10f, 10f, barPaint)
                    canvas.drawText(pt.label, cx, contentBottom + 32f, labelPaint)
                }
            }
            LearningVisualKind.LINE_CHART -> {
                val points = spec.series.firstOrNull()?.points ?: listOf(
                    ChartPoint("t0", 15.0), ChartPoint("t1", 35.0), ChartPoint("t2", 60.0), ChartPoint("t3", 85.0)
                )
                val maxVal = points.maxOfOrNull { it.value }?.coerceAtLeast(1.0) ?: 1.0
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    strokeWidth = 5f
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tealColor
                    style = Paint.Style.FILL
                }
                val path = android.graphics.Path()
                val n = points.size
                val stepX = if (n > 1) (contentRight - contentLeft) / (n - 1) else 0f
                points.forEachIndexed { idx, pt ->
                    val x = contentLeft + idx * stepX
                    val y = contentBottom - ((pt.value / maxVal) * (contentBottom - contentTop)).toFloat()
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, linePaint)
                points.forEachIndexed { idx, pt ->
                    val x = contentLeft + idx * stepX
                    val y = contentBottom - ((pt.value / maxVal) * (contentBottom - contentTop)).toFloat()
                    canvas.drawCircle(x, y, 9f, dotPaint)
                }
            }
            LearningVisualKind.FUNCTION_PLOT -> {
                val eq = spec.equations.firstOrNull() ?: "sin(x)"
                val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    strokeWidth = 4f
                    style = Paint.Style.STROKE
                }
                val axisPaint = Paint().apply {
                    color = android.graphics.Color.rgb(82, 82, 91)
                    strokeWidth = 2f
                }
                val midY = (contentTop + contentBottom) / 2f
                val midX = (contentLeft + contentRight) / 2f
                canvas.drawLine(contentLeft, midY, contentRight, midY, axisPaint)
                canvas.drawLine(midX, contentTop, midX, contentBottom, axisPaint)

                val samples = 120
                val path = android.graphics.Path()
                var started = false
                for (s in 0..samples) {
                    val t = s.toDouble() / samples
                    val xv = spec.xMin + t * (spec.xMax - spec.xMin)
                    val yv = MathExprEvaluator.evaluate(eq, xv)
                    if (yv != null && !yv.isNaN() && !yv.isInfinite()) {
                        val px = contentLeft + t.toFloat() * (contentRight - contentLeft)
                        val py = (midY - (yv * 40.0).toFloat()).coerceIn(contentTop, contentBottom)
                        if (!started) {
                            path.moveTo(px, py)
                            started = true
                        } else {
                            path.lineTo(px, py)
                        }
                    } else {
                        started = false
                    }
                }
                canvas.drawPath(path, curvePaint)
            }
            LearningVisualKind.PIE_CHART -> {
                val points = spec.series.firstOrNull()?.points ?: listOf(
                    ChartPoint("Part 1", 50.0), ChartPoint("Part 2", 30.0), ChartPoint("Part 3", 20.0)
                )
                val total = points.sumOf { it.value }.coerceAtLeast(0.001)
                val cx = (contentLeft + contentRight) / 2f
                val cy = (contentTop + contentBottom) / 2f
                val radius = 180f
                val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                val sliceColors = listOf(accentColor, tealColor, amberColor, LearningFixedColors.TheoremAccent.toArgb())
                var startAngle = -90f
                val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                }
                points.forEachIndexed { idx, pt ->
                    val sweep = ((pt.value / total) * 360f).toFloat()
                    slicePaint.color = sliceColors[idx % sliceColors.size]
                    canvas.drawArc(oval, startAngle, sweep, true, slicePaint)
                    startAngle += sweep
                }
                val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(24, 24, 27)
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, 90f, holePaint)
            }
            LearningVisualKind.TIMELINE -> {
                val nodes = spec.timeline.ifEmpty {
                    listOf(
                        TimelineNode("1", "Phase 1", "Initial conditions"),
                        TimelineNode("2", "Phase 2", "Primary reaction"),
                        TimelineNode("3", "Phase 3", "System resolution")
                    )
                }
                val nodeCount = nodes.size
                val stepH = (contentBottom - contentTop) / nodeCount.coerceAtLeast(1)
                val linePaint = Paint().apply {
                    color = android.graphics.Color.rgb(63, 63, 70)
                    strokeWidth = 3f
                }
                val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    style = Paint.Style.FILL
                }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 26f
                    isFakeBoldText = true
                }
                val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(161, 161, 170)
                    textSize = 20f
                }
                canvas.drawLine(contentLeft + 30f, contentTop + 20f, contentLeft + 30f, contentBottom - 20f, linePaint)
                nodes.forEachIndexed { idx, n ->
                    val ny = contentTop + idx * stepH + 30f
                    canvas.drawCircle(contentLeft + 30f, ny, 16f, circlePaint)
                    canvas.drawText("${n.step}: ${n.title}", contentLeft + 70f, ny + 8f, textPaint)
                    canvas.drawText(n.detail, contentLeft + 70f, ny + 38f, detailPaint)
                }
            }
            LearningVisualKind.MERMAID_DIAGRAM -> {
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(39, 39, 42)
                    style = Paint.Style.FILL
                }
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = accentColor
                    style = Paint.Style.STROKE
                    strokeWidth = 3f
                }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                }
                val b1 = RectF(contentLeft + 50f, contentTop + 100f, contentLeft + 320f, contentTop + 220f)
                val b2 = RectF(contentRight - 320f, contentTop + 100f, contentRight - 50f, contentTop + 220f)
                canvas.drawRoundRect(b1, 16f, 16f, boxPaint)
                canvas.drawRoundRect(b1, 16f, 16f, strokePaint)
                canvas.drawText(spec.title.take(18), b1.centerX(), b1.centerY() + 8f, textPaint)

                canvas.drawRoundRect(b2, 16f, 16f, boxPaint)
                canvas.drawRoundRect(b2, 16f, 16f, strokePaint)
                canvas.drawText("Mechanism", b2.centerX(), b2.centerY() + 8f, textPaint)

                val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tealColor
                    strokeWidth = 4f
                }
                canvas.drawLine(b1.right + 20f, b1.centerY(), b2.left - 20f, b2.centerY(), arrowPaint)
            }
            LearningVisualKind.AI_IMAGE -> {
                val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(161, 161, 170)
                    textSize = 28f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("AI Diagram: ${spec.title}", width / 2f, height / 2f, fallbackPaint)
            }
        }

        if (spec.caption.isNotBlank()) {
            val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(161, 161, 170)
                textSize = 22f
            }
            canvas.drawText(spec.caption.take(80), 64f, 740f, capPaint)
        }

        outFile.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, spec.title)
        if (spec.caption.isNotBlank()) {
            putExtra(Intent.EXTRA_TEXT, "${spec.title}\n${spec.caption}")
        } else {
            putExtra(Intent.EXTRA_TEXT, spec.title)
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(sendIntent, "Share Visual").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}
