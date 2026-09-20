package LLD.ticketManagementSystem.solution.State;

import LLD.ticketManagementSystem.solution.models.Ticket;
import LLD.ticketManagementSystem.solution.models.User;

public class InProgressState implements TicketState {
    @Override
    public void assign(Ticket ticket, User agent) {
        throw new IllegalStateException("Cannot reassign while in progress.");
    }

    @Override
    public void startProgress(Ticket ticket) {
        System.out.println("Already in progress.");
    }

    @Override
    public void resolve(Ticket ticket) {
        ticket.setState(new ResolvedState());
        ticket.notifyObservers("Ticket has been RESOLVED.");
    }

    @Override
    public String getStateName() {
        return "IN_PROGRESS";
    }
}
