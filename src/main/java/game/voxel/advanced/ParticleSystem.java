package game.voxel.advanced;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * ParticleSystem: Advanced particle effects for the engine
 * Supports various particle types: dust, smoke, fire, water splashes, etc.
 */
public class ParticleSystem {
    
    private List<Particle> particles;
    private static final int MAX_PARTICLES = 10000;
    
    public ParticleSystem() {
        particles = new ArrayList<>();
    }
    
    /**
     * Particle class
     */
    public static class Particle {
        public Vector3f position;
        public Vector3f velocity;
        public Vector3f acceleration;
        public float lifetime;
        public float maxLifetime;
        public Vector3f color;
        public float size;
        public ParticleType type;
        
        public Particle(Vector3f position, Vector3f velocity, float lifetime, Vector3f color, float size, ParticleType type) {
            this.position = new Vector3f(position);
            this.velocity = new Vector3f(velocity);
            this.acceleration = new Vector3f(0, -9.81f, 0);  // Gravity
            this.lifetime = lifetime;
            this.maxLifetime = lifetime;
            this.color = new Vector3f(color);
            this.size = size;
            this.type = type;
        }
        
        public void update(float deltaTime) {
            // Update velocity with acceleration
            velocity.add(acceleration.x * deltaTime, acceleration.y * deltaTime, acceleration.z * deltaTime);
            
            // Update position
            position.add(velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime);
            
            // Update lifetime
            lifetime -= deltaTime;
            
            // Apply drag
            velocity.mul(0.98f);
            
            // Fade out effect
            float alpha = lifetime / maxLifetime;
            color.mul(alpha);
        }
        
        public boolean isAlive() {
            return lifetime > 0;
        }
    }
    
    public enum ParticleType {
        DUST,
        SMOKE,
        FIRE,
        WATER,
        BLOOD,
        SPARK,
        LEAF
    }
    
    /**
     * Emit particles at a location
     */
    public void emit(Vector3f position, ParticleType type, int count) {
        if (particles.size() >= MAX_PARTICLES) {
            return;
        }
        
        for (int i = 0; i < count; i++) {
            Vector3f velocity = generateRandomVelocity(type);
            float lifetime = generateLifetime(type);
            Vector3f color = getColorForType(type);
            float size = getDefaultSize(type);
            
            particles.add(new Particle(position, velocity, lifetime, color, size, type));
        }
    }
    
    /**
     * Generate random velocity based on particle type
     */
    private Vector3f generateRandomVelocity(ParticleType type) {
        float x = (float) (Math.random() - 0.5f) * 2.0f;
        float y = (float) Math.random();
        float z = (float) (Math.random() - 0.5f) * 2.0f;
        
        switch (type) {
            case DUST:
                return new Vector3f(x * 2.0f, y * 1.0f, z * 2.0f);
            case SMOKE:
                return new Vector3f(x * 0.5f, y * 3.0f, z * 0.5f);
            case FIRE:
                return new Vector3f(x * 1.0f, y * 4.0f, z * 1.0f);
            case WATER:
                return new Vector3f(x * 3.0f, y * 5.0f, z * 3.0f);
            case BLOOD:
                return new Vector3f(x * 4.0f, y * 6.0f, z * 4.0f);
            case SPARK:
                return new Vector3f(x * 5.0f, y * 8.0f, z * 5.0f);
            case LEAF:
                return new Vector3f(x * 1.5f, y * 0.5f, z * 1.5f);
            default:
                return new Vector3f(x, y, z);
        }
    }
    
    /**
     * Generate lifetime based on particle type
     */
    private float generateLifetime(ParticleType type) {
        switch (type) {
            case DUST:
                return 2.0f + (float) Math.random() * 2.0f;
            case SMOKE:
                return 3.0f + (float) Math.random() * 2.0f;
            case FIRE:
                return 1.0f + (float) Math.random() * 1.0f;
            case WATER:
                return 2.0f + (float) Math.random() * 1.0f;
            case BLOOD:
                return 3.0f + (float) Math.random() * 2.0f;
            case SPARK:
                return 0.5f + (float) Math.random() * 0.5f;
            case LEAF:
                return 5.0f + (float) Math.random() * 3.0f;
            default:
                return 2.0f;
        }
    }
    
    /**
     * Get color for particle type
     */
    private Vector3f getColorForType(ParticleType type) {
        switch (type) {
            case DUST:
                return new Vector3f(0.8f, 0.7f, 0.6f);
            case SMOKE:
                return new Vector3f(0.5f, 0.5f, 0.5f);
            case FIRE:
                return new Vector3f(1.0f, 0.5f, 0.0f);
            case WATER:
                return new Vector3f(0.2f, 0.6f, 1.0f);
            case BLOOD:
                return new Vector3f(0.8f, 0.0f, 0.0f);
            case SPARK:
                return new Vector3f(1.0f, 1.0f, 0.0f);
            case LEAF:
                return new Vector3f(0.2f, 0.8f, 0.2f);
            default:
                return new Vector3f(1.0f, 1.0f, 1.0f);
        }
    }
    
    /**
     * Get default size for particle type
     */
    private float getDefaultSize(ParticleType type) {
        switch (type) {
            case DUST:
                return 0.1f;
            case SMOKE:
                return 0.3f;
            case FIRE:
                return 0.2f;
            case WATER:
                return 0.05f;
            case BLOOD:
                return 0.08f;
            case SPARK:
                return 0.03f;
            case LEAF:
                return 0.15f;
            default:
                return 0.1f;
        }
    }
    
    /**
     * Update all particles
     */
    public void update(float deltaTime) {
        particles.removeIf(p -> !p.isAlive());
        
        for (Particle p : particles) {
            p.update(deltaTime);
        }
    }
    
    /**
     * Get all active particles
     */
    public List<Particle> getParticles() {
        return particles;
    }
    
    /**
     * Clear all particles
     */
    public void clear() {
        particles.clear();
    }
}
