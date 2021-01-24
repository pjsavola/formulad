package gp.ai;

import gp.Main;
import gp.model.*;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public abstract class BaseAI implements AI {

    String playerId;
    int maxHitpoints;
    final TrackData data;
    final List<Node> nodes;
    int gear;
    Tires tires;
    List<Weather> weatherForecast;
    int weatherIndex;
    int totalLaps;

    BaseAI(TrackData data) {
        this.data = data;
        nodes = data.getNodes();
    }

    // This is called if AI takes over of a player after selecting a gear but before selecting where to move.
    public void init(GameState gameState, int gear, Tires tires) {
        selectGear(gameState);
        this.gear = gear;
        this.tires = tires;
    }

    @Override
    public void notify(Object notification) {
        if (notification instanceof CreatedPlayerNotification) {
            final CreatedPlayerNotification createdPlayer = (CreatedPlayerNotification) notification;
            if (createdPlayer.isControlled()) {
                if (playerId != null) {
                    Main.log.log(Level.SEVERE, "AI assigneed to control multiple players");
                }
                playerId = createdPlayer.getPlayerId();
            }
            maxHitpoints = createdPlayer.getHitpoints();
            totalLaps = createdPlayer.getLapsRemaining();
        } else if (notification instanceof WeatherNotification) {
            weatherForecast = ((WeatherNotification) notification).getWeatherForecast();
        } else if (notification instanceof Standings) {
            ++weatherIndex;
        }
    }

    Weather getWeather(int offset) {
        return weatherForecast == null ? null : weatherForecast.get(Math.min(weatherForecast.size() - 1, weatherIndex + offset));
    }

    Tires getBestTires(Tires current, int lapsToGo, boolean free) {
        if (current == null) return null;
        int rainCount = 0;
        for (int i = 1; i < 20; ++i) {
            Weather w = getWeather(i);
            if (w == Weather.RAIN) ++rainCount;
        }
        if (rainCount > 9) {
            if (current.getType() == Tires.Type.WET) return current;
            else return new Tires(Tires.Type.WET);
        }
        if (lapsToGo <= 1) {
            if (free) {
                if (current.getType() == Tires.Type.SOFT && current.getAge() == 0) return current;
                return new Tires(Tires.Type.SOFT);
            } else {
                if (current.getType() != Tires.Type.HARD) return new Tires(Tires.Type.SOFT);
                return current;
            }
        }
        if (current.getType() == Tires.Type.WET) {
            if (Main.random.nextInt(2) == 0) return new Tires(Tires.Type.SOFT);
            else return new Tires(Tires.Type.HARD);
        }
        if (current.getType() == Tires.Type.SOFT && current.getAge() > 0) {
            if (Main.random.nextInt(2) == 0) return new Tires(Tires.Type.SOFT);
            else return new Tires(Tires.Type.HARD);
        }
        if (free) {
            if (Main.random.nextInt(2) == 0) return new Tires(Tires.Type.SOFT);
            else return new Tires(Tires.Type.HARD);
        }
        return current;
    }
}
