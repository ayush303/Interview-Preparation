package LLD.trafficControlSystem.solution.states.Intersection;

import LLD.trafficControlSystem.solution.IntersectionController;
import LLD.trafficControlSystem.solution.enums.Direction;
import LLD.trafficControlSystem.solution.enums.LightColor;

public class EastWestGreenState implements IntersectionState {
    @Override
    public void handle(IntersectionController context) throws InterruptedException {

        context.getLight(Direction.EAST).startGreen();
        context.getLight(Direction.WEST).startGreen();
        context.getLight(Direction.NORTH).setColor(LightColor.RED);
        context.getLight(Direction.SOUTH).setColor(LightColor.RED);

        // Wait for green light duration
        Thread.sleep(context.getGreenDuration());

        // Transition EAST and WEST to Yellow
        context.getLight(Direction.EAST).transition();
        context.getLight(Direction.WEST).transition();

        // Wait for yellow light duration
        Thread.sleep(context.getYellowDuration());

        // Transition North and South to Red
        context.getLight(Direction.NORTH).transition();
        context.getLight(Direction.SOUTH).transition();

        // Change the intersection's state to let East-West go
        context.setState(new NorthSouthGreenState());
    }

}
