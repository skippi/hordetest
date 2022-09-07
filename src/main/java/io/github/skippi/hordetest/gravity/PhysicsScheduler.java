package io.github.skippi.hordetest.gravity;

import java.util.ArrayDeque;
import java.util.Queue;

public class PhysicsScheduler {
  private Queue<Action> queue = new ArrayDeque<>();

  public void schedule(Action action) {
    queue.add(action);
  }

  public void tick() {
    double weight = 0.0;
    while (!queue.isEmpty() && weight < 1.0) {
      Action action = queue.remove();
      action.call(this);
      weight += action.getWeight();
    }
  }
}
