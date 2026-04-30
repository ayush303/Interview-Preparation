package LLD.trafficControlSystem.solution.observers;

import LLD.trafficControlSystem.solution.enums.Direction;
import LLD.trafficControlSystem.solution.enums.LightColor;

public class CentralMonitor implements TrafficObserver {
    @Override
    public void update(int intersectionId, Direction direction, LightColor lightColor) {
        System.out.printf("[MONITOR] Intersection %d: Light for %s direction changed to %s.\n",
                intersectionId, direction, lightColor);
    }

}
