package LLD.trafficControlSystem.solution.states.Intersection;

import LLD.trafficControlSystem.solution.IntersectionController;
import LLD.trafficControlSystem.solution.enums.Direction;
import LLD.trafficControlSystem.solution.enums.LightColor;

public class NorthSouthGreenState implements IntersectionState {
    @Override
    public void handle(IntersectionController context) throws InterruptedException {

        // Turn North and South green, ensure East and West are red
        context.getLight(Direction.NORTH).startGreen();
        context.getLight(Direction.SOUTH).startGreen();
        context.getLight(Direction.WEST).setColor(LightColor.RED);
        context.getLight(Direction.EAST).setColor(LightColor.RED);

        // Wait for green light duration
        Thread.sleep(context.getGreenDuration());

        // Transition North and South to Yellow
        context.getLight(Direction.NORTH).transition();
        context.getLight(Direction.SOUTH).transition();

        // Wait for yellow light duration
        Thread.sleep(context.getYellowDuration());

        // Transition North and South to Red
        context.getLight(Direction.NORTH).transition();
        context.getLight(Direction.SOUTH).transition();

        // Change the intersection's state to let East-West go
        context.setState(new EastWestGreenState());
    }

}
