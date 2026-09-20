package LLD.ticketManagementSystem.solution.State;

import LLD.ticketManagementSystem.solution.models.Ticket;
import LLD.ticketManagementSystem.solution.models.User;

public class ResolvedState implements TicketState {

    @Override
    public void assign(Ticket ticket, User agent) {
        throw new IllegalStateException("Ticket resolved.");
    }

    @Override
    public void startProgress(Ticket ticket) {
        throw new IllegalStateException("Ticket resolved.");
    }

    @Override
    public void resolve(Ticket ticket) {
        System.out.println("Already resolved.");
    }

    @Override
    public String getStateName() {
        return "RESOLVED";
    }
}
