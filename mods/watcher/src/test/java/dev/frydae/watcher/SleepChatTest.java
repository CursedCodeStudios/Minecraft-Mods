package dev.frydae.watcher;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SleepChatTest {
    @Test void acceptsBothCommandsAndServerWrappers() {
        assertEquals(SleepChat.Request.PAUSE, SleepChat.parse("Please don't sleep", false));
        assertEquals(SleepChat.Request.PAUSE, SleepChat.parse("  Please DON’T sleep  ", false));
        assertEquals(SleepChat.Request.RESUME, SleepChat.parse("pickles yummy yummy", false));
        assertEquals(SleepChat.Request.PAUSE, SleepChat.parse("<Bestie> Please don't sleep", true));
        assertEquals(SleepChat.Request.RESUME, SleepChat.parse("[Member] Bestie: pickles yummy yummy", true));
    }

    @Test void watcherRepliesCannotTriggerOtherWatchers() {
        for (String message : new String[] { SleepChat.PAUSE_REPLY, SleepChat.RESUME_REPLY,
            "<Alt> " + SleepChat.PAUSE_REPLY, "Alt: " + SleepChat.PAUSE_REPLY }) {
            assertEquals(SleepChat.Request.NONE, SleepChat.parse(message, false));
            assertEquals(SleepChat.Request.NONE, SleepChat.parse(message, true));
        }
        assertEquals(SleepChat.Request.NONE, SleepChat.parse("I'm saying pickles yummy yummy later", false));
        assertEquals(SleepChat.Request.NONE, SleepChat.parse("Please don't sleep tonight", false));
    }
}
