package com.skanga.core;

/**
 * A concrete implementation of {@link BaseAgent}.
 * This class can be used to instantiate a functional agent with the behaviors
 * defined in `BaseAgent`. It can be extended further for more specialized agents,
 * or used directly if `BaseAgent`'s default capabilities are sufficient.
 *
 * This class primarily serves as a simple, instantiable version of `BaseAgent`.
 * If `BaseAgent` were to have abstract methods needing specific implementations
 * beyond its current scope, `ConcreteAgent` would provide them.
 */
public class ConcreteAgent extends BaseAgent {

    /**
     * Default constructor. Initializes a new ConcreteAgent instance.
     * Calls the super constructor of {@link BaseAgent}.
     */
    public ConcreteAgent() {
        super();
        // Initialize any specific properties for ConcreteAgent if needed in the future.
        // For example, it could set up a default AIProvider or ChatHistory here if desired.
    }

    /**
     * Static factory method to create a new instance of ConcreteAgent.
     * This is a convenience method for instantiation.
     *
     * @return A new instance of {@link ConcreteAgent}.
     */
    public static ConcreteAgent make() {
        return new ConcreteAgent();
    }

    // If BaseAgent had abstract methods that ConcreteAgent needed to implement,
    // they would be implemented here. For example:
    // @Override
    // protected AIProvider defaultProvider() {
    //     // Return a default AIProvider instance
    //     // e.g., return new SomeDefaultProvider(); // Assuming SomeDefaultProvider exists
    //     throw new UnsupportedOperationException("No default AIProvider configured for ConcreteAgent.");
    // }

    // Other specific methods or overrides for ConcreteAgent can be added below.
}
