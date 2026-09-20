package LLD.ticketManagementSystem.solution;

import LLD.ticketManagementSystem.solution.Strategy.RoutingStrategy;
import LLD.ticketManagementSystem.solution.enums.Priority;
import LLD.ticketManagementSystem.solution.models.Ticket;
import LLD.ticketManagementSystem.solution.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class IncidentManager {
    private final Map<String, Ticket> ticketDb = new ConcurrentHashMap<>();
    private final List<User> agentDb = Collections.synchronizedList(new ArrayList<>());

    private RoutingStrategy routingStrategy;
    private final AtomicInteger ticketCounter = new AtomicInteger(1);

    private static final IncidentManager instance = null;
    private static Object lock = new Object();

    public IncidentManager(RoutingStrategy routingStrategy) {
        this.routingStrategy = routingStrategy;
    }

    public static IncidentManager getInstance(RoutingStrategy routingStrategy) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    return new IncidentManager(routingStrategy);
                }
            }
        }
        return instance;
    }

    public void addAgent(User agent) {
        agentDb.add(agent);
    }

    public Ticket createIncident(String title, Priority priority, User reporter) {
        String id = "INC-" + String.format("%04d", ticketCounter.getAndIncrement());
        Ticket ticket = new Ticket(id, title, priority, reporter);
        ticketDb.put(id, ticket);
        System.out.println("Created " + id + " | Status: " + ticket.getCurrentState());
        return ticket;
    }

    public void autoAssign(String ticketId) {
        Ticket ticket = ticketDb.get(ticketId);
        if (ticket != null) {
            User bestAgent = routingStrategy.findAgent(agentDb);
            ticket.assignTo(bestAgent);
        }
    }

    public Ticket getTicket(String ticketId) {
        return ticketDb.get(ticketId);
    }
}
