package engine.ai.behavior.composite;

import engine.ai.AIBlackboard;
import engine.ai.behavior.AbstractBehaviorNode;
import engine.ai.behavior.BehaviorNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequence composite node - executes children in order until one fails
 * Returns SUCCESS only if all children succeed
 * Returns FAILURE if any child fails
 * Returns RUNNING if current child is running
 * Part of the Enterprise AI Framework - Phase 4 Implementation
 */
public class SequenceNode extends AbstractBehaviorNode {
    
    private final List<BehaviorNode> children;
    private int currentChildIndex;
    
    /**
     * Create a new sequence node
     * @param name Name of the node
     */
    public SequenceNode(String name) {
        super(name);
        this.children = new ArrayList<>();
        this.currentChildIndex = 0;
    }
    
    /**
     * Add a child node to this sequence
     * @param child Child node to add
     * @return This node for method chaining
     */
    public SequenceNode addChild(BehaviorNode child) {
        children.add(child);
        return this;
    }
    
    /**
     * Remove a child node from this sequence
     * @param child Child node to remove
     * @return true if removed successfully
     */
    public boolean removeChild(BehaviorNode child) {
        return children.remove(child);
    }
    
    /**
     * Get all child nodes
     * @return List of child nodes
     */
    public List<BehaviorNode> getChildren() {
        return new ArrayList<>(children);
    }
    
    /**
     * Get the number of child nodes
     * @return Number of children
     */
    public int getChildCount() {
        return children.size();
    }
    
    /**
     * Get the current child being executed
     * @return Current child node, or null if none
     */
    public BehaviorNode getCurrentChild() {
        if (currentChildIndex >= 0 && currentChildIndex < children.size()) {
            return children.get(currentChildIndex);
        }
        return null;
    }
    
    /**
     * Get the current child index
     * @return Current index
     */
    public int getCurrentChildIndex() {
        return currentChildIndex;
    }
    
    @Override
    protected Status doExecute(AIBlackboard blackboard, float deltaTime) {
        // If no children, return success
        if (children.isEmpty()) {
            return Status.SUCCESS;
        }
        
        // Execute children in sequence
        while (currentChildIndex < children.size()) {
            BehaviorNode currentChild = children.get(currentChildIndex);
            Status childStatus = currentChild.execute(blackboard, deltaTime);
            
            switch (childStatus) {
                case SUCCESS:
                    // Move to next child
                    currentChildIndex++;
                    break;
                    
                case FAILURE:
                    // Sequence fails if any child fails
                    return Status.FAILURE;
                    
                case RUNNING:
                    // Wait for current child to complete
                    return Status.RUNNING;
            }
        }
        
        // All children succeeded
        return Status.SUCCESS;
    }
    
    @Override
    protected void doReset() {
        currentChildIndex = 0;
        // Reset all children
        for (BehaviorNode child : children) {
            child.reset();
        }
    }
    
    @Override
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getDebugInfo());
        sb.append(" - Child ").append(currentChildIndex).append("/").append(children.size());
        
        if (getCurrentChild() != null) {
            sb.append(" [").append(getCurrentChild().getName()).append("]");
        }
        
        return sb.toString();
    }
    
    /**
     * Get detailed debug information including all children
     * @return Detailed debug string
     */
    public String getDetailedDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(getDebugInfo()).append("\n");
        
        for (int i = 0; i < children.size(); i++) {
            BehaviorNode child = children.get(i);
            String prefix = (i == currentChildIndex) ? "-> " : "   ";
            sb.append(prefix).append(i).append(": ").append(child.getDebugInfo()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Create a sequence node with the given children
     * @param name Name of the sequence
     * @param children Child nodes
     * @return New sequence node
     */
    public static SequenceNode create(String name, BehaviorNode... children) {
        SequenceNode sequence = new SequenceNode(name);
        for (BehaviorNode child : children) {
            sequence.addChild(child);
        }
        return sequence;
    }
}