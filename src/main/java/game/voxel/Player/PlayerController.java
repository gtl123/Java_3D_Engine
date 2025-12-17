package game.voxel.Player;

import engine.graph.Camera;
import engine.graph.GameItem;
import engine.graph.Mesh;
import engine.physics.AABB;
import engine.utils.CubeMeshBuilder;
import engine.voxel.ChunkManager;
import org.joml.Vector3f;

import java.util.List;

public class PlayerController {

    private final Camera camera;

    // Player parts
    private final GameItem body;
    private final GameItem leftArm;
    private final GameItem rightArm;
    private final GameItem leftLeg;
    private final GameItem rightLeg;

    private float armSwingTime = 0f;

    // Physics state
    private final Vector3f velocity = new Vector3f();
    private final Vector3f accumulatedForces = new Vector3f();

    // Rigid body properties
    private float mass = 80.0f;          // kg (approx human mass)
    private static final float MOVE_FORCE = 2000.0f;   // N
    private static final float JUMP_FORCE = 2000.0f;   // N
    private static final float GRAVITY = -9.81f;      // m/s^2
    private static final float DAMPING = 5f;          // friction/drag

    private boolean onGround = false;

    private float health = 100f;
    private float stamina = 100f;

    public float getHealth() { return health; }
    public float getStamina() { return stamina; }

    public void applyDamage(float amount) {
        health = Math.max(0, health - amount);
    }

    public void consumeStamina(float amount) {
        stamina = Math.max(0, stamina - amount);
    }

    public void recoverStamina(float amount) {
        stamina = Math.min(100, stamina + amount);
    }

    private float hunger = 100f;
    private float hydration = 100f;

    public float getHunger() { return hunger; }
    public void setHunger(float h) { hunger = Math.max(0, Math.min(100, h)); }

    public float getHydration() { return hydration; }
    public void setHydration(float w) { hydration = Math.max(0, Math.min(100, w)); }

    public PlayerController(Camera camera) {
        this.camera = camera;

        Mesh bodyMesh = CubeMeshBuilder.createCube(0.8f, 1.2f, 0.4f);
        Mesh armMesh  = CubeMeshBuilder.createCube(0.3f, 0.9f, 0.3f);
        Mesh legMesh  = CubeMeshBuilder.createCube(0.3f, 0.9f, 0.3f);

        body     = new GameItem(bodyMesh);
        leftArm  = new GameItem(armMesh);
        rightArm = new GameItem(armMesh);
        leftLeg  = new GameItem(legMesh);
        rightLeg = new GameItem(legMesh);

        // Attach limbs as children with local offsets
        body.addChild(leftArm,  new Vector3f(-0.65f, 0.3f, 0f));
        body.addChild(rightArm, new Vector3f( 0.65f, 0.3f, 0f));
        body.addChild(leftLeg,  new Vector3f(-0.25f,-1.2f, 0f));
        body.addChild(rightLeg, new Vector3f( 0.25f,-1.2f, 0f));
    }

    private void applyForce(Vector3f force) {
        accumulatedForces.add(force);
    }

    public void update(float interval,
                       ChunkManager chunkManager,
                       boolean jump, boolean fwd, boolean back,
                       boolean left, boolean right,
                       boolean moveUp, boolean moveDown) {

        // Reset forces
        accumulatedForces.zero();

        // Gravity
        applyForce(new Vector3f(0, mass * GRAVITY, 0));

        // --- Movement input ---
        float yawRad = (float) Math.toRadians(camera.getRotation().y);
        Vector3f forward = new Vector3f((float)Math.sin(yawRad), 0, (float)-Math.cos(yawRad));
        Vector3f strafe  = new Vector3f((float)Math.sin(yawRad - Math.PI/2), 0, (float)-Math.cos(yawRad - Math.PI/2));

        Vector3f moveDir = new Vector3f();
        if (fwd)  moveDir.add(forward);
        if (back) moveDir.add(new Vector3f(forward).negate());
        if (left) moveDir.add(strafe);
        if (right)moveDir.add(new Vector3f(strafe).negate());

        if (moveDir.lengthSquared() > 0) {
            moveDir.normalize().mul(MOVE_FORCE);
            applyForce(moveDir);
        }

        if (moveUp)   applyForce(new Vector3f(0, MOVE_FORCE, 0));
        if (moveDown) applyForce(new Vector3f(0, -MOVE_FORCE, 0));

        // --- Jump ---
        if (jump && onGround) {
            applyForce(new Vector3f(0, JUMP_FORCE, 0));
            onGround = false;
        }

        // --- Physics integration ---
        Vector3f acceleration = new Vector3f(accumulatedForces).div(mass);
        velocity.add(acceleration.mul(interval));
        velocity.mul(1.0f / (1.0f + DAMPING * interval));

        Vector3f nextPos = new Vector3f(camera.getPosition());

        // --- Axis-by-axis collision resolution with push-out ---
        nextPos.x += velocity.x * interval;
        if (isColliding(nextPos, chunkManager)) {
            nextPos.x = camera.getPosition().x;
            velocity.x = 0;
        }

        nextPos.z += velocity.z * interval;
        if (isColliding(nextPos, chunkManager)) {
            nextPos.z = camera.getPosition().z;
            velocity.z = 0;
        }

        nextPos.y += velocity.y * interval;
        if (isColliding(nextPos, chunkManager)) {
            if (velocity.y < 0) onGround = true;
            velocity.y = 0;
            while (isColliding(nextPos, chunkManager)) {
                nextPos.y += 0.01f;
            }
        } else {
            onGround = false;
        }

        // Commit position
        camera.setPosition(nextPos.x, nextPos.y, nextPos.z);

        // --- Animate arms/legs ---
        boolean movingHoriz = fwd || back || left || right;
        float speedFactor = movingHoriz ? 1.0f : 0.0f;
        armSwingTime += interval * 6.0f * speedFactor;

        float swingAmplitude = movingHoriz ? 30.0f : 0.0f;
        float swing = (float) Math.sin(armSwingTime) * swingAmplitude;

        leftArm.setLocalRotation(swing, 0, 0);
        rightArm.setLocalRotation(-swing, 0, 0);
        leftLeg.setLocalRotation(-swing, 0, 0);
        rightLeg.setLocalRotation(swing, 0, 0);

        // --- Body position only ---
        float bodyHeight = 1.8f;
        body.setLocalPosition(nextPos.x, nextPos.y - bodyHeight * 0.5f, nextPos.z);
    }

    // Accessors
    public Camera getCamera() { return camera; }
    public List<GameItem> getAllParts() {
        return List.of(body, leftArm, rightArm, leftLeg, rightLeg);
    }

    private boolean isColliding(Vector3f pos, ChunkManager chunkManager) {
        float halfWidth = 0.3f;
        float height = 1.8f;

        int minX = (int)Math.floor(pos.x - halfWidth);
        int maxX = (int)Math.floor(pos.x + halfWidth);
        int minY = (int)Math.floor(pos.y);
        int maxY = (int)Math.floor(pos.y + height);
        int minZ = (int)Math.floor(pos.z - halfWidth);
        int maxZ = (int)Math.floor(pos.z + halfWidth);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (chunkManager.isSolidBlock(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private AABB getBoundingBox(Vector3f pos) {
        float halfWidth = 0.3f;
        float height = 1.8f;
        return new AABB(
                pos.x - halfWidth, pos.y, pos.z - halfWidth,
                halfWidth * 2, height, halfWidth * 2
        );
    }
}
