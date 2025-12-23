package game.voxel.entity;

import engine.camera.Camera;
import engine.entity.Entity;
import engine.raster.Mesh;
import engine.physics.AABB;
import engine.raster.CubeMeshBuilder;
import game.voxel.ChunkManager;
import org.joml.Vector3f;

import java.util.List;

public class PlayerController {

    private final Camera camera;

    private final Entity body;
    private final Entity leftArm;
    private final Entity rightArm;
    private final Entity leftLeg;
    private final Entity rightLeg;
    private final Entity head;

    private float armSwingTime = 0f;
    private float bodyYaw = 0f;

    public enum CameraMode {
        FIRST_PERSON,
        THIRD_PERSON_BACK,
        THIRD_PERSON_FRONT
    }

    private CameraMode cameraMode = CameraMode.FIRST_PERSON;
    private final Vector3f position = new Vector3f();
    private final Vector3f viewRotation = new Vector3f();
    private Mesh headMeshInstance;
    private Mesh bodyMeshInstance;

    // Physics state
    private final Vector3f velocity = new Vector3f();
    private final Vector3f accumulatedForces = new Vector3f();

    // Rigid body properties
    private float mass = 80.0f; // kg (approx human mass)
    // Increased movement force for much faster walking/running.
    // If this feels too fast, tune this value down slightly (e.g. 4000–6000).
    private static final float MOVE_FORCE = 6000.0f; // N
    private static final float JUMP_FORCE = 2000.0f; // N
    private static final float GRAVITY = -9.81f; // m/s^2
    // Slightly reduced damping so high speeds are not immediately cancelled out.
    private static final float DAMPING = 3f; // friction/drag
    private static final float EYE_HEIGHT = 1.6f;

    private boolean onGround = false;

    private float health = 100f;
    private float stamina = 100f;

    public float getHealth() {
        return health;
    }

    public float getStamina() {
        return stamina;
    }

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

    public float getHunger() {
        return hunger;
    }

    public void setHunger(float h) {
        hunger = Math.max(0, Math.min(100, h));
    }

    public float getHydration() {
        return hydration;
    }

    public void setHydration(float w) {
        hydration = Math.max(0, Math.min(100, w));
    }

    public PlayerController(Camera camera) {
        this.camera = camera;

        Mesh bodyMesh = CubeMeshBuilder.createCube(0.6f, 0.8f, 0.4f); // Torso
        Mesh armMesh = CubeMeshBuilder.createCube(0.25f, 0.75f, 0.25f);
        Mesh legMesh = CubeMeshBuilder.createCube(0.25f, 0.8f, 0.25f);
        Mesh headMesh = CubeMeshBuilder.createCube(0.4f, 0.4f, 0.4f);

        body = new Entity(bodyMesh);
        leftArm = new Entity(armMesh);
        rightArm = new Entity(armMesh);
        leftLeg = new Entity(legMesh);
        rightLeg = new Entity(legMesh);
        head = new Entity(headMesh);
        this.headMeshInstance = headMesh;
        this.bodyMeshInstance = bodyMesh;

        // In first person, we hide the head and body to keep them out of view
        head.setMesh(null);
        body.setMesh(null);

        // Attach defaults (3rd person style)
        body.addChild(head, new Vector3f(0f, 0.6f, 0f));
        body.addChild(leftArm, new Vector3f(-0.45f, 0f, 0f));
        body.addChild(rightArm, new Vector3f(0.45f, 0f, 0f));
        body.addChild(leftLeg, new Vector3f(-0.15f, -0.8f, 0f));
        body.addChild(rightLeg, new Vector3f(0.15f, -0.8f, 0f));

        this.bodyYaw = camera.getRotation().y;
        this.viewRotation.set(camera.getRotation());
        this.position.set(camera.getPosition());

        // Now force the initial FIRST_PERSON setup
        this.cameraMode = null;
        setCameraMode(CameraMode.FIRST_PERSON);
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
        float yawRad = (float) Math.toRadians(viewRotation.y);
        Vector3f forward = new Vector3f((float) Math.sin(yawRad), 0, (float) -Math.cos(yawRad));
        Vector3f strafe = new Vector3f((float) Math.sin(yawRad - Math.PI / 2), 0,
                (float) -Math.cos(yawRad - Math.PI / 2));

        Vector3f moveDir = new Vector3f();
        if (fwd)
            moveDir.add(forward);
        if (back)
            moveDir.add(new Vector3f(forward).negate());
        if (left)
            moveDir.add(strafe);
        if (right)
            moveDir.add(new Vector3f(strafe).negate());

        if (moveDir.lengthSquared() > 0) {
            moveDir.normalize().mul(MOVE_FORCE);
            applyForce(moveDir);
        }

        if (moveUp)
            applyForce(new Vector3f(0, MOVE_FORCE, 0));
        if (moveDown)
            applyForce(new Vector3f(0, -MOVE_FORCE, 0));

        // --- Jump ---
        if (jump && onGround) {
            applyForce(new Vector3f(0, JUMP_FORCE, 0));
            onGround = false;
        }

        // --- Physics integration ---
        Vector3f acceleration = new Vector3f(accumulatedForces).div(mass);
        velocity.add(acceleration.mul(interval));
        velocity.mul(1.0f / (1.0f + DAMPING * interval));

        Vector3f nextPos = new Vector3f(position);

        // --- Axis-by-axis collision resolution with push-out ---
        nextPos.x += velocity.x * interval;
        if (isColliding(nextPos, chunkManager)) {
            nextPos.x = position.x;
            velocity.x = 0;
        }

        nextPos.z += velocity.z * interval;
        if (isColliding(nextPos, chunkManager)) {
            nextPos.z = position.z;
            velocity.z = 0;
        }

        nextPos.y += velocity.y * interval;
        if (isColliding(nextPos, chunkManager)) {
            if (velocity.y < 0)
                onGround = true;
            velocity.y = 0;
            while (isColliding(nextPos, chunkManager)) {
                nextPos.y += 0.01f;
            }
        } else {
            onGround = false;
        }

        // Commit position
        position.set(nextPos);

        // --- Animations ---
        boolean movingHoriz = fwd || back || left || right;
        float speedFactor = movingHoriz ? 1.0f : 0.0f;
        armSwingTime += interval * 6.0f * speedFactor;
        float swing = (float) Math.sin(armSwingTime) * 30.0f * speedFactor;

        // Apply animations based on camera mode
        if (cameraMode == CameraMode.FIRST_PERSON) {
            // "View bobbing" for hands
            float bobX = (float) Math.cos(armSwingTime * 0.5f) * 0.02f * speedFactor;
            float bobY = (float) Math.sin(armSwingTime) * 0.02f * speedFactor;
            leftArm.setLocalPosition(-0.35f + bobX, -0.2f + bobY, 0.4f);
            rightArm.setLocalPosition(0.35f + bobX, -0.2f + bobY, 0.4f);

            leftLeg.setLocalRotation(-swing, 0, 0);
            rightLeg.setLocalRotation(swing, 0, 0);
        } else {
            leftArm.setLocalRotation(swing, 0, 0);
            rightArm.setLocalRotation(-swing, 0, 0);
            leftLeg.setLocalRotation(-swing, 0, 0);
            rightLeg.setLocalRotation(swing, 0, 0);
        }

        // --- Body posture and movement ---
        float headYaw = viewRotation.y;
        float diff = headYaw - bodyYaw;
        while (diff > 180)
            diff -= 360;
        while (diff < -180)
            diff += 360;

        float deadzone = 50.0f;
        if (movingHoriz) {
            bodyYaw += diff * 10f * interval;
        } else {
            if (Math.abs(diff) > deadzone) {
                if (diff > 0)
                    bodyYaw = headYaw - deadzone;
                else
                    bodyYaw = headYaw + deadzone;
            }
        }

        body.setLocalRotation(0, bodyYaw, 0);
        head.setLocalRotation(viewRotation.x, headYaw - bodyYaw, 0);

        // Position torso safely away from camera eye
        float offsetDist = 0.25f;
        float offsetX = (float) Math.sin(yawRad) * offsetDist;
        float offsetZ = (float) -Math.cos(yawRad) * offsetDist;
        body.setLocalPosition(position.x - offsetX, position.y - 0.5f, position.z - offsetZ);

        // --- Camera placement based on mode ---
        camera.setRotation(viewRotation.x, viewRotation.y, viewRotation.z);

        if (cameraMode == CameraMode.FIRST_PERSON) {
            camera.setPosition(position.x, position.y, position.z);
        } else if (cameraMode == CameraMode.THIRD_PERSON_BACK) {
            float dist = 4.0f;
            float pitchRad = (float) Math.toRadians(viewRotation.x);
            // Offset camera back and up
            float cosP = (float) Math.cos(pitchRad);
            float sinP = (float) Math.sin(pitchRad);
            camera.setPosition(
                    position.x - (float) Math.sin(yawRad) * dist * cosP,
                    position.y + sinP * dist + 1.0f,
                    position.z - (float) -Math.cos(yawRad) * dist * cosP);
        } else if (cameraMode == CameraMode.THIRD_PERSON_FRONT) {
            float dist = 3.0f;
            // Face the player
            camera.setPosition(
                    position.x + (float) Math.sin(yawRad) * dist,
                    position.y + 0.5f,
                    position.z + (float) -Math.cos(yawRad) * dist);
            // Invert yaw to look at player
            camera.setRotation(viewRotation.x, headYaw + 180, 0);
        }
    }

    public void rotateView(float pitch, float yaw, float roll) {
        viewRotation.add(pitch, yaw, roll);
        // Clamp pitch
        if (viewRotation.x < -89)
            viewRotation.x = -89;
        if (viewRotation.x > 89)
            viewRotation.x = 89;
    }

    public void setCameraMode(CameraMode mode) {
        if (this.cameraMode == mode)
            return;

        // Clean up old mode
        if (this.cameraMode == CameraMode.FIRST_PERSON) {
            head.removeChild(leftArm);
            head.removeChild(rightArm);
        } else {
            body.removeChild(leftArm);
            body.removeChild(rightArm);
        }

        this.cameraMode = mode;

        if (mode == CameraMode.FIRST_PERSON) {
            head.setMesh(null);
            body.setMesh(null);
            // Attach arms to head so they follow gaze (pitch)
            head.addChild(leftArm, new Vector3f(-0.35f, -0.2f, 0.4f));
            head.addChild(rightArm, new Vector3f(0.35f, -0.2f, 0.4f));
            // Tilt arms slightly inward/natural
            leftArm.setLocalRotation(25, 10, 0);
            rightArm.setLocalRotation(25, -10, 0);
        } else {
            head.setMesh(headMeshInstance);
            body.setMesh(bodyMeshInstance);
            // Attach arms back to body
            body.addChild(leftArm, new Vector3f(-0.45f, 0f, 0f));
            body.addChild(rightArm, new Vector3f(0.45f, 0f, 0f));
            leftArm.setLocalRotation(0, 0, 0);
            rightArm.setLocalRotation(0, 0, 0);
        }
    }

    public CameraMode getCameraMode() {
        return cameraMode;
    }

    // Accessors
    public Vector3f getPosition() {
        return position;
    }

    public Camera getCamera() {
        return camera;
    }

    public Vector3f getViewRotation() {
        return viewRotation;
    }

    public List<Entity> getAllParts() {
        // We only return the parts we want to render in the scene pass.
        // Hiding head for local player to prevent looking through your own skull.
        // limbs/head are children of body, but the renderer recursively draws children.
        // If we want to hide children we need to be careful.
        // Actually, let's just make the head's mesh null or empty if it's the local
        // player.
        return List.of(body);
    }

    private boolean isColliding(Vector3f pos, ChunkManager chunkManager) {
        float halfWidth = 0.3f;
        float height = 1.8f;
        float feetY = pos.y - EYE_HEIGHT;

        int minX = (int) Math.floor(pos.x - halfWidth);
        int maxX = (int) Math.floor(pos.x + halfWidth);
        int minY = (int) Math.floor(feetY);
        int maxY = (int) Math.floor(feetY + height);
        int minZ = (int) Math.floor(pos.z - halfWidth);
        int maxZ = (int) Math.floor(pos.z + halfWidth);

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

    public AABB getBoundingBox() {
        return getBoundingBox(camera.getPosition());
    }

    public AABB getBoundingBox(Vector3f pos) {
        float halfWidth = 0.3f;
        float height = 1.8f;
        // The hitbox should be centered on X/Z and start from feet at Y
        return new AABB(
                pos.x - halfWidth, pos.y - EYE_HEIGHT, pos.z - halfWidth,
                halfWidth * 2, height, halfWidth * 2);
    }
}
