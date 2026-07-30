package com.example.chalauncher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import kotlin.math.max
import kotlin.math.min

@Composable
fun TreemapLayout(
    modifier: Modifier = Modifier,
    items: List<AppInfo>,
    content: @Composable (AppInfo) -> Unit
) {
    // If no items, do nothing
    if (items.isEmpty()) {
        Layout(content = {}, modifier = modifier) { _, _ -> layout(0, 0) {} }
        return
    }

    Layout(
        content = {
            items.forEach { content(it) }
        },
        modifier = modifier
    ) { measurables, constraints ->
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        
        if (width == 0f || height == 0f || measurables.isEmpty()) {
            return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        }

        val totalWeight = items.sumOf { it.clickCount }.toFloat()
        val totalArea = width * height
        val scale = if (totalWeight > 0) totalArea / totalWeight else 0f

        val rects = squarify(items.map { it.clickCount * scale }, width, height)
        
        val placeables = mutableListOf<Placeable>()
        val positions = mutableListOf<Pair<Int, Int>>()
        
        measurables.forEachIndexed { index, measurable ->
            if (index < rects.size) {
                val rect = rects[index]
                val itemWidth = max(0, rect.w.toInt())
                val itemHeight = max(0, rect.h.toInt())
                
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(itemWidth, itemHeight)
                )
                placeables.add(placeable)
                positions.add(Pair(rect.x.toInt(), rect.y.toInt()))
            } else {
                placeables.add(measurable.measure(androidx.compose.ui.unit.Constraints.fixed(0, 0)))
                positions.add(Pair(0,0))
            }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val pos = positions[index]
                placeable.placeRelative(x = pos.first, y = pos.second)
            }
        }
    }
}

data class Rect(val x: Float, val y: Float, val w: Float, val h: Float)

fun squarify(areas: List<Float>, width: Float, height: Float): List<Rect> {
    var xOffset = 0f
    var yOffset = 0f
    var w = width
    var h = height
    val result = mutableListOf<Rect>()
    
    var remaining = areas.toMutableList()
    
    while (remaining.isNotEmpty()) {
        var row = mutableListOf<Float>()
        var wShortest = min(w, h)
        var rowIdx = 0
        
        while (rowIdx < remaining.size) {
            val area = remaining[rowIdx]
            if (area == 0f) {
                rowIdx++
                continue
            }
            
            val newRow = row.toMutableList().apply { add(area) }
            if (row.isEmpty() || worst(row, wShortest) >= worst(newRow, wShortest)) {
                row.add(area)
                rowIdx++
            } else {
                break
            }
        }
        
        if (row.isEmpty()) {
            break // all remaining areas are 0
        }
        
        // Layout the row
        val rowArea = row.sum()
        val rowWidth = if (wShortest == h) rowArea / h else w
        val rowHeight = if (wShortest == w) rowArea / w else h
        
        var currentX = xOffset
        var currentY = yOffset
        
        if (wShortest == h) { // Layout vertically along the left edge
            for (area in row) {
                val itemH = area / rowWidth
                result.add(Rect(currentX, currentY, rowWidth, itemH))
                currentY += itemH
            }
            xOffset += rowWidth
            w -= rowWidth
        } else { // Layout horizontally along the top edge
            for (area in row) {
                val itemW = area / rowHeight
                result.add(Rect(currentX, currentY, itemW, rowHeight))
                currentX += itemW
            }
            yOffset += rowHeight
            h -= rowHeight
        }
        
        remaining = remaining.subList(rowIdx, remaining.size)
    }
    
    return result
}

fun worst(row: List<Float>, w: Float): Float {
    if (row.isEmpty()) return Float.MAX_VALUE
    val maxA = row.maxOrNull() ?: 0f
    val minA = row.minOrNull() ?: 0f
    val s = row.sum()
    if (s == 0f || minA == 0f) return Float.MAX_VALUE
    val wSq = w * w
    val sSq = s * s
    return max((wSq * maxA) / sSq, sSq / (wSq * minA))
}
