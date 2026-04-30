package LLD.trafficControlSystem.solution.states.Signal;

import LLD.trafficControlSystem.solution.TrafficLight;
import LLD.trafficControlSystem.solution.enums.LightColor;

public class YellowState implements SignalState {
    @Override
    public void handle(TrafficLight context) {
        context.setColor(LightColor.YELLOW);
        // After being yellow, the next state is red.
        context.setNextState(new RedState());
    }

}
