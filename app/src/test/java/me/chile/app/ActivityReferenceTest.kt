package me.chile.app
import me.chile.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
class ActivityReferenceTest {
    private val profile=Profile("male",30,175.0,70.0,activity="sedentary")
    @Test fun timeReferenceStartsAtZeroAndDoesNotBecomeRecordedExercise() {
        val full=profile.totalEnergy()!!-profile.resting()!!
        assertEquals(0,dailyActivityReference(profile,LocalTime.MIDNIGHT))
        assertEquals(kotlin.math.round(full/2.0).toInt(),dailyActivityReference(profile,LocalTime.NOON))
        assertEquals(full,dailyActivityReference(profile,LocalTime.MAX))
        assertNull(dailyActivityReference(profile.copy(activity=null),LocalTime.NOON))
        assertNull(dailyActivityReference(null,LocalTime.NOON))
    }
}
