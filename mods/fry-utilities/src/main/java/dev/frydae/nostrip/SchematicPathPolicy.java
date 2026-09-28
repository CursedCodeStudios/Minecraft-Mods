package dev.frydae.nostrip;

/** Decides whether a shovel path conflicts with a Litematica placement. */
final class SchematicPathPolicy {
    enum Expectation { NOT_APPLICABLE, EXPECTS_PATH, EXPECTS_OTHER }

    private SchematicPathPolicy() { }

    static boolean shouldBlock(Expectation expectation) {
        return expectation == Expectation.EXPECTS_OTHER;
    }
}
