package io.github.skippi.hordetest.gravity;

public interface Action {
    default double getWeight() { return 1.0; }
    void call(PhysicsScheduler physicsScheduler);
}
