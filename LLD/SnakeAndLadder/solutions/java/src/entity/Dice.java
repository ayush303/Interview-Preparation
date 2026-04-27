package entity;

public class Dice {
    private int minValue;
    private int maxValue;

    public Dice(int minValue, int maxValue) {
        if (minValue <= 0 || maxValue <= 0) {
            throw new IllegalArgumentException("Dice values must be positive.");
        }
        if (minValue >= maxValue) {
            throw new IllegalArgumentException("Minimum value must be less than maximum value.");
        }
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public int roll() {
        return (int) (Math.random() * (maxValue - minValue + 1)) + minValue;
    }
}
