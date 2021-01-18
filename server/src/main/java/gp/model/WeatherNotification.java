package gp.model;

import gp.Client;

import java.io.Serializable;

public class WeatherNotification implements Serializable {
    private final Weather weather;

    public WeatherNotification(Weather weather) {
        this.weather = weather;
    }

    public Weather getWeather() {
        return weather;
    }
}
