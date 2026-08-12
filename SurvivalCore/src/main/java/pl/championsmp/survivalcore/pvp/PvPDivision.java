package pl.championsmp.survivalcore.pvp;

public enum PvPDivision {
    NORMAL(0, "§7NORMAL"),
    IRON(400, "§f§lIRON"),
    GOLD(1000, "§6§lGOLD"),
    DIAMOND(1800, "§b§lDIAMOND"),
    EMERALD(2800, "§a§lEMERALD");

    private final int requiredPoints;
    private final String displayName;

    PvPDivision(int requiredPoints, String displayName) {
        this.requiredPoints = requiredPoints;
        this.displayName = displayName;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static PvPDivision getByPoints(int points) {
        PvPDivision current = NORMAL;
        for (PvPDivision division : values()) {
            if (points >= division.requiredPoints) {
                current = division;
            }
        }
        return current;
    }
}
