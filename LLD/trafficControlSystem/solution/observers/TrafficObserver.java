package LLD.trafficControlSystem.solution.observers;

import LLD.trafficControlSystem.solution.enums.Direction;
import LLD.trafficControlSystem.solution.enums.LightColor;

public interface TrafficObserver {
    void update(int intersectionId, Direction direction, LightColor lightColor);
}
