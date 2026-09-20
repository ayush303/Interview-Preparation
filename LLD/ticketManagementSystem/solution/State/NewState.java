package LLD.ticketManagementSystem.solution.State;

import LLD.ticketManagementSystem.solution.models.Ticket;
import LLD.ticketManagementSystem.solution.models.User;

public class NewState implements TicketState {

    @Override
    public void assign(Ticket ticket, User agent) {
        ticket.setAssignee(agent);
        ticket.setState(new AssignedState());
        ticket.notifyObservers("Assigned to agent: " + agent.getName());
    }

    @Override
    public void startProgress(Ticket ticket) {
        throw new IllegalStateException("Cannot start progress on unassigned ticket.");
    }

    @Override
    public void resolve(Ticket ticket) {
        throw new IllegalStateException("Cannot resolve ticket directly from NEW.");
    }

    @Override
    public String getStateName() {
        return "NEW";
    }
}
