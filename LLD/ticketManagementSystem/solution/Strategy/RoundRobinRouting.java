package LLD.ticketManagementSystem.solution.Strategy;

import LLD.ticketManagementSystem.solution.models.User;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinRouting implements RoutingStrategy {
    private AtomicInteger index = new AtomicInteger(1);


    @Override
    public User findAgent(List<User> agents) {
        if (agents.isEmpty()) {
            throw new RuntimeException("No agents available");
        }
        int curr = index.getAndUpdate(i -> i + 1) % agents.size();
        return agents.get(curr);
    }
}
