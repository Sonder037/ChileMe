package me.chile.app

import me.chile.app.ui.*
import org.junit.Assert.*
import org.junit.Test

class BalanceChartTest {
    @Test fun neutralBandIsGreenAndExcessChangesContinuously() {
        for(value in listOf(-300,0,300)) assertEquals(balanceColor(0),balanceColor(value))
        assertNotEquals(balanceColor(0),balanceColor(-900))
        assertNotEquals(balanceColor(0),balanceColor(900))
        assertEquals(balanceColor(-900),balanceColor(-9000))
        assertEquals(balanceColor(900),balanceColor(9000))
        assertNotEquals(balanceColor(null),balanceColor(0))
        val near=balanceColor(301);val green=balanceColor(300)
        assertTrue(kotlin.math.abs(near.red-green.red)<.01f)
    }
    @Test fun curveNeverConnectsAcrossMissingDaysOrOvershoots() {
        val values=listOf(0,900,-600,null,300,300,null)
        val segments=balanceSegments(values)
        assertEquals(listOf(0,1,4),segments.map {it.index})
        for(segment in segments) {
            val a=values[segment.index]!!.toFloat();val b=values[segment.index+1]!!.toFloat()
            for(step in 0..100) {
                val y=segment.valueAt(step/100f)
                assertTrue(y>=minOf(a,b)-.001f && y<=maxOf(a,b)+.001f)
            }
            assertEquals(a,segment.valueAt(0f),0f)
            assertEquals(b,segment.valueAt(1f),0f)
        }
    }
}
