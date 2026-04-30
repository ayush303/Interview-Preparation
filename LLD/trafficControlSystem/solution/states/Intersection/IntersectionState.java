package LLD.trafficControlSystem.solution.states.Intersection;

import LLD.trafficControlSystem.solution.IntersectionController;

public interface IntersectionState {
    void handle(IntersectionController context) throws InterruptedException;
}
