package dev.frydae.monument;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MonumentSurveyTest {
    private static final class Reader implements MonumentSurvey.Reader {
        boolean loaded = true;
        int reads;
        final Set<String> sponges = new HashSet<>();
        @Override public boolean allChunksLoaded(MonumentBounds b) { return loaded; }
        @Override public boolean isSponge(int x, int y, int z) {
            reads++;
            return sponges.contains(x + "," + y + "," + z);
        }
    }
    @Test void completeVolumeIncludesCornersAndEveryLayer() {
        var bounds = new MonumentBounds(-16, 32);
        var reader = new Reader();
        reader.sponges.add(bounds.minX() + ",39," + bounds.minZ());
        reader.sponges.add(bounds.maxX() + ",63," + bounds.maxZ());
        reader.sponges.add("-16,50,32");
        reader.sponges.add("-16,38,32"); // Outside the structure survey.
        var survey = new MonumentSurvey(bounds);
        while (!survey.complete()) survey.advance(reader, 4096);
        assertEquals(MonumentBounds.VOLUME, reader.reads);
        assertEquals(3, survey.sponges());
    }
    @Test void perTickBudgetIsRespectedAndPartialResultCannotBePublished() {
        var reader = new Reader();
        var survey = new MonumentSurvey(new MonumentBounds(0, 0));
        survey.advance(reader, 100);
        assertEquals(100, reader.reads);
        assertFalse(survey.complete());
        assertThrows(IllegalStateException.class, survey::sponges);
    }
    @Test void missingChunkNeverMeansZeroSponges() {
        var reader = new Reader(); reader.loaded = false;
        var survey = new MonumentSurvey(new MonumentBounds(0, 0));
        survey.advance(reader, MonumentBounds.VOLUME);
        assertTrue(survey.invalid());
        assertEquals(0, reader.reads);
        assertThrows(IllegalStateException.class, survey::sponges);
    }
    @Test void chunkUnloadDuringSurveyInvalidatesIt() {
        var reader = new Reader();
        var survey = new MonumentSurvey(new MonumentBounds(0, 0));
        survey.advance(reader, 4096);
        reader.loaded = false;
        survey.advance(reader, 4096);
        reader.loaded = true;
        survey.advance(reader, MonumentBounds.VOLUME);
        assertFalse(survey.complete());
        assertTrue(survey.invalid());
        assertEquals(4096, reader.reads);
    }
    @Test void emptyFullyLoadedMonumentReturnsZero() {
        var survey = new MonumentSurvey(new MonumentBounds(0, 0));
        survey.advance(new Reader(), MonumentBounds.VOLUME);
        assertEquals(0, survey.sponges());
    }
    @Test void unloadOnFinalBatchCannotPublishADecreasedCount() {
        var survey = new MonumentSurvey(new MonumentBounds(0, 0));
        var reader = new MonumentSurvey.Reader() {
            boolean loaded = true;
            public boolean allChunksLoaded(MonumentBounds bounds) { return loaded; }
            public boolean isSponge(int x, int y, int z) { loaded = false; return false; }
        };
        survey.advance(reader, MonumentBounds.VOLUME);
        assertTrue(survey.invalid()); assertFalse(survey.complete());
        assertThrows(IllegalStateException.class, survey::sponges);
    }
}
