package LLD.ticketManagementSystem.solution;

import LLD.ticketManagementSystem.solution.Strategy.RoundRobinRouting;
import LLD.ticketManagementSystem.solution.Strategy.RoutingStrategy;
import LLD.ticketManagementSystem.solution.enums.Priority;
import LLD.ticketManagementSystem.solution.enums.Role;
import LLD.ticketManagementSystem.solution.models.Ticket;
import LLD.ticketManagementSystem.solution.models.User;

public class ServiceNowSystem {

    public static void main(String args[]) {
        RoutingStrategy routingStrategy = new RoundRobinRouting();
        IncidentManager system = IncidentManager.getInstance(routingStrategy);

        User agent1 = new User("A1", "Alice", Role.AGENT);
        User agent2 = new User("A2", "Bob", Role.AGENT);
        system.addAgent(agent1);
        system.addAgent(agent2);

        User employee = new User("U1", "Charlie", Role.REPORTER);

        // 2. Walkthrough Lifecycle
        Ticket t1 = system.createIncident("Database is down", Priority.P1_CRITICAL, employee);

        System.out.println("\n--- Triggering Auto-Assign ---");
        system.autoAssign(t1.getId());

        System.out.println("\n--- Agent starts work ---");
        t1.markInProgress();

        System.out.println("\n--- Agent resolves issue ---");
        t1.resolveTicket();

        // 3. Testing illegal state transition
        System.out.println("\n--- Testing illegal transition ---");
        try {
            t1.markInProgress();
        } catch (IllegalStateException e) {
            System.out.println("Caught Expected Error: " + e.getMessage());
        }


    }
}
