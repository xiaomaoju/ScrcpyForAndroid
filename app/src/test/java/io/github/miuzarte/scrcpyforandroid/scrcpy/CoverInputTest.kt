package io.github.miuzarte.scrcpyforandroid.scrcpy

import org.junit.Assert.*
import org.junit.Test

class CoverInputTest {
    private val events = mutableListOf<CoverInput>()
    private fun controller() = CoverInputController({ events.addAll(it) }, {})

    @Test fun touchKeepsPointerIdsAndReleasesEveryFingerOnCancel() {
        val input = controller()
        input.touchDown(4, CoverPoint(.2f, .3f))
        input.touchDown(9, CoverPoint(.8f, .7f))
        input.touchMove(mapOf(4 to CoverPoint(-.5f, 1.5f), 9 to CoverPoint(.5f, .5f)))
        input.cancel()
        input.touchMove(mapOf(4 to CoverPoint(.8f, .8f)))
        val touches = events.filterIsInstance<CoverInput.Touch>()
        assertEquals(listOf(0, 0, 2, 2, 1, 1), touches.map { it.action })
        assertEquals(listOf(4L, 9L, 4L, 9L, 4L, 9L), touches.map { it.id })
        assertEquals(CoverPoint(0f, 1f), touches[2].point)
    }

    @Test fun touchStartingInLetterboxCannotTurnIntoAPressWhenItMovesInside() {
        val input = controller()
        input.touchDown(1, CoverPoint(-.01f, .5f))
        input.touchMove(mapOf(1 to CoverPoint(.5f, .5f)))
        input.touchUp(1, CoverPoint(.5f, .5f))
        assertTrue(events.isEmpty())
    }

    @Test fun trackpadMovesRelativelyAndTapClicksTheRetainedCursor() {
        val input = controller()
        input.padDown(CoverPoint(.1f, .1f))
        input.padMove(CoverPoint(.3f, .4f), 1, .02f)
        input.padUp()
        val retained = input.cursor
        assertEquals(.7f, retained.x, .0001f)
        assertEquals(.8f, retained.y, .0001f)
        input.padDown(CoverPoint(.9f, .1f))
        input.padUp()
        val click = events.takeLast(2).map { it as CoverInput.Touch }
        assertEquals(listOf(0, 1), click.map { it.action })
        assertTrue(click.all { it.mouse && it.id == -1L && it.point == retained })
    }

    @Test fun twoFingerScrollResumesSingleFingerMovementWithoutJumpingOrClicking() {
        val input = controller()
        input.padDown(CoverPoint(.2f, .2f))
        input.padPointersChanged(CoverPoint(.4f, .4f))
        input.padMove(CoverPoint(.4f, .6f), 2, .02f)
        input.padPointersChanged(CoverPoint(.9f, .2f))
        assertEquals(CoverPoint(.5f, .5f), input.cursor)
        input.padMove(CoverPoint(.95f, .3f), 1, .02f)
        input.padUp()
        assertEquals(.55f, input.cursor.x, .0001f)
        assertEquals(.6f, input.cursor.y, .0001f)
        assertEquals(2, events.size)
        assertEquals(-1f, (events.first() as CoverInput.Scroll).vertical, .0001f)
        assertEquals(listOf(CoverInputController.HOVER), events.filterIsInstance<CoverInput.Touch>().map { it.action })
    }

    @Test fun briefSecondContactCannotLatchTheCursorUntilTheNextTap() {
        val input = controller()
        input.padDown(CoverPoint(.2f, .2f))
        input.padMove(CoverPoint(.25f, .25f), 1, .02f)
        val before = input.cursor
        input.padPointersChanged(CoverPoint(.6f, .4f))
        input.padPointersChanged(CoverPoint(.25f, .25f))
        assertEquals(before, input.cursor)
        input.padMove(CoverPoint(.35f, .3f), 1, .02f)
        input.padLongPress()
        input.padUp()
        assertEquals(.65f, input.cursor.x, .0001f)
        assertEquals(.6f, input.cursor.y, .0001f)
        assertEquals(listOf(7, 7), events.filterIsInstance<CoverInput.Touch>().map { it.action })
    }

    @Test fun interruptedDragResumesHoverWithoutPressingAgain() {
        val input = controller()
        input.padDown(CoverPoint(.2f, .2f))
        input.padLongPress()
        input.padPointersChanged(CoverPoint(.5f, .5f))
        input.padPointersChanged(CoverPoint(.2f, .2f))
        input.padMove(CoverPoint(.3f, .3f), 1, .02f)
        input.padUp()
        assertEquals(listOf(0, 1, 7), events.filterIsInstance<CoverInput.Touch>().map { it.action })
        assertEquals(.6f, input.cursor.x, .0001f)
    }

    @Test fun cancelledGestureStillRequiresAFreshDown() {
        val input = controller()
        input.padDown(CoverPoint(.2f, .2f))
        input.padPointersChanged(CoverPoint(.5f, .5f))
        input.cancel()
        input.padPointersChanged(CoverPoint(.2f, .2f))
        input.padMove(CoverPoint(.3f, .3f), 1, .02f)
        input.padUp()
        assertEquals(CoverPoint(.5f, .5f), input.cursor)
        assertTrue(events.isEmpty())
    }

    @Test fun longPressDragEndsWithReleaseOnModeChange() {
        val input = controller()
        input.padDown(CoverPoint(.2f, .2f))
        input.padLongPress()
        input.padMove(CoverPoint(.3f, .4f), 1, .02f)
        input.cancel()
        input.padUp()
        assertEquals(listOf(0, 2, 1), events.filterIsInstance<CoverInput.Touch>().map { it.action })
    }

    @Test fun addingSecondFingerReleasesAnActiveMouseDrag() {
        val input = controller()
        input.padDown(CoverPoint(.2f, .2f))
        input.padLongPress()
        input.padPointersChanged(CoverPoint(.5f, .5f))
        input.padUp()
        assertEquals(listOf(0, 1), events.filterIsInstance<CoverInput.Touch>().map { it.action })
    }

    @Test fun layoutFitsActualVideoAndLeavesRoomForControlsAndKeyboard() {
        for ((width, height) in listOf(1080 to 1920, 1080 to 2640, 2640 to 1080)) {
            for (viewportHeight in listOf(292f, 140f)) {
                val pane = coverVideoPaneWidth(width, height, 306f, viewportHeight, 120f, 4f)
                assertTrue(pane <= 182f)
                val bounds = fitVideoContent(width, height, pane.toInt(), viewportHeight.toInt())
                assertTrue(bounds.width <= pane && bounds.height <= viewportHeight)
                assertEquals(width.toFloat() / height, bounds.width / bounds.height, .0001f)
            }
        }
    }

    @Test fun resizingSessionRetainsItsInputIdentity() {
        val original = Scrcpy.Session.SessionInfo("test", 0, null, 1080, 1920, controlEnabled = true, controlSessionId = 32u)
        assertEquals(32u, original.copy(width = 1920, height = 1080).controlSessionId)
    }
}
