package LLD.ticketManagementSystem.solution.State;

import LLD.ticketManagementSystem.solution.models.Ticket;
import LLD.ticketManagementSystem.solution.models.User;

public interface TicketState {
    void assign(Ticket ticket, User agent);
    void startProgress(Ticket ticket);
    void resolve(Ticket ticket);
    String getStateName();
}
