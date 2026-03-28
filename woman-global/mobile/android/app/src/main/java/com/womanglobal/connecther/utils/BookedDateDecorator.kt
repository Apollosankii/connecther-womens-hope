
import android.graphics.Color
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.spans.DotSpan
import java.util.Calendar

class BookedDateDecorator(private val bookedDates: List<Calendar>) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean {
        return bookedDates.any { it.time == day.date }
    }

    override fun decorate(view: DayViewFacade) {
        view.addSpan(DotSpan(8f, Color.RED)) // Adjust size and color as needed
    }
}
