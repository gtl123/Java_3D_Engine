package engine.ai.examples;

import engine.ai.*;
import engine.ai.behavior.*;
import engine.ai.behavior.composite.*;
import engine.ai.behavior.decorator.*;
import engine.ai.behavior.action.*;
import engine.ai.decision.*;
import engine.ai.navigation.*;
import engine.ai.debug.*;
import engine.entity.Entity;
import engine.raster.Mesh;
import engine.logging.LogManager;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstration of the AI Framework capabilities
 * Shows how to create and use AI entities with behavior trees, pathfinding, and decision making
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class SimpleAIDemo {
    
    private static final LogManager logger = LogManager.getInstance();
    
    private AIManager aiManager;
    private AIDebugger aiDebugger;
    
    public SimpleAIDemo() {
        // Initialize AI systems
        this.aiManager = new AIManager();
        this.aiDebugger = new AIDebugger(aiManager);
        
        logger.info("SimpleAIDemo initialized");
    }
    
    /**
     * Run the AI demonstration
     */
    public void runDemo() {
        logger.info("Starting AI Framework demonstration...");
        
        // Setup navigation mesh
        setupNavigationMesh();
        
        // Create behavior trees
        createBehaviorTrees();
        
        // Create AI entities
        createAIEntities();
        
        // Setup decision making
        setupDecisionMaking();
        
        // Run simulation
        runSimulation();
        
        logger.info("AI Framework demonstration completed");
    }
    
    /**
     * Setup navigation mesh for the demo world
     */
    private void setupNavigationMesh() {
        logger.info("Setting up navigation mesh...");
        
        NavigationMesh navMesh = aiManager.getNavigationMesh();
        
        // Create a simple demo world bounds
        BoundingBox worldBounds = new BoundingBox(-50, 0, -50, 50, 10, 50);
        
        // Simple obstacle checker for demo
        NavigationMesh.ObstacleChecker obstacleChecker = (position) -> {
            // Simple obstacles: walls at certain positions
            if (Math.abs(position.x) > 45 || Math.abs(position.z) > 45) {
                return true; // World boundaries
            }
            
            // Add some obstacles in the middle
            if (position.x > -10 && position.x < 10 && position.z > -5 && position.z < 5) {
                return true; // Central obstacle
            }
            
            return position.y < 0; // Ground level
        };
        
        // Generate navigation mesh
        navMesh.generateMesh(worldBounds, obstacleChecker);
        
        logger.info("Navigation mesh setup complete: " + navMesh.getStats());
    }
    
    /**
     * Create behavior trees for different AI types
     */
    private void createBehaviorTrees() {
        logger.info("Creating behavior trees...");
        
        BehaviorTreeSystem behaviorSystem = aiManager.getBehaviorTreeSystem();
        
        // Create a simple patrol behavior tree
        BehaviorNode patrolTree = createPatrolBehaviorTree();
        behaviorSystem.registerTree("patrol_behavior", patrolTree);
        
        // Create a guard behavior tree
        BehaviorNode guardTree = createGuardBehaviorTree();
        behaviorSystem.registerTree("guard_behavior", guardTree);
        
        // Create a wanderer behavior tree
        BehaviorNode wandererTree = createWandererBehaviorTree();
        behaviorSystem.registerTree("wanderer_behavior", wandererTree);
        
        logger.info("Behavior trees created successfully");
    }
    
    /**
     * Create a patrol behavior tree
     */
    private BehaviorNode createPatrolBehaviorTree() {
        // Patrol behavior: Move between waypoints in sequence
        return SequenceNode.create("Patrol Sequence",
            new SetPatrolTargetAction("Set Next Waypoint"),
            new MoveToAction("Move to Waypoint", 3.0f, 2.0f),
            new WaitAction("Wait at Waypoint", 2.0f)
        );
    }
    
    /**
     * Create a guard behavior tree
     */
    private BehaviorNode createGuardBehaviorTree() {
        // Guard behavior: Stay at post, investigate disturbances
        return SelectorNode.create("Guard Selector",
            SequenceNode.create("Investigate Sequence",
                new CheckForDisturbanceCondition("Check Disturbance"),
                new MoveToAction("Investigate", 4.0f, 1.0f),
                new WaitAction("Look Around", 3.0f)
            ),
            new ReturnToPostAction("Return to Post")
        );
    }
    
    /**
     * Create a wanderer behavior tree
     */
    private BehaviorNode createWandererBehaviorTree() {
        // Wanderer behavior: Move randomly around the world
        return RepeaterNode.infinite("Wander Loop",
            SequenceNode.create("Wander Sequence",
                new SetRandomTargetAction("Pick Random Target"),
                new MoveToAction("Move to Target", 2.0f, 3.0f),
                new WaitAction("Rest", 1.0f)
            )
        );
    }
    
    /**
     * Create AI entities for the demonstration
     */
    private void createAIEntities() {
        logger.info("Creating AI entities...");
        
        // Create patrol entity
        Entity patrolEntity = new Entity(null); // Null mesh for demo
        patrolEntity.setLocalPosition(10, 1, 10);
        String patrolId = aiManager.registerAIEntity(patrolEntity, "patrol_behavior");
        setupPatrolWaypoints(patrolId);
        
        // Create guard entity
        Entity guardEntity = new Entity(null);
        guardEntity.setLocalPosition(0, 1, 20);
        String guardId = aiManager.registerAIEntity(guardEntity, "guard_behavior");
        setupGuardPost(guardId);
        
        // Create wanderer entity
        Entity wandererEntity = new Entity(null);
        wandererEntity.setLocalPosition(-15, 1, -15);
        String wandererId = aiManager.registerAIEntity(wandererEntity, "wanderer_behavior");
        
        logger.info("Created AI entities: patrol=" + patrolId + ", guard=" + guardId + ", wanderer=" + wandererId);
    }
    
    /**
     * Setup patrol waypoints for patrol entity
     */
    private void setupPatrolWaypoints(String entityId) {
        AIBlackboard blackboard = aiManager.getEntityBlackboard(entityId);
        if (blackboard != null) {
            List<Vector3f> waypoints = Arrays.asList(
                new Vector3f(10, 1, 10),
                new Vector3f(20, 1, 10),
                new Vector3f(20, 1, 20),
                new Vector3f(10, 1, 20)
            );
            blackboard.setValue("patrolWaypoints", waypoints);
            blackboard.setValue("currentWaypointIndex", 0);
        }
    }
    
    /**
     * Setup guard post for guard entity
     */
    private void setupGuardPost(String entityId) {
        AIBlackboard blackboard = aiManager.getEntityBlackboard(entityId);
        if (blackboard != null) {
            blackboard.setValue("guardPost", new Vector3f(0, 1, 20));
            blackboard.setValue("guardRadius", 10.0f);
        }
    }
    
    /**
     * Setup decision making for entities
     */
    private void setupDecisionMaking() {
        logger.info("Setting up decision making...");
        
        DecisionSystem decisionSystem = aiManager.getDecisionSystem();
        
        // Add decisions for entities (simplified for demo)
        for (String entityId : aiManager.getRegisteredEntities()) {
            AIEntity aiEntity = aiManager.getAIEntity(entityId);
            if (aiEntity != null) {
                aiEntity.setHasDecisionMaking(true);
                
                // Add basic decisions
                decisionSystem.registerDecision(entityId, new IdleDecision());
                decisionSystem.registerDecision(entityId, new ExploreDecision());
            }
        }
        
        logger.info("Decision making setup complete");
    }
    
    /**
     * Run the AI simulation
     */
    private void runSimulation() {
        logger.info("Running AI simulation...");
        
        // Simulate for 30 seconds at 30 FPS
        float deltaTime = 1.0f / 30.0f;
        int totalFrames = 30 * 30; // 30 seconds
        
        for (int frame = 0; frame < totalFrames; frame++) {
            // Update AI systems
            aiManager.updateAll(deltaTime);
            
            // Update debug information
            aiDebugger.update(deltaTime);
            
            // Print debug info every 5 seconds
            if (frame % (30 * 5) == 0) {
                printSimulationStatus(frame / 30);
            }
            
            // Simulate frame timing
            try {
                Thread.sleep(33); // ~30 FPS
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Print final statistics
        printFinalStatistics();
    }
    
    /**
     * Print simulation status
     */
    private void printSimulationStatus(int seconds) {
        logger.info("=== Simulation Status (T+" + seconds + "s) ===");
        logger.info(aiDebugger.generateSummaryReport());
        
        // Print entity states
        for (String entityId : aiManager.getRegisteredEntities()) {
            AIEntity entity = aiManager.getAIEntity(entityId);
            if (entity != null) {
                logger.info("Entity " + entityId + ": " + entity.getDebugInfo());
            }
        }
    }
    
    /**
     * Print final simulation statistics
     */
    private void printFinalStatistics() {
        logger.info("=== Final Simulation Statistics ===");
        logger.info(aiDebugger.generateDebugReport());
    }
    
    /**
     * Cleanup demo resources
     */
    public void cleanup() {
        if (aiDebugger != null) {
            aiDebugger.cleanup();
        }
        if (aiManager != null) {
            aiManager.cleanup();
        }
        logger.info("SimpleAIDemo cleaned up");
    }
    
    // Example action implementations (simplified for demo)
    
    private static class SetPatrolTargetAction extends AbstractBehaviorNode {
        public SetPatrolTargetAction(String name) { super(name); }
        
        @Override
        protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
            @SuppressWarnings("unchecked")
            List<Vector3f> waypoints = blackboard.getValue("patrolWaypoints", List.class);
            Integer currentIndex = blackboard.getValue("currentWaypointIndex", Integer.class, 0);
            
            if (waypoints != null && !waypoints.isEmpty()) {
                Vector3f target = waypoints.get(currentIndex % waypoints.size());
                blackboard.setValue("moveTarget", target);
                blackboard.setValue("currentWaypointIndex", (currentIndex + 1) % waypoints.size());
                return Status.SUCCESS;
            }
            return Status.FAILURE;
        }
    }
    
    private static class WaitAction extends AbstractBehaviorNode {
        private final float waitTime;
        private float elapsed = 0;
        
        public WaitAction(String name, float waitTime) {
            super(name);
            this.waitTime = waitTime;
        }
        
        @Override
        protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
            elapsed += deltaTime;
            return elapsed >= waitTime ? Status.SUCCESS : Status.RUNNING;
        }
        
        @Override
        protected void doReset() {
            elapsed = 0;
        }
    }
    
    private static class CheckForDisturbanceCondition extends AbstractBehaviorNode {
        public CheckForDisturbanceCondition(String name) { super(name); }
        
        @Override
        protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
            // Simplified: random chance of disturbance
            return Math.random() < 0.1 ? Status.SUCCESS : Status.FAILURE;
        }
    }
    
    private static class ReturnToPostAction extends AbstractBehaviorNode {
        public ReturnToPostAction(String name) { super(name); }
        
        @Override
        protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
            Vector3f guardPost = blackboard.getValue("guardPost", Vector3f.class);
            if (guardPost != null) {
                blackboard.setValue("moveTarget", guardPost);
                return Status.SUCCESS;
            }
            return Status.FAILURE;
        }
    }
    
    private static class SetRandomTargetAction extends AbstractBehaviorNode {
        public SetRandomTargetAction(String name) { super(name); }
        
        @Override
        protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
            // Set random target within bounds
            float x = (float) (Math.random() * 80 - 40); // -40 to 40
            float z = (float) (Math.random() * 80 - 40);
            blackboard.setValue("moveTarget", new Vector3f(x, 1, z));
            return Status.SUCCESS;
        }
    }
    
    // Example decision implementations
    
    private static class IdleDecision implements Decision {
        @Override
        public String getName() { return "Idle"; }
        
        @Override
        public float evaluate(AIBlackboard blackboard, DecisionSystem.DecisionContext context, float deltaTime) {
            // Always has some base utility
            return 0.2f;
        }
        
        @Override
        public void execute(AIBlackboard blackboard, DecisionSystem.DecisionContext context, float deltaTime) {
            // Do nothing - just idle
        }
    }
    
    private static class ExploreDecision implements Decision {
        @Override
        public String getName() { return "Explore"; }
        
        @Override
        public float evaluate(AIBlackboard blackboard, DecisionSystem.DecisionContext context, float deltaTime) {
            // Higher utility if no current target
            Vector3f target = blackboard.getValue("moveTarget", Vector3f.class);
            return target == null ? 0.8f : 0.1f;
        }
        
        @Override
        public void execute(AIBlackboard blackboard, DecisionSystem.DecisionContext context, float deltaTime) {
            // Set exploration target
            if (!blackboard.hasKey("moveTarget")) {
                float x = (float) (Math.random() * 60 - 30);
                float z = (float) (Math.random() * 60 - 30);
                blackboard.setValue("moveTarget", new Vector3f(x, 1, z));
            }
        }
    }
    
    /**
     * Main method to run the demo
     */
    public static void main(String[] args) {
        SimpleAIDemo demo = new SimpleAIDemo();
        try {
            demo.runDemo();
        } finally {
            demo.cleanup();
        }
    }
}