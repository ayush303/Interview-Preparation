package LLD.ticketManagementSystem.solution.models;

import LLD.ticketManagementSystem.solution.Observer.Observer;
import LLD.ticketManagementSystem.solution.enums.Role;

public class User implements Observer {

    private final String id;
    private final String name;
    private final Role role;

    public User(String id, String name, Role role) {
        this.id = id;
        this.name = name;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public Role getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    @Override
    public void onUpdate(Ticket ticket, String message) {
        System.out.println("[NOTIFICATION to " + name + "] Ticket " + ticket.getId() + ": " + message);
    }
}
