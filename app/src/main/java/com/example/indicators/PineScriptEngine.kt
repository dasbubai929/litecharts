package com.example.indicators

import com.example.model.Candle
import com.squareup.moshi.JsonClass
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

const val MAX_CUSTOM_PINE_INDICATORS = 3

@JsonClass(generateAdapter = true)
data class CustomPineIndicator(
    val id: String,
    val name: String,
    val code: String,
    val enabled: Boolean = true,
    val isOverlay: Boolean = true
)

@JsonClass(generateAdapter = true)
data class StoredCustomIndicators(
    val items: List<CustomPineIndicator> = emptyList()
)

data class PinePlotSeries(
    val title: String,
    val values: List<Double?>,
    val colorArgb: Long,
    val lineWidth: Float = 2f,
    val isHistogram: Boolean = false
)

data class PineHLine(
    val price: Double,
    val title: String,
    val colorArgb: Long
)

data class PineShapeMarker(
    val barIndex: Int,
    val price: Double,
    val colorArgb: Long,
    val isBelowBar: Boolean,
    val label: String
)

data class PineEvaluationResult(
    val indicatorId: String,
    val title: String,
    val isOverlay: Boolean,
    val plots: List<PinePlotSeries> = emptyList(),
    val hlines: List<PineHLine> = emptyList(),
    val shapes: List<PineShapeMarker> = emptyList(),
    val error: String? = null
)

/**
 * Pure Kotlin safe Pine Script v4/v5/v6 interpreter supporting user-pasted
 * custom indicators (up to 3 extra slots) on OHLCV candle series.
 *
 * Supports: indicator/study metadata (title & overlay=true/false), input.* defaults,
 * variable & tuple assignments, arithmetic/comparison/ternary expressions, history [n],
 * standard ta.* (sma, ema, rma, wma, vwap, rsi, atr, stdev, highest, lowest, macd, bb,
 * supertrend, dmi, cci, stoch, roc, mom, change, crossover, crossunder), math.*,
 * plot(), hline(), and plotshape().
 */
object PineScriptEngine {

    private val defaultPalette = longArrayOf(
        0xFF5EA8D8L,
        0xFFE0B463L,
        0xFF9C7CE8L,
        0xFF38B28EL,
        0xFFD96B70L,
        0xFF38BDF8L
    )

    data class ScriptHeader(val title: String, val isOverlay: Boolean)

    fun parseHeader(code: String, fallbackName: String = "Custom Pine"): ScriptHeader {
        val cleaned = stripComments(code)
        val headerRegex = Regex("""(?:indicator|study)\s*\(([^)]*)\)""")
        val match = headerRegex.find(cleaned) ?: return ScriptHeader(fallbackName, true)
        val args = match.groupValues[1]
        val titleMatch = Regex("""(?:shorttitle|title)\s*=\s*["']([^"']+)["']""").find(args)
            ?: Regex("""^\s*["']([^"']+)["']""").find(args)
        val title = titleMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackName
        val overlayMatch = Regex("""overlay\s*=\s*(true|false)""").find(args)
        val isOverlay = overlayMatch?.groupValues?.get(1)?.equals("true", ignoreCase = true) ?: false
        return ScriptHeader(title, isOverlay)
    }

    fun evaluate(indicator: CustomPineIndicator, candles: List<Candle>): PineEvaluationResult {
        val header = parseHeader(indicator.code, indicator.name)
        if (candles.isEmpty()) {
            return PineEvaluationResult(indicator.id, header.title, header.isOverlay)
        }
        return try {
            val runtime = Runtime(candles)
            runtime.execute(indicator.code)
            PineEvaluationResult(
                indicatorId = indicator.id,
                title = header.title,
                isOverlay = header.isOverlay,
                plots = runtime.plots,
                hlines = runtime.hlines,
                shapes = runtime.shapes,
                error = if (runtime.plots.isEmpty() && runtime.hlines.isEmpty() && runtime.shapes.isEmpty()) {
                    "No plot(), hline(), or plotshape() calls produced values."
                } else null
            )
        } catch (e: Exception) {
            PineEvaluationResult(
                indicatorId = indicator.id,
                title = header.title,
                isOverlay = header.isOverlay,
                error = e.message ?: "Unable to parse Pine Script"
            )
        }
    }

    private fun stripComments(code: String): String {
        return code.lines().joinToString("\n") { line ->
            var inSingle = false
            var inDouble = false
            var cutAt = line.length
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                if (ch == '\'' && !inDouble) inSingle = !inSingle
                else if (ch == '"' && !inSingle) inDouble = !inDouble
                else if (ch == '/' && i + 1 < line.length && line[i + 1] == '/' && !inSingle && !inDouble) {
                    cutAt = i
                    break
                }
                i++
            }
            line.substring(0, cutAt)
        }
    }

    private class Runtime(private val candles: List<Candle>) {
        private val n = candles.size
        private val env = mutableMapOf<String, DoubleArray>()
        private val colorEnv = mutableMapOf<String, Long>()
        val plots = mutableListOf<PinePlotSeries>()
        val hlines = mutableListOf<PineHLine>()
        val shapes = mutableListOf<PineShapeMarker>()

        init {
            env["open"] = PineTa.sourceSeries(candles, "open")
            env["high"] = PineTa.sourceSeries(candles, "high")
            env["low"] = PineTa.sourceSeries(candles, "low")
            env["close"] = PineTa.sourceSeries(candles, "close")
            env["volume"] = PineTa.sourceSeries(candles, "volume")
            env["hl2"] = PineTa.sourceSeries(candles, "hl2")
            env["hlc3"] = PineTa.sourceSeries(candles, "hlc3")
            env["ohlc4"] = PineTa.sourceSeries(candles, "ohlc4")
            env["hlcc4"] = PineTa.sourceSeries(candles, "hlcc4")
            env["bar_index"] = DoubleArray(n) { it.toDouble() }
            env["na"] = DoubleArray(n) { Double.NaN }
            env["true"] = DoubleArray(n) { 1.0 }
            env["false"] = DoubleArray(n) { 0.0 }
        }

        fun execute(rawCode: String) {
            val cleaned = stripComments(rawCode)
            val lines = mergeContinuationLines(cleaned)

            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("indicator(") || line.startsWith("study(") || line.startsWith("alertcondition(")) continue
                if (line.startsWith("fill(") || line.startsWith("bgcolor(") || line.startsWith("barcolor(")) continue

                when {
                    line.startsWith("plot(") -> handlePlot(line)
                    line.startsWith("hline(") -> handleHLine(line)
                    line.startsWith("plotshape(") -> handlePlotShape(line)
                    line.startsWith("[") && line.contains("=") -> handleTupleAssignment(line)
                    else -> handleAssignment(line)
                }
            }
        }

        private fun mergeContinuationLines(code: String): List<String> {
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var parenDepth = 0
            for (raw in code.lines()) {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) continue
                if (parenDepth > 0) {
                    current.append(' ').append(trimmed)
                } else {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current.clear()
                    }
                    current.append(trimmed)
                }
                for (ch in trimmed) {
                    if (ch == '(' || ch == '[') parenDepth++
                    else if (ch == ')' || ch == ']') parenDepth = max(0, parenDepth - 1)
                }
            }
            if (current.isNotEmpty()) result.add(current.toString())
            return result
        }

        private fun handleTupleAssignment(line: String) {
            val eqIdx = findTopLevelEquals(line)
            if (eqIdx <= 0) return
            val lhs = line.substring(0, eqIdx).trim().removePrefix("[").removeSuffix("]")
            val rhs = line.substring(eqIdx + 1).trim()
            val names = lhs.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val tuple = evalTupleCall(rhs) ?: return
            for (i in names.indices) {
                if (i < tuple.size && names[i] != "_") {
                    env[names[i]] = tuple[i]
                }
            }
        }

        private fun handleAssignment(line: String) {
            val eqIdx = findTopLevelEquals(line)
            if (eqIdx <= 0) return
            val rawLhs = line.substring(0, eqIdx).trim()
            val rhs = line.substring(eqIdx + 1).trim()

            val lhsTokens = rawLhs.split(Regex("\\s+")).filter { it.isNotEmpty() }
            val varName = lhsTokens.lastOrNull() ?: return
            if (!varName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) return

            // Color variable or input.color
            if (rhs.startsWith("#") || rhs.startsWith("color.") || rhs.startsWith("input.color(")) {
                resolveColorExpr(rhs)?.let { colorEnv[varName] = it }
                return
            }

            // Input declarations
            if (rhs.startsWith("input.") || rhs.startsWith("input(")) {
                val parsed = evalInput(rhs)
                if (parsed != null) env[varName] = parsed
                return
            }

            val series = evalExpr(rhs)
            if (series != null) {
                env[varName] = series
            }
        }

        private fun evalInput(expr: String): DoubleArray? {
            val inner = extractCallArgs(expr) ?: return null
            val args = splitTopLevelArgs(inner)
            if (args.isEmpty()) return null
            val defArg = args.firstOrNull { it.startsWith("defval=") }?.substringAfter("=")?.trim()
                ?: args.first().trim()
            if (expr.startsWith("input.color(")) return null
            if (expr.startsWith("input.string(") || defArg.startsWith("\"") || defArg.startsWith("'")) return null
            return evalExpr(defArg)
        }

        private fun handlePlot(line: String) {
            val inner = extractCallArgs(line) ?: return
            val args = splitTopLevelArgs(inner)
            if (args.isEmpty()) return

            val positional = mutableListOf<String>()
            val named = mutableMapOf<String, String>()
            for (a in args) {
                val eq = findTopLevelEquals(a)
                if (eq > 0) {
                    named[a.substring(0, eq).trim()] = a.substring(eq + 1).trim()
                } else {
                    positional.add(a.trim())
                }
            }

            val seriesExpr = named["series"] ?: positional.getOrNull(0) ?: return
            val values = evalExpr(seriesExpr) ?: return

            val titleRaw = named["title"] ?: positional.getOrNull(1)
            val title = titleRaw?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
                ?: "Plot ${plots.size + 1}"

            val colorExpr = named["color"] ?: positional.getOrNull(2)
            val colorArgb = colorExpr?.let { resolveColorExpr(it) }
                ?: defaultPalette[plots.size % defaultPalette.size]

            val widthExpr = named["linewidth"] ?: positional.getOrNull(3)
            val lw = widthExpr?.toFloatOrNull()?.coerceIn(1f, 5f) ?: 2f

            val styleExpr = named["style"] ?: ""
            val isHist = styleExpr.contains("histogram", ignoreCase = true) ||
                styleExpr.contains("columns", ignoreCase = true)

            plots.add(
                PinePlotSeries(
                    title = title,
                    values = values.map { if (it.isNaN()) null else it },
                    colorArgb = colorArgb,
                    lineWidth = lw,
                    isHistogram = isHist
                )
            )
        }

        private fun handleHLine(line: String) {
            val inner = extractCallArgs(line) ?: return
            val args = splitTopLevelArgs(inner)
            if (args.isEmpty()) return
            val named = mutableMapOf<String, String>()
            val positional = mutableListOf<String>()
            for (a in args) {
                val eq = findTopLevelEquals(a)
                if (eq > 0) named[a.substring(0, eq).trim()] = a.substring(eq + 1).trim()
                else positional.add(a.trim())
            }
            val priceExpr = named["price"] ?: positional.getOrNull(0) ?: return
            val price = evalExpr(priceExpr)?.lastOrNull { !it.isNaN() } ?: return
            val title = (named["title"] ?: positional.getOrNull(1))
                ?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
            val color = (named["color"] ?: positional.getOrNull(2))
                ?.let { resolveColorExpr(it) } ?: 0xFF8E9EB3L
            hlines.add(PineHLine(price, title, color))
        }

        private fun handlePlotShape(line: String) {
            val inner = extractCallArgs(line) ?: return
            val args = splitTopLevelArgs(inner)
            if (args.isEmpty()) return
            val named = mutableMapOf<String, String>()
            val positional = mutableListOf<String>()
            for (a in args) {
                val eq = findTopLevelEquals(a)
                if (eq > 0) named[a.substring(0, eq).trim()] = a.substring(eq + 1).trim()
                else positional.add(a.trim())
            }
            val condExpr = named["series"] ?: positional.getOrNull(0) ?: return
            val cond = evalExpr(condExpr) ?: return
            val location = named["location"] ?: "location.abovebar"
            val isBelow = location.contains("below", ignoreCase = true)
            val color = named["color"]?.let { resolveColorExpr(it) }
                ?: if (isBelow) 0xFF38B28EL else 0xFFD96B70L
            val text = named["text"]?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""

            for (i in 0 until n) {
                if (!cond[i].isNaN() && cond[i] != 0.0) {
                    val price = if (isBelow) candles[i].low else candles[i].high
                    shapes.add(PineShapeMarker(i, price, color, isBelow, text))
                }
            }
        }

        private fun evalTupleCall(expr: String): List<DoubleArray>? {
            val name = expr.substringBefore("(").trim()
            val inner = extractCallArgs(expr) ?: return null
            val args = splitTopLevelArgs(inner)
            return when (name) {
                "ta.macd", "macd" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: env["close"]!!
                    val fast = scalarInt(args.getOrNull(1), 12)
                    val slow = scalarInt(args.getOrNull(2), 26)
                    val sig = scalarInt(args.getOrNull(3), 9)
                    val r = PineTa.macd(src, fast, slow, sig)
                    listOf(r.macdLine, r.signalLine, r.histogram)
                }
                "ta.bb", "bb" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: env["close"]!!
                    val len = scalarInt(args.getOrNull(1), 20)
                    val mult = scalarDouble(args.getOrNull(2), 2.0)
                    val mid = PineTa.sma(src, len)
                    val sd = PineTa.stdev(src, len)
                    val up = DoubleArray(n) { i -> if (mid[i].isNaN() || sd[i].isNaN()) Double.NaN else mid[i] + sd[i] * mult }
                    val lo = DoubleArray(n) { i -> if (mid[i].isNaN() || sd[i].isNaN()) Double.NaN else mid[i] - sd[i] * mult }
                    listOf(mid, up, lo)
                }
                "ta.kc", "kc" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: env["close"]!!
                    val len = scalarInt(args.getOrNull(1), 20)
                    val mult = scalarDouble(args.getOrNull(2), 1.5)
                    val mid = PineTa.ema(src, len)
                    val atr = PineTa.atr(candles, len)
                    val up = DoubleArray(n) { i -> if (mid[i].isNaN() || atr[i].isNaN()) Double.NaN else mid[i] + atr[i] * mult }
                    val lo = DoubleArray(n) { i -> if (mid[i].isNaN() || atr[i].isNaN()) Double.NaN else mid[i] - atr[i] * mult }
                    listOf(mid, up, lo)
                }
                "ta.dmi", "dmi" -> {
                    val diLen = scalarInt(args.getOrNull(0), 14)
                    val adxSm = scalarInt(args.getOrNull(1), 14)
                    val r = PineTa.dmi(candles, diLen, adxSm)
                    listOf(r.plusDi, r.minusDi, r.adx)
                }
                "ta.supertrend", "supertrend" -> {
                    val factor = scalarDouble(args.getOrNull(0), 3.0)
                    val atrLen = scalarInt(args.getOrNull(1), 10)
                    evalSupertrend(factor, atrLen)
                }
                else -> null
            }
        }

        private fun evalSupertrend(factor: Double, atrLen: Int): List<DoubleArray> {
            val atr = PineTa.atr(candles, atrLen)
            val st = DoubleArray(n) { Double.NaN }
            val dir = DoubleArray(n) { 1.0 }
            val upperBand = DoubleArray(n) { Double.NaN }
            val lowerBand = DoubleArray(n) { Double.NaN }

            for (i in 0 until n) {
                val hl2 = (candles[i].high + candles[i].low) * 0.5
                val a = atr[i]
                if (a.isNaN()) continue
                var up = hl2 + factor * a
                var lo = hl2 - factor * a
                if (i > 0 && !lowerBand[i - 1].isNaN() && !upperBand[i - 1].isNaN()) {
                    val prevClose = candles[i - 1].close
                    lo = if (lo > lowerBand[i - 1] || prevClose < lowerBand[i - 1]) lo else lowerBand[i - 1]
                    up = if (up < upperBand[i - 1] || prevClose > upperBand[i - 1]) up else upperBand[i - 1]
                    val prevDir = dir[i - 1]
                    dir[i] = when {
                        prevDir == -1.0 && candles[i].close < lo -> 1.0
                        prevDir == 1.0 && candles[i].close > up -> -1.0
                        else -> prevDir
                    }
                }
                upperBand[i] = up
                lowerBand[i] = lo
                st[i] = if (dir[i] == -1.0) lo else up
            }
            return listOf(st, dir)
        }

        private fun evalExpr(raw: String): DoubleArray? {
            val s = stripOuterParens(raw.trim())
            if (s.isEmpty()) return null

            // 1. Ternary: cond ? a : b
            val qIdx = findTopLevelChar(s, '?')
            if (qIdx > 0) {
                val cIdx = findTopLevelChar(s, ':', startFrom = qIdx + 1)
                if (cIdx > qIdx) {
                    val cond = evalExpr(s.substring(0, qIdx)) ?: return null
                    val whenTrue = evalExpr(s.substring(qIdx + 1, cIdx)) ?: DoubleArray(n) { Double.NaN }
                    val whenFalse = evalExpr(s.substring(cIdx + 1)) ?: DoubleArray(n) { Double.NaN }
                    return DoubleArray(n) { i ->
                        if (!cond[i].isNaN() && cond[i] != 0.0) whenTrue[i] else whenFalse[i]
                    }
                }
            }

            // 2. Logical OR / AND
            findTopLevelKeyword(s, "or")?.let { idx ->
                val l = evalExpr(s.substring(0, idx)) ?: return null
                val r = evalExpr(s.substring(idx + 2)) ?: return null
                return DoubleArray(n) { i -> if ((!l[i].isNaN() && l[i] != 0.0) || (!r[i].isNaN() && r[i] != 0.0)) 1.0 else 0.0 }
            }
            findTopLevelKeyword(s, "and")?.let { idx ->
                val l = evalExpr(s.substring(0, idx)) ?: return null
                val r = evalExpr(s.substring(idx + 3)) ?: return null
                return DoubleArray(n) { i -> if ((!l[i].isNaN() && l[i] != 0.0) && (!r[i].isNaN() && r[i] != 0.0)) 1.0 else 0.0 }
            }

            // 3. Comparisons
            for (op in listOf(">=", "<=", "==", "!=", ">", "<")) {
                val idx = findTopLevelOperator(s, op)
                if (idx > 0) {
                    val l = evalExpr(s.substring(0, idx)) ?: return null
                    val r = evalExpr(s.substring(idx + op.length)) ?: return null
                    return DoubleArray(n) { i ->
                        if (l[i].isNaN() || r[i].isNaN()) 0.0 else when (op) {
                            ">=" -> if (l[i] >= r[i]) 1.0 else 0.0
                            "<=" -> if (l[i] <= r[i]) 1.0 else 0.0
                            "==" -> if (l[i] == r[i]) 1.0 else 0.0
                            "!=" -> if (l[i] != r[i]) 1.0 else 0.0
                            ">" -> if (l[i] > r[i]) 1.0 else 0.0
                            "<" -> if (l[i] < r[i]) 1.0 else 0.0
                            else -> 0.0
                        }
                    }
                }
            }

            // 4. Addition / Subtraction (rightmost at top level)
            val addSubIdx = findRightmostTopLevelAddSub(s)
            if (addSubIdx > 0) {
                val op = s[addSubIdx]
                val l = evalExpr(s.substring(0, addSubIdx)) ?: return null
                val r = evalExpr(s.substring(addSubIdx + 1)) ?: return null
                return DoubleArray(n) { i ->
                    if (l[i].isNaN() || r[i].isNaN()) Double.NaN
                    else if (op == '+') l[i] + r[i] else l[i] - r[i]
                }
            }

            // 5. Multiplication / Division
            val mulDivIdx = findRightmostTopLevelMulDiv(s)
            if (mulDivIdx > 0) {
                val op = s[mulDivIdx]
                val l = evalExpr(s.substring(0, mulDivIdx)) ?: return null
                val r = evalExpr(s.substring(mulDivIdx + 1)) ?: return null
                return DoubleArray(n) { i ->
                    if (l[i].isNaN() || r[i].isNaN()) Double.NaN
                    else if (op == '*') l[i] * r[i]
                    else if (r[i] != 0.0) l[i] / r[i] else Double.NaN
                }
            }

            // 6. Unary minus / not
            if (s.startsWith("-")) {
                val inner = evalExpr(s.substring(1)) ?: return null
                return DoubleArray(n) { i -> if (inner[i].isNaN()) Double.NaN else -inner[i] }
            }
            if (s.startsWith("not ")) {
                val inner = evalExpr(s.substring(4)) ?: return null
                return DoubleArray(n) { i -> if (!inner[i].isNaN() && inner[i] != 0.0) 0.0 else 1.0 }
            }

            // 7. Numeric literal
            s.toDoubleOrNull()?.let { num -> return DoubleArray(n) { num } }

            // 8. History indexing: expr[k]
            if (s.endsWith("]")) {
                val openBracket = findMatchingOpenBracket(s)
                if (openBracket > 0) {
                    val base = evalExpr(s.substring(0, openBracket)) ?: return null
                    val offset = scalarInt(s.substring(openBracket + 1, s.length - 1), 1).coerceAtLeast(0)
                    return DoubleArray(n) { i -> if (i - offset >= 0) base[i - offset] else Double.NaN }
                }
            }

            // 9. Function call
            if (s.endsWith(")") && s.contains("(")) {
                return evalFunctionCall(s)
            }

            // 10. Variable lookup
            return env[s]
        }

        private fun evalFunctionCall(expr: String): DoubleArray? {
            val fnName = expr.substringBefore("(").trim()
            val inner = extractCallArgs(expr) ?: return null
            val args = splitTopLevelArgs(inner)

            return when (fnName) {
                "ta.sma", "sma" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    PineTa.sma(src, scalarInt(args.getOrNull(1), 14))
                }
                "ta.ema", "ema" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    PineTa.ema(src, scalarInt(args.getOrNull(1), 14))
                }
                "ta.rma", "rma" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    PineTa.rma(src, scalarInt(args.getOrNull(1), 14))
                }
                "ta.wma", "wma" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    PineTa.wma(src, scalarInt(args.getOrNull(1), 14))
                }
                "ta.hma", "hma" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val len = scalarInt(args.getOrNull(1), 14)
                    val half = PineTa.wma(src, max(1, len / 2))
                    val full = PineTa.wma(src, len)
                    val diff = DoubleArray(n) { i -> if (half[i].isNaN() || full[i].isNaN()) Double.NaN else 2.0 * half[i] - full[i] }
                    PineTa.wma(diff, max(1, sqrt(len.toDouble()).toInt()))
                }
                "ta.vwap", "vwap" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: env["hlc3"]!!
                    PineTa.dailyVwap(candles, src).first
                }
                "ta.rsi", "rsi" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    PineTa.rsi(src, scalarInt(args.getOrNull(1), 14))
                }
                "ta.atr", "atr" -> PineTa.atr(candles, scalarInt(args.getOrNull(0), 14))
                "ta.tr", "tr" -> PineTa.trueRange(candles)
                "ta.stdev", "stdev" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    PineTa.stdev(src, scalarInt(args.getOrNull(1), 20))
                }
                "ta.highest", "highest" -> {
                    val (src, len) = if (args.size >= 2) {
                        (evalExpr(args[0]) ?: env["high"]!!) to scalarInt(args[1], 14)
                    } else {
                        env["high"]!! to scalarInt(args.getOrNull(0), 14)
                    }
                    PineTa.highest(src, len)
                }
                "ta.lowest", "lowest" -> {
                    val (src, len) = if (args.size >= 2) {
                        (evalExpr(args[0]) ?: env["low"]!!) to scalarInt(args[1], 14)
                    } else {
                        env["low"]!! to scalarInt(args.getOrNull(0), 14)
                    }
                    PineTa.lowest(src, len)
                }
                "ta.change", "change" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val len = scalarInt(args.getOrNull(1), 1).coerceAtLeast(1)
                    DoubleArray(n) { i -> if (i >= len) src[i] - src[i - len] else Double.NaN }
                }
                "ta.mom", "mom" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val len = scalarInt(args.getOrNull(1), 10).coerceAtLeast(1)
                    DoubleArray(n) { i -> if (i >= len) src[i] - src[i - len] else Double.NaN }
                }
                "ta.roc", "roc" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val len = scalarInt(args.getOrNull(1), 9).coerceAtLeast(1)
                    DoubleArray(n) { i ->
                        if (i >= len && src[i - len] != 0.0) 100.0 * (src[i] - src[i - len]) / src[i - len] else Double.NaN
                    }
                }
                "ta.stoch", "stoch" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: env["close"]!!
                    val hi = args.getOrNull(1)?.let { evalExpr(it) } ?: env["high"]!!
                    val lo = args.getOrNull(2)?.let { evalExpr(it) } ?: env["low"]!!
                    val len = scalarInt(args.getOrNull(3), 14)
                    val hh = PineTa.highest(hi, len)
                    val ll = PineTa.lowest(lo, len)
                    DoubleArray(n) { i ->
                        val span = hh[i] - ll[i]
                        if (span.isNaN() || span <= 0.0) Double.NaN else 100.0 * (src[i] - ll[i]) / span
                    }
                }
                "ta.cci", "cci" -> {
                    val src = args.getOrNull(0)?.let { evalExpr(it) } ?: env["hlc3"]!!
                    val len = scalarInt(args.getOrNull(1), 20)
                    val ma = PineTa.sma(src, len)
                    DoubleArray(n) { i ->
                        if (i < len - 1 || ma[i].isNaN()) Double.NaN else {
                            var md = 0.0
                            for (k in (i - len + 1)..i) md += abs(src[k] - ma[i])
                            md /= len
                            if (md > 1e-12) (src[i] - ma[i]) / (0.015 * md) else 0.0
                        }
                    }
                }
                "ta.crossover", "crossover" -> {
                    val a = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val b = args.getOrNull(1)?.let { evalExpr(it) } ?: return null
                    DoubleArray(n) { i ->
                        if (i > 0 && !a[i].isNaN() && !b[i].isNaN() && !a[i - 1].isNaN() && !b[i - 1].isNaN() &&
                            a[i - 1] <= b[i - 1] && a[i] > b[i]
                        ) 1.0 else 0.0
                    }
                }
                "ta.crossunder", "crossunder" -> {
                    val a = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val b = args.getOrNull(1)?.let { evalExpr(it) } ?: return null
                    DoubleArray(n) { i ->
                        if (i > 0 && !a[i].isNaN() && !b[i].isNaN() && !a[i - 1].isNaN() && !b[i - 1].isNaN() &&
                            a[i - 1] >= b[i - 1] && a[i] < b[i]
                        ) 1.0 else 0.0
                    }
                }
                "math.abs", "abs" -> args.getOrNull(0)?.let { evalExpr(it) }?.let { a -> DoubleArray(n) { abs(a[it]) } }
                "math.sqrt", "sqrt" -> args.getOrNull(0)?.let { evalExpr(it) }?.let { a -> DoubleArray(n) { if (a[it] >= 0.0) sqrt(a[it]) else Double.NaN } }
                "math.log", "log" -> args.getOrNull(0)?.let { evalExpr(it) }?.let { a -> DoubleArray(n) { if (a[it] > 0.0) ln(a[it]) else Double.NaN } }
                "math.round", "round" -> args.getOrNull(0)?.let { evalExpr(it) }?.let { a -> DoubleArray(n) { round(a[it]) } }
                "math.max", "max" -> {
                    val a = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val b = args.getOrNull(1)?.let { evalExpr(it) } ?: return null
                    DoubleArray(n) { max(a[it], b[it]) }
                }
                "math.min", "min" -> {
                    val a = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val b = args.getOrNull(1)?.let { evalExpr(it) } ?: return null
                    DoubleArray(n) { min(a[it], b[it]) }
                }
                "math.avg", "avg" -> {
                    val seriesList = args.mapNotNull { evalExpr(it) }
                    if (seriesList.isEmpty()) null else DoubleArray(n) { i ->
                        var sum = 0.0
                        for (s in seriesList) sum += s[i]
                        sum / seriesList.size
                    }
                }
                "nz" -> {
                    val a = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    val rep = args.getOrNull(1)?.let { evalExpr(it) } ?: DoubleArray(n) { 0.0 }
                    DoubleArray(n) { i -> if (a[i].isNaN()) rep[i] else a[i] }
                }
                "na" -> {
                    val a = args.getOrNull(0)?.let { evalExpr(it) } ?: return null
                    DoubleArray(n) { i -> if (a[i].isNaN()) 1.0 else 0.0 }
                }
                else -> null
            }
        }

        private fun resolveColorExpr(raw: String): Long? {
            val s = raw.trim()
            colorEnv[s]?.let { return it }
            if (s.startsWith("input.color(")) {
                val inner = extractCallArgs(s) ?: return null
                val first = splitTopLevelArgs(inner).firstOrNull()?.substringAfter("=")?.trim() ?: return null
                return resolveColorExpr(first)
            }
            if (s.startsWith("color.new(")) {
                val inner = extractCallArgs(s) ?: return null
                val parts = splitTopLevelArgs(inner)
                val base = parts.getOrNull(0)?.let { resolveColorExpr(it) } ?: return null
                val transp = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 100) ?: 0
                val alpha = (((100 - transp) / 100.0) * 255.0).toInt().coerceIn(0, 255)
                return ((alpha.toLong() shl 24) or (base and 0x00FFFFFFL))
            }
            if (s.startsWith("#")) {
                val hex = s.removePrefix("#")
                return when (hex.length) {
                    6 -> ("FF$hex").toLongOrNull(16)
                    8 -> hex.toLongOrNull(16)
                    else -> null
                }
            }
            return when (s.lowercase()) {
                "color.green" -> 0xFF38B28EL
                "color.red" -> 0xFFD96B70L
                "color.blue" -> 0xFF5EA8D8L
                "color.orange" -> 0xFFE09F5AL
                "color.purple" -> 0xFF9C7CE8L
                "color.yellow" -> 0xFFE0B463L
                "color.aqua" -> 0xFF38BDF8L
                "color.teal" -> 0xFF26A69AL
                "color.lime" -> 0xFF84CC16L
                "color.fuchsia" -> 0xFFE879F9L
                "color.white" -> 0xFFE6EDF5L
                "color.gray", "color.silver" -> 0xFF8E9EB3L
                "color.maroon" -> 0xFF9F3A47L
                "color.navy" -> 0xFF1E4D6BL
                "color.olive" -> 0xFF808000L
                else -> null
            }
        }

        private fun scalarInt(expr: String?, default: Int): Int {
            if (expr == null) return default
            val clean = expr.substringAfter("=").trim()
            return evalExpr(clean)?.lastOrNull { !it.isNaN() }?.toInt() ?: default
        }

        private fun scalarDouble(expr: String?, default: Double): Double {
            if (expr == null) return default
            val clean = expr.substringAfter("=").trim()
            return evalExpr(clean)?.lastOrNull { !it.isNaN() } ?: default
        }

        private fun extractCallArgs(s: String): String? {
            val open = s.indexOf('(')
            val close = s.lastIndexOf(')')
            if (open < 0 || close <= open) return null
            return s.substring(open + 1, close)
        }

        private fun stripOuterParens(s: String): String {
            var res = s
            while (res.startsWith("(") && res.endsWith(")")) {
                var depth = 0
                var wrapsAll = true
                for (i in 0 until res.length - 1) {
                    when (res[i]) {
                        '(' -> depth++
                        ')' -> {
                            depth--
                            if (depth == 0) {
                                wrapsAll = false
                                break
                            }
                        }
                    }
                }
                if (wrapsAll) res = res.substring(1, res.length - 1).trim() else break
            }
            return res
        }

        private fun splitTopLevelArgs(s: String): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var depth = 0
            var inStr = false
            var strChar = '"'
            for (ch in s) {
                if (inStr) {
                    cur.append(ch)
                    if (ch == strChar) inStr = false
                    continue
                }
                when (ch) {
                    '"', '\'' -> { inStr = true; strChar = ch; cur.append(ch) }
                    '(', '[' -> { depth++; cur.append(ch) }
                    ')', ']' -> { depth--; cur.append(ch) }
                    ',' -> if (depth == 0) { out.add(cur.toString().trim()); cur.clear() } else cur.append(ch)
                    else -> cur.append(ch)
                }
            }
            if (cur.isNotEmpty()) out.add(cur.toString().trim())
            return out
        }

        private fun findTopLevelEquals(s: String): Int {
            var depth = 0
            var inStr = false
            var strChar = '"'
            for (i in s.indices) {
                val ch = s[i]
                if (inStr) { if (ch == strChar) inStr = false; continue }
                when (ch) {
                    '"', '\'' -> { inStr = true; strChar = ch }
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    '=' -> if (depth == 0) {
                        val prev = s.getOrNull(i - 1)
                        val next = s.getOrNull(i + 1)
                        if (prev != '!' && prev != '=' && prev != '>' && prev != '<' && prev != ':' && next != '=') {
                            return i
                        }
                    }
                }
            }
            return -1
        }

        private fun findTopLevelChar(s: String, target: Char, startFrom: Int = 0): Int {
            var depth = 0
            var inStr = false
            var strChar = '"'
            for (i in startFrom until s.length) {
                val ch = s[i]
                if (inStr) { if (ch == strChar) inStr = false; continue }
                when (ch) {
                    '"', '\'' -> { inStr = true; strChar = ch }
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    target -> if (depth == 0) return i
                }
            }
            return -1
        }

        private fun findTopLevelKeyword(s: String, kw: String): Int? {
            var depth = 0
            var inStr = false
            var strChar = '"'
            val len = kw.length
            for (i in 0..s.length - len) {
                val ch = s[i]
                if (inStr) { if (ch == strChar) inStr = false; continue }
                when (ch) {
                    '"', '\'' -> { inStr = true; strChar = ch }
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    else -> if (depth == 0 && s.regionMatches(i, kw, 0, len)) {
                        val beforeOk = i == 0 || !s[i - 1].isLetterOrDigit() && s[i - 1] != '_'
                        val afterOk = i + len == s.length || !s[i + len].isLetterOrDigit() && s[i + len] != '_'
                        if (beforeOk && afterOk) return i
                    }
                }
            }
            return null
        }

        private fun findTopLevelOperator(s: String, op: String): Int {
            var depth = 0
            var inStr = false
            var strChar = '"'
            for (i in 0..s.length - op.length) {
                val ch = s[i]
                if (inStr) { if (ch == strChar) inStr = false; continue }
                when (ch) {
                    '"', '\'' -> { inStr = true; strChar = ch }
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    else -> if (depth == 0 && s.regionMatches(i, op, 0, op.length)) {
                        if (op == ">" || op == "<") {
                            if (s.getOrNull(i + 1) == '=') continue
                        }
                        return i
                    }
                }
            }
            return -1
        }

        private fun findRightmostTopLevelAddSub(s: String): Int {
            var depth = 0
            var inStr = false
            var strChar = '"'
            var best = -1
            for (i in s.indices) {
                val ch = s[i]
                if (inStr) { if (ch == strChar) inStr = false; continue }
                when (ch) {
                    '"', '\'' -> { inStr = true; strChar = ch }
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    '+', '-' -> if (depth == 0 && i > 0) {
                        val prev = s.substring(0, i).trimEnd().lastOrNull()
                        if (prev != null && prev !in listOf('+', '-', '*', '/', '(', '[', ',', '=', '?', ':', '<', '>')) {
                            best = i
                        }
                    }
                }
            }
            return best
        }

        private fun findRightmostTopLevelMulDiv(s: String): Int {
            var depth = 0
            var inStr = false
            var strChar = '"'
            var best = -1
            for (i in s.indices) {
                val ch = s[i]
                if (inStr) { if (ch == strChar) inStr = false; continue }
                when (ch) {
                    '"', '\'' -> { inStr = true; strChar = ch }
                    '(', '[' -> depth++
                    ')', ']' -> depth--
                    '*', '/' -> if (depth == 0 && i > 0) best = i
                }
            }
            return best
        }

        private fun findMatchingOpenBracket(s: String): Int {
            var depth = 0
            for (i in s.lastIndex downTo 0) {
                when (s[i]) {
                    ']' -> depth++
                    '[' -> {
                        depth--
                        if (depth == 0) return i
                    }
                }
            }
            return -1
        }
    }

    val starterTemplates: List<Pair<String, String>> = listOf(
        "Bollinger Bands (Overlay)" to """
            //@version=6
            indicator("Bollinger Bands", overlay=true)
            len = input.int(20, "Length")
            mult = input.float(2.0, "Multiplier")
            [basis, upper, lower] = ta.bb(close, len, mult)
            plot(basis, title="BB Basis", color=#5EA8D8)
            plot(upper, title="BB Upper", color=#7C90DB)
            plot(lower, title="BB Lower", color=#7C90DB)
        """.trimIndent(),
        "SuperTrend (Overlay)" to """
            //@version=6
            indicator("SuperTrend", overlay=true)
            factor = input.float(3.0, "Factor")
            atrLen = input.int(10, "ATR Length")
            [st, dir] = ta.supertrend(factor, atrLen)
            plot(st, title="SuperTrend", color=#E0B463, linewidth=2)
        """.trimIndent(),
        "RSI 14 (Oscillator)" to """
            //@version=6
            indicator("RSI 14", overlay=false)
            len = input.int(14, "RSI Length")
            r = ta.rsi(close, len)
            plot(r, title="RSI", color=#5EA8D8, linewidth=2)
            hline(70, "Overbought", color=#D96B70)
            hline(30, "Oversold", color=#38B28E)
        """.trimIndent(),
        "MACD (Oscillator)" to """
            //@version=6
            indicator("MACD", overlay=false)
            [macdLine, sigLine, hist] = ta.macd(close, 12, 26, 9)
            plot(hist, title="Histogram", color=#38B28E, style=plot.style_histogram)
            plot(macdLine, title="MACD", color=#5EA8D8, linewidth=2)
            plot(sigLine, title="Signal", color=#E09F5A, linewidth=1)
            hline(0, "Zero", color=#8E9EB3)
        """.trimIndent()
    )
}
