# Seed Scout

Seed Scout is a client-only Fabric mod for Minecraft Java 26.2 that recovers a world's full seed from modern Nether bedrock generation.

Run `/seedscout crack` while standing in the Nether. The mod reads bedrock at Y=4 and Y=123 in the current chunk and its four cardinal neighbors, runs the cracker on a background thread, and prints the recovered signed world seed as clickable chat text. An optional thread count can be supplied, for example `/seedscout crack 8`. `/seedscout` shows whether a crack is running.

All five chunks must be loaded. Use newly generated, vanilla terrain when possible. Worlds generated before Java 1.18, customized Nether generation, old affected Paper servers, or edited bedrock can produce no result. The mod does not contact a server or external service; cracking runs locally.

The JAR embeds Nether Bedrock Cracker 1.5.0 and its native libraries under the LGPL-3.0-or-later license. See `THIRD_PARTY_NOTICES.md` for source and attribution.
