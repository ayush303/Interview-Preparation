package LLD.ticketManagementSystem.solution.models;

import LLD.ticketManagementSystem.solution.Observer.Observer;
import LLD.ticketManagementSystem.solution.Observer.Subject;
import LLD.ticketManagementSystem.solution.State.NewState;
import LLD.ticketManagementSystem.solution.State.TicketState;
import LLD.ticketManagementSystem.solution.enums.Priority;

import java.util.ArrayList;
import java.util.List;

public class Ticket implements Subject {
    private String id;
    private User reporter;
    private User assignee;
    private String title;
    private Priority priority;

    private TicketState currentState;
    private List<Observer> observers = new ArrayList<>();

    public Ticket(String id, String title, Priority priority, User reporter) {
        this.id = id;
        this.title = title;
        this.priority = priority;
        this.reporter = reporter;
        this.currentState = new NewState();

        // The reporter automatically watches this ticket
        this.addObserver(reporter);
    }

    // --- State Delegation ---
    public void assignTo(User agent) {
        currentState.assign(this, agent);
    }
    public void markInProgress() {
        currentState.startProgress(this);
    }
    public void resolveTicket() {
        currentState.resolve(this);
    }

    // --- Package-private Setters for State Classes ---
    public void setState(TicketState state) {
        String oldState = currentState.getStateName();
        this.currentState = state;
        notifyObservers("Ticket has been moved from " + oldState + " to " + state.getStateName());
    }

    public void setAssignee(User assignee) {
        this.assignee = assignee;
    }

    @Override
    public void addObserver(Observer o) {
        observers.add(o);
    }

    @Override
    public void notifyObservers(String message) {
        for (Observer o: observers) {
            o.onUpdate(this, message);
        }
    }

    public String getId() {
        return id;
    }

    public User getReporter() {
        return reporter;
    }

    public User getAssignee() {
        return assignee;
    }

    public String getTitle() {
        return title;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getCurrentState() {
        return currentState.getStateName();
    }

    public List<Observer> getObservers() {
        return observers;
    }
}
