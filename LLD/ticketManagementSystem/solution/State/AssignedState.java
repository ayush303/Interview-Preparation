package LLD.ticketManagementSystem.solution.State;

import LLD.ticketManagementSystem.solution.models.Ticket;
import LLD.ticketManagementSystem.solution.models.User;

public class AssignedState implements TicketState {

    @Override
    public void assign(Ticket ticket, User agent) {
        ticket.setAssignee(agent); // Reassignment
        ticket.notifyObservers("Re-assigned to agent: " + agent.getName());
    }

    @Override
    public void startProgress(Ticket ticket) {
        ticket.setState(new InProgressState());
        ticket.notifyObservers("Work has started.");
    }

    @Override
    public void resolve(Ticket ticket) {
        throw new IllegalStateException("Cannot resolve ticket directly from NEW.");
    }

    @Override
    public String getStateName() {
        return "ASSIGNED";
    }
}
