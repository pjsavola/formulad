package gp.ai;

public enum Gear {

    ONE { @Override public int[] getDistribution() { return g1; }},
    TWO { @Override public int[] getDistribution() { return g2; }},
    THREE { @Override public int[] getDistribution() { return g3; }},
    FOUR { @Override public int[] getDistribution() { return g4; }},
    FIVE { @Override public int[] getDistribution() { return g5; }},
    SIX { @Override public int[] getDistribution() { return g6; }};

    public abstract int[] getDistribution();

    private static final int[] g1 = new int[] {1, 2};
    private static final int[] g2 = new int[] {2, 3, 3, 4, 4, 4};
    private static final int[] g3 = new int[] {4, 5, 6, 6, 7, 7, 8, 8};
    private static final int[] g4 = new int[] {7, 8, 9, 10, 11, 12};
    private static final int[] g5 = new int[] {11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    private static final int[] g6 = new int[] {21, 22, 23, 24, 25, 26, 27, 28, 29, 30};

    public static int[] getDistribution(int gear) {
        return Gear.values()[gear - 1].getDistribution();
    }

    public static int getMax(int gear) {
        final int[] distribution = getDistribution(gear);
        return distribution[distribution.length - 1];
    }

    public static int getMin(int gear) {
        final int[] distribution = getDistribution(gear);
        return distribution[0];
    }

    public static int getAvg(int gear) {
        final int[] distribution = getDistribution(gear);
        return distribution[distribution.length / 2];
    }
}
