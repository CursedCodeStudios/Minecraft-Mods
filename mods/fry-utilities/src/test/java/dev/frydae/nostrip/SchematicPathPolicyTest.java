package dev.frydae.nostrip;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchematicPathPolicyTest {
    @Test void blocksOnlySchematicMismatches() {
        assertFalse(SchematicPathPolicy.shouldBlock(SchematicPathPolicy.Expectation.NOT_APPLICABLE));
        assertFalse(SchematicPathPolicy.shouldBlock(SchematicPathPolicy.Expectation.EXPECTS_PATH));
        assertTrue(SchematicPathPolicy.shouldBlock(SchematicPathPolicy.Expectation.EXPECTS_OTHER));
    }
}
