package LLD.trafficControlSystem.solution.states.Signal;

import LLD.trafficControlSystem.solution.TrafficLight;

public interface SignalState {
    void handle(TrafficLight context);
}
