package engine.world;

import org.joml.Vector3f;

public class TimeSystem {

    private float timeOfDay; // 0.0 to 1.0 (0.25 = noon, 0.75 = midnight)
    private float timeSpeed; // How fast time progresses

    public TimeSystem() {
        this.timeOfDay = 0.25f; // Start at noon
        this.timeSpeed = 0.01f; // Default speed
    }

    public void update(float deltaTime) {
        timeOfDay += timeSpeed * deltaTime;
        if (timeOfDay >= 1.0f) {
            timeOfDay -= 1.0f;
        }
    }

    public float getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(float time) {
        this.timeOfDay = time % 1.0f;
    }

    public void setTimeSpeed(float speed) {
        this.timeSpeed = speed;
    }

    /**
     * Get sun direction vector based on time of day
     * Sun moves in an arc across the sky
     */
    public Vector3f getSunDirection() {
        // Convert time to angle (0 = sunrise, 0.5 = sunset)
        float angle = timeOfDay * (float) Math.PI * 2.0f;

        // Sun position calculation
        float x = (float) Math.sin(angle);
        float y = (float) Math.cos(angle);
        float z = 0.0f;

        return new Vector3f(x, y, z).normalize();
    }

    /**
     * Get moon direction vector based on time of day
     * Moon is positioned opposite to the sun
     */
    public Vector3f getMoonDirection() {
        // Moon is opposite to sun (180 degrees offset)
        float angle = (timeOfDay + 0.5f) * (float) Math.PI * 2.0f;

        float x = (float) Math.sin(angle);
        float y = (float) Math.cos(angle);
        float z = 0.0f;

        return new Vector3f(x, y, z).normalize();
    }

    /**
     * Get light color based on time of day
     * Changes from warm sunrise, white noon, warm sunset, blue night
     */
    public Vector3f getLightColor() {
        float t = timeOfDay;

        // Dawn (0.0 - 0.15): Orange/Pink
        if (t < 0.15f) {
            float factor = t / 0.15f;
            return lerp(new Vector3f(0.3f, 0.3f, 0.5f), new Vector3f(1.0f, 0.7f, 0.4f), factor);
        }
        // Day (0.15 - 0.35): Warm to White
        else if (t < 0.35f) {
            float factor = (t - 0.15f) / 0.2f;
            return lerp(new Vector3f(1.0f, 0.7f, 0.4f), new Vector3f(1.0f, 1.0f, 1.0f), factor);
        }
        // Noon (0.35 - 0.5): Bright white
        else if (t < 0.5f) {
            return new Vector3f(1.0f, 1.0f, 1.0f);
        }
        // Afternoon (0.5 - 0.65): White to warm
        else if (t < 0.65f) {
            float factor = (t - 0.5f) / 0.15f;
            return lerp(new Vector3f(1.0f, 1.0f, 1.0f), new Vector3f(1.0f, 0.7f, 0.4f), factor);
        }
        // Dusk (0.65 - 0.85): Orange to purple
        else if (t < 0.85f) {
            float factor = (t - 0.65f) / 0.2f;
            return lerp(new Vector3f(1.0f, 0.7f, 0.4f), new Vector3f(0.4f, 0.3f, 0.6f), factor);
        }
        // Night (0.85 - 1.0): Dark blue
        else {
            float factor = (t - 0.85f) / 0.15f;
            return lerp(new Vector3f(0.4f, 0.3f, 0.6f), new Vector3f(0.3f, 0.3f, 0.5f), factor);
        }
    }

    /**
     * Get ambient light strength based on time of day
     * Higher during day, lower at night
     */
    public float getAmbientStrength() {
        float t = timeOfDay;

        // Dawn (0.0 - 0.25): 0.3 to 0.6
        if (t < 0.25f) {
            return 0.3f + (t / 0.25f) * 0.3f;
        }
        // Day (0.25 - 0.5): 0.6 (bright)
        else if (t < 0.5f) {
            return 0.6f;
        }
        // Dusk (0.5 - 0.75): 0.6 to 0.3
        else if (t < 0.75f) {
            return 0.6f - ((t - 0.5f) / 0.25f) * 0.3f;
        }
        // Night (0.75 - 1.0): 0.3 (dim)
        else {
            return 0.3f;
        }
    }

    private Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t);
    }
}
