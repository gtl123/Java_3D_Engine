package engine.world;

import org.joml.Vector3f;

public class TimeSystem {

    /* =====================
       Time state
       ===================== */

    private float timeOfDay;   // 0.0 → 1.0
    private float timeSpeed;   // progression speed

    private int dayOfYear;     // 0 → 364
    private boolean advanceSeasons = true;

    /* =====================
       Constructor
       ===================== */

    public TimeSystem() {
        this.timeOfDay = 0.25f; // noon
        this.timeSpeed = 0.01f;
        this.dayOfYear = 0;     // start of year
    }

    /* =====================
       Update
       ===================== */

    public void update(float deltaTime) {
        timeOfDay += timeSpeed * deltaTime;

        if (timeOfDay >= 1.0f) {
            timeOfDay -= 1.0f;

            if (advanceSeasons) {
                dayOfYear = (dayOfYear + 1) % 365;
            }
        }
    }

    /* =====================
       Basic getters/setters
       ===================== */

    public float getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDay(float time) {
        this.timeOfDay = ((time % 1.0f) + 1.0f) % 1.0f;
    }

    public void setTimeSpeed(float speed) {
        this.timeSpeed = speed;
    }

    public int getDayOfYear() {
        return dayOfYear;
    }

    public void setDayOfYear(int day) {
        this.dayOfYear = ((day % 365) + 365) % 365;
    }

    public void setAdvanceSeasons(boolean enabled) {
        this.advanceSeasons = enabled;
    }

    /* =====================
       Astronomy
       ===================== */

    /**
     * Solar declination angle (radians)
     *  +23.44° in summer, -23.44° in winter
     */
    public float getSolarDeclination() {
        return 0.409f *
                (float) Math.sin(2.0 * Math.PI * (dayOfYear - 81) / 365.0);
    }

    /**
     * Legacy sun direction (simple arc)
     * Kept for compatibility
     */
    public Vector3f getSunDirection() {
        float angle = timeOfDay * (float) Math.PI * 2.0f;
        return new Vector3f(
                (float) Math.sin(angle),
                (float) Math.cos(angle),
                0.0f
        ).normalize();
    }

    /**
     * Legacy moon direction (opposite sun)
     */
    public Vector3f getMoonDirection() {
        float angle = (timeOfDay + 0.5f) * (float) Math.PI * 2.0f;
        return new Vector3f(
                (float) Math.sin(angle),
                (float) Math.cos(angle),
                0.0f
        ).normalize();
    }

    /* =====================
       Lighting helpers (unchanged)
       ===================== */

    public Vector3f getLightColor() {
        float t = timeOfDay;

        if (t < 0.15f) {
            return lerp(
                    new Vector3f(0.3f, 0.3f, 0.5f),
                    new Vector3f(1.0f, 0.7f, 0.4f),
                    t / 0.15f
            );
        } else if (t < 0.35f) {
            return lerp(
                    new Vector3f(1.0f, 0.7f, 0.4f),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    (t - 0.15f) / 0.2f
            );
        } else if (t < 0.5f) {
            return new Vector3f(1.0f, 1.0f, 1.0f);
        } else if (t < 0.65f) {
            return lerp(
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Vector3f(1.0f, 0.7f, 0.4f),
                    (t - 0.5f) / 0.15f
            );
        } else if (t < 0.85f) {
            return lerp(
                    new Vector3f(1.0f, 0.7f, 0.4f),
                    new Vector3f(0.4f, 0.3f, 0.6f),
                    (t - 0.65f) / 0.2f
            );
        } else {
            return lerp(
                    new Vector3f(0.4f, 0.3f, 0.6f),
                    new Vector3f(0.3f, 0.3f, 0.5f),
                    (t - 0.85f) / 0.15f
            );
        }
    }

    public float getAmbientStrength() {
        float t = timeOfDay;

        if (t < 0.25f) {
            return 0.3f + (t / 0.25f) * 0.3f;
        } else if (t < 0.5f) {
            return 0.6f;
        } else if (t < 0.75f) {
            return 0.6f - ((t - 0.5f) / 0.25f) * 0.3f;
        } else {
            return 0.3f;
        }
    }

    private Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }
}
