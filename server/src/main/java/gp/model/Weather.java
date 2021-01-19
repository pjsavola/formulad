package gp.model;

import jdk.internal.org.objectweb.asm.commons.RemappingAnnotationAdapter;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public enum Weather {
    DRY, RAIN;

    private final static Random rand = new Random();

    public static List<Weather> forecast(int rainProbability, int volatility, int turns) {
        int i = 0;
        int sum = 0;
        int randomMotion[] = new int[turns];
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        while (i < turns) {
            final int next = rand.nextInt(volatility * 2 + 1) - volatility;
            sum += next;
            randomMotion[i++] = sum;
            min = Math.min(min, sum);
            max = Math.max(max, sum);
        }
        // rainProbability 100 -> max
        // rainProbability 0 -> min
        // rainProbability 50 -> (min + max) / 2
        final int pr = Math.min(100, Math.max(0, rainProbability));
        final int rainThreshold = (max - min) * rainProbability / 100 + min + (pr == 100 ? 1 : 0);
        return Arrays.stream(randomMotion).mapToObj(r -> r < rainThreshold ? RAIN : DRY).collect(Collectors.toList());
    }
}
