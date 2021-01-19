package gp.model;

import gp.Client;

import java.io.Serializable;
import java.util.List;

public class WeatherNotification implements Serializable {
    private final List<Weather> weather;

    public WeatherNotification(List<Weather> weather) {
        this.weather = weather;
    }

    public List<Weather> getWeatherForecast() {
        return weather;
    }
}
