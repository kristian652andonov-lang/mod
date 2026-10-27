package com.fantasyweapons.quest;

/** The Quest Journal's chapters, in the order of the road. */
public enum Chapter {
    FIRST_STEPS("First Steps", 0xE8C46A),
    ORES("Ores", 0xD49A4C),
    REFINING("Refining", 0x6FD8F0),
    WEAPONS("Weapons", 0xC98CFF),
    ARMOR("Armor", 0xB8C4D4),
    HUNTS("Hunts", 0xFF6A5A),
    JOURNEYS("Journeys", 0x7DE08A),
    MASTERY("Mastery", 0xFFD970);

    private final String title;
    private final int color;

    Chapter(String title, int color) {
        this.title = title;
        this.color = color;
    }

    /** The chapter's name in the player's language. */
    public String title() {
        return com.fantasyweapons.text.Tr.s(title);
    }

    /** The chapter's colour (RGB). */
    public int color() {
        return color;
    }
}
