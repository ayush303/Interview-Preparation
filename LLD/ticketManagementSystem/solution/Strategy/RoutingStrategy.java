package LLD.ticketManagementSystem.solution.Strategy;

import LLD.ticketManagementSystem.solution.models.User;

import java.util.List;

public interface RoutingStrategy {
    User findAgent(List<User> agents);
}
