package dev.frydae.watcher;

import java.util.Locale;

/** Matches commands as whole messages, including common server chat wrappers. */
public final class SleepChat {
    public enum Request { NONE, PAUSE, RESUME }
    public static final String PAUSE_REPLY = "Okay bestie! We won't sleep! Say \"pickles yummy yummy\" when we can sleep again";
    public static final String RESUME_REPLY = "Ugh finally! I'm so tired!";

    private SleepChat() {}

    public static Request parse(String message, boolean decorated) {
        String text = message.strip().replace('\u2019', '\'').toLowerCase(Locale.ROOT);
        if (decorated) {
            int separator = Math.max(text.lastIndexOf("> "), text.lastIndexOf(": "));
            if (separator >= 0) text = text.substring(separator + 2).strip();
        }
        return switch (text) {
            case "please don't sleep" -> Request.PAUSE;
            case "pickles yummy yummy" -> Request.RESUME;
            default -> Request.NONE;
        };
    }
}
