package engine.ai.behavior.decorator;

import engine.ai.AIBlackboard;
import engine.ai.behavior.AbstractBehaviorNode;
import engine.ai.behavior.BehaviorNode;

/**
 * Inverter decorator node - inverts the result of its child
 * SUCCESS becomes FAILURE, FAILURE becomes SUCCESS
 * RUNNING remains RUNNING
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class InverterNode extends AbstractBehaviorNode {
    
    private final BehaviorNode child;
    
    /**
     * Create a new inverter node
     * @param name Name of the node
     * @param child Child node to invert
     */
    public InverterNode(String name, BehaviorNode child) {
        super(name);
        this.child = child;
    }
    
    /**
     * Get the child node
     * @return Child node
     */
    public BehaviorNode getChild() {
        return child;
    }
    
    @Override
    protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
        if (child == null) {
            return Status.FAILURE;
        }
        
        Status childStatus = child.execute(blackboard, deltaTime);
        
        switch (childStatus) {
            case SUCCESS:
                return Status.FAILURE;
            case FAILURE:
                return Status.SUCCESS;
            case RUNNING:
                return Status.RUNNING;
            default:
                return Status.FAILURE;
        }
    }
    
    @Override
    protected void doReset() {
        if (child != null) {
            child.reset();
        }
    }
    
    @Override
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDebugInfo());
        if (child != null) {
            sb.append(" - Child: [").append(child.getName()).append("]");
        }
        return sb.toString();
    }
    
    /**
     * Create an inverter node
     * @param name Name of the inverter
     * @param child Child node to invert
     * @return New inverter node
     */
    public static InverterNode create(String name, BehaviorNode child) {
        return new InverterNode(name, child);
    }
}