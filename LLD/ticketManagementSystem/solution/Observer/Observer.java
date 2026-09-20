package LLD.ticketManagementSystem.solution.Observer;

import LLD.ticketManagementSystem.solution.models.Ticket;

public interface Observer {
    void onUpdate(Ticket ticket, String message);
}
