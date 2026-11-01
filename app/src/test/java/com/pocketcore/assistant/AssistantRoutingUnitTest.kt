package com.pocketcore.assistant

import com.pocketcore.assistant.core.AssistantModule
import com.pocketcore.assistant.core.CommandActionType
import com.pocketcore.assistant.core.CommandDispatcher
import com.pocketcore.assistant.core.FeatureRegistry
import com.pocketcore.assistant.modules.reminder.ReminderModule
import com.pocketcore.assistant.modules.reminder.ReminderParser
import com.pocketcore.assistant.modules.utility.AppLauncherModule
import com.pocketcore.assistant.modules.utility.NoteModule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class AssistantRoutingUnitTest {

    @Before
    fun setup() {
        FeatureRegistry.clear()
        FeatureRegistry.registerModule(ReminderModule())
        FeatureRegistry.registerModule(NoteModule())
        FeatureRegistry.registerModule(AppLauncherModule())
    }

    @Test
    fun testModuleCanHandleRouting() {
        val reminderModule = ReminderModule()
        val noteModule = NoteModule()
        val appLauncherModule = AppLauncherModule()

        // Reminder module matching
        assertTrue(reminderModule.canHandle("Remind me to buy groceries at 9pm"))
        assertTrue(reminderModule.canHandle("remind me to call Mom tomorrow"))
        assertTrue(reminderModule.canHandle("what are my reminders"))
        assertTrue(reminderModule.canHandle("check reminders today"))
        assertTrue(reminderModule.canHandle("any reminders at 9pm"))
        assertTrue(reminderModule.canHandle("delete reminder buy groceries"))
        assertTrue(reminderModule.canHandle("remove reminder 1"))
        assertFalse(reminderModule.canHandle("Open camera"))

        // Note module matching
        assertTrue(noteModule.canHandle("Note Buy groceries"))
        assertTrue(noteModule.canHandle("Take note Meeting notes"))
        assertFalse(noteModule.canHandle("Open Settings"))

        // App launcher matching
        assertTrue(appLauncherModule.canHandle("Open Camera"))
        assertTrue(appLauncherModule.canHandle("launch Settings"))
        assertFalse(appLauncherModule.canHandle("Note hello"))
    }

    @Test
    fun testReminderParserAtTime() {
        val fixedNow = LocalDateTime.of(2026, 11, 1, 10, 0)
        val parsed = ReminderParser.parse("Remind me to buy groceries at 9pm", fixedNow)

        assertNotNull(parsed)
        assertEquals("Buy groceries", parsed!!.task)
        assertEquals(21, parsed.triggerDateTime.hour)
        assertEquals(0, parsed.triggerDateTime.minute)
    }

    @Test
    fun testReminderParserRelativeTime() {
        val fixedNow = LocalDateTime.of(2026, 11, 1, 10, 0)
        val parsed = ReminderParser.parse("Remind me to drink water in 30 minutes", fixedNow)

        assertNotNull(parsed)
        assertEquals("Drink water", parsed!!.task)
        assertEquals(10, parsed.triggerDateTime.hour)
        assertEquals(30, parsed.triggerDateTime.minute)

        // Test "after 2 minutes" and "today after 2 minutes"
        val parsedAfter = ReminderParser.parse("remind me to check oven after 2 minutes", fixedNow)
        assertNotNull(parsedAfter)
        assertEquals("Check oven", parsedAfter!!.task)
        assertEquals(10, parsedAfter.triggerDateTime.hour)
        assertEquals(2, parsedAfter.triggerDateTime.minute)
        assertEquals(1, parsedAfter.triggerDateTime.dayOfMonth)

        val parsedTodayAfter = ReminderParser.parse("remind me to take pill at today after 5 mins", fixedNow)
        assertNotNull(parsedTodayAfter)
        assertEquals("Take pill", parsedTodayAfter!!.task)
        assertEquals(10, parsedTodayAfter.triggerDateTime.hour)
        assertEquals(5, parsedTodayAfter.triggerDateTime.minute)
        assertEquals(1, parsedTodayAfter.triggerDateTime.dayOfMonth)
    }

    @Test
    fun testReminderParserContextAwareHourInference() {
        // At 3:00 PM (15:00), saying "at 5" should choose 5:00 PM today (17:00)
        val afternoon = LocalDateTime.of(2026, 11, 1, 15, 0)
        val parsed5 = ReminderParser.parse("remind me to exercise at 5", afternoon)
        assertNotNull(parsed5)
        assertEquals(17, parsed5!!.triggerDateTime.hour)
        assertEquals(1, parsed5.triggerDateTime.dayOfMonth)

        // At 3:00 PM (15:00), saying "at 2" has passed in PM, so it should schedule 2:00 AM upcoming
        val parsed2 = ReminderParser.parse("remind me to check log at 2", afternoon)
        assertNotNull(parsed2)
        assertEquals(2, parsed2!!.triggerDateTime.hour)
    }

    @Test
    fun testReminderParserDateTime() {
        val fixedNow = LocalDateTime.of(2026, 10, 1, 12, 0)
        val parsed = ReminderParser.parse("Remind me to call Mom on Oct 24 at 10am", fixedNow)

        assertNotNull(parsed)
        assertEquals("Call Mom", parsed!!.task)
        assertEquals(10, parsed.triggerDateTime.monthValue)
        assertEquals(24, parsed.triggerDateTime.dayOfMonth)
        assertEquals(10, parsed.triggerDateTime.hour)
        assertEquals(0, parsed.triggerDateTime.minute)
    }

    @Test
    fun testReminderParserTomorrow() {
        val fixedNow = LocalDateTime.of(2026, 11, 1, 10, 0)
        val parsed = ReminderParser.parse("Remind me to submit report tomorrow at 5pm", fixedNow)

        assertNotNull(parsed)
        assertEquals("Submit report", parsed!!.task)
        assertEquals(2, parsed.triggerDateTime.dayOfMonth)
        assertEquals(17, parsed.triggerDateTime.hour)
        assertEquals(0, parsed.triggerDateTime.minute)
    }

    @Test
    fun testDynamicFeatureRegistryToggling() {
        val reminderModule = ReminderModule()
        assertEquals(3, FeatureRegistry.getAllModules().size)

        FeatureRegistry.setModuleEnabled("reminder", false)
        assertFalse(FeatureRegistry.isModuleEnabled("reminder"))
        assertEquals(2, FeatureRegistry.getActiveModules().size)

        FeatureRegistry.setModuleEnabled("reminder", true)
        assertTrue(FeatureRegistry.isModuleEnabled("reminder"))
        assertEquals(3, FeatureRegistry.getActiveModules().size)
    }
}
