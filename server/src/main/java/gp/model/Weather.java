package gp.model;

import gp.Main;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum Weather {
    DRY, RAIN;

    public static class Params {
        public int rainProbability;
        public int shortestPeriod;
    }

    public static List<Weather> forecast(Params params, int turns) {
        int i = 0;
        int sum = 0;
        int randomMotion[] = new int[turns];
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        while (i < turns) {
            final int next = Main.random.nextInt(3) - 1;
            sum += next;
            randomMotion[i++] = sum;
            min = Math.min(min, sum);
            max = Math.max(max, sum);
        }
        // rainProbability 100 -> max
        // rainProbability 0 -> min
        // rainProbability 50 -> (min + max) / 2
        final int pr = Math.min(100, Math.max(0, params.rainProbability));
        final int rainThreshold = (max - min) * params.rainProbability / 100 + min + (pr == 100 ? 1 : 0);
        final List<Weather> forecast = Arrays.stream(randomMotion).mapToObj(r -> r < rainThreshold ? RAIN : DRY).collect(Collectors.toList());
        int period = 1;
        Weather weather = forecast.get(0);
        for (i = 1; i < forecast.size(); ++i) {
            Weather previous = weather;
            weather = forecast.get(i);
            if (previous == weather) {
                ++period;
            } else if (period < params.shortestPeriod) {
                for (int j = i - 1; j >= i - period; --j) {
                    forecast.set(j, weather);
                }
                ++period;
            } else {
                period = 1;
            }
        }

        return forecast;
    }
}
