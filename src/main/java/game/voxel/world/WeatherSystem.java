package game.voxel.world;

import java.util.Random;

/**
 * Manages dynamic weather states and transitions.
 */
public class WeatherSystem {
    public enum WeatherType {
        CLEAR(0.0005f, 0.0f),
        RAIN(0.015f, 0.8f),
        SNOW(0.02f, 1.0f),
        STORM(0.03f, 0.6f);

        public final float fogDensity;
        public final float skyDarkness;

        WeatherType(float fogDensity, float skyDarkness) {
            this.fogDensity = fogDensity;
            this.skyDarkness = skyDarkness;
        }
    }

    private WeatherType currentWeather = WeatherType.CLEAR;
    private float weatherIntensity = 0.0f; // 0.0 to 1.0 transition
    private float weatherTimer = 0.0f;
    private final Random random = new Random();

    // Interpolated values for rendering
    private float currentFogDensity = 0.0005f;
    private float currentSkyDarkness = 0.0f;

    public void cycleWeather() {
        int next = (currentWeather.ordinal() + 1) % WeatherType.values().length;
        this.currentWeather = WeatherType.values()[next];
        this.weatherIntensity = 1.0f; // Full effect for showcase
        System.out.println("Manual weather switch: " + currentWeather);
    }

    public void update(float deltaTime) {
        weatherTimer -= deltaTime;
        if (weatherTimer <= 0) {
            transitionToRandomWeather();
        }

        // Smoothly interpolate towards target weather parameters
        float lerpSpeed = 0.1f * deltaTime;
        currentFogDensity += (currentWeather.fogDensity * weatherIntensity - currentFogDensity) * lerpSpeed;
        currentSkyDarkness += (currentWeather.skyDarkness * weatherIntensity - currentSkyDarkness) * lerpSpeed;
    }

    private void transitionToRandomWeather() {
        int r = random.nextInt(100);
        if (r < 70) {
            currentWeather = WeatherType.CLEAR;
            weatherTimer = 600 + random.nextInt(1200); // 10-20 mins
            weatherIntensity = 0.0f;
        } else if (r < 85) {
            currentWeather = WeatherType.RAIN;
            weatherTimer = 300 + random.nextInt(600); // 5-10 mins
            weatherIntensity = 0.5f + random.nextFloat() * 0.5f;
        } else if (r < 95) {
            currentWeather = WeatherType.SNOW;
            weatherTimer = 300 + random.nextInt(600);
            weatherIntensity = 0.5f + random.nextFloat() * 0.5f;
        } else {
            currentWeather = WeatherType.STORM;
            weatherTimer = 120 + random.nextInt(300); // Short storms
            weatherIntensity = 1.0f;
        }
        System.out.println("Weather changed to: " + currentWeather + " (Intensity: " + weatherIntensity + ")");
    }

    public WeatherType getCurrentWeather() {
        return currentWeather;
    }

    public float getFogDensity() {
        return currentFogDensity;
    }

    public float getSkyDarkness() {
        return currentSkyDarkness;
    }
}
