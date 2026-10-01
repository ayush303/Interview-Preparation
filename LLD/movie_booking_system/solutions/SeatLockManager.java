package LLD.movie_booking_system.solutions;

import LLD.movie_booking_system.solutions.enums.SeatStatus;
import LLD.movie_booking_system.solutions.models.Seat;
import LLD.movie_booking_system.solutions.models.Show;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SeatLockManager {
    private final Map<Show, Map<Seat, String>> lockedSeats = new ConcurrentHashMap<>();
    private final Map<Show, Map<Seat, ScheduledFuture<?>>> expireTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final long LOCK_TIMEOUT_MS = 500; // 0.5 seconds. In real world, timeout would be in minutez

    // Returns true only if every requested seat was available and is now locked for this user.
    public boolean lockSeats(Show show, List<Seat> seats, String userId) {
        synchronized (show.getLock()) {
            // serialize lock/unlock/confirm for this show
            // Check if any of the requested seats are already locked or booked
            for (Seat seat: seats) {
                if (seat.getStatus() != SeatStatus.AVAILABLE) {
                    System.out.println("Seat " + seat.getId() + " is not available.");
                    return false;
                }
            }

            // Lock the seats and record who holds each lock
            for (Seat seat : seats) {
                seat.setStatus(SeatStatus.LOCKED);
            }

            lockedSeats.computeIfAbsent(show, k -> new HashMap<>());
            for (Seat seat: seats) {
                lockedSeats.get(show).put(seat, userId);
            }

            // Schedule a single task to release this batch if the booking is not completed in time,
            // and remember it per seat so a confirmed or released booking can cancel it.

            ScheduledFuture<?> task = scheduler.schedule(() -> unlockSeats(show, seats, userId), LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            expireTasks.computeIfAbsent(show, k -> new HashMap<>());
            for (Seat seat : seats) {
                expireTasks.get(show).put(seat, task);
            }

            System.out.println("Locked seats: " + seats.stream().map(Seat::getId).collect(Collectors.toList()) + " for user " + userId);
            return true;
        }
    }

    // Atomically confirm the booking: succeed only if the user still holds the lock on every seat,
    // then mark those seats BOOKED. This closes the window where a lock expired during a slow payment.
    public boolean confirmSeats(Show show, List<Seat> seats, String userId) {
        synchronized (show.getLock()) {
            Map<Seat, String> showLocks = lockedSeats.get(show);
            if (showLocks == null) return false;

            for (Seat seat: seats) {
                if (!userId.equals(showLocks.get(seat))) {
                    return false; // lock expired or was taken by another user
                }
            }

            for (Seat seat: seats) {
                seat.setStatus(SeatStatus.BOOKED);
                showLocks.remove(seat);
                cancelExpiry(show, seat); // the seat is booked, so the timeout is no longer needed
            }
            if (showLocks.isEmpty()) {
                lockedSeats.remove(show);
            }
            return true;
        }
    }

    public void unlockSeats(Show show, List<Seat> seats, String userId) {
        synchronized (show.getLock()) {
            Map<Seat, String> showLocks = lockedSeats.get(show);
            if (showLocks == null) return;

            for (Seat seat: seats) {
                // Only the user who currently holds the lock can release it
                if (userId.equals(showLocks.get(seat))) {
                    showLocks.remove(seat);
                    cancelExpiry(show, seat);

                    // Revert only a seat that is still LOCKED; a BOOKED seat is left untouched
                    if (seat.getStatus() == SeatStatus.LOCKED) {
                        seat.setStatus(SeatStatus.AVAILABLE);
                        System.out.println("Released lock on seat: " + seat.getId());
                    }
                }
            }
            if (showLocks.isEmpty()) {
                lockedSeats.remove(show);
            }
        }
    }

    // Cancel and forget the pending expiry task for a seat once its lock is resolved.
    private void cancelExpiry(Show show, Seat seat) {
        Map<Seat, ScheduledFuture<?>> tasks = expireTasks.get(show);
        if (tasks != null) {
            ScheduledFuture<?> task = tasks.remove(seat);
            if (task != null) {
                task.cancel(false);
            }
            if (tasks.isEmpty()) {
                expireTasks.remove(show);
            }
        }
    }

    public void shutdown() {
        System.out.println("Shutting down SeatLockManager scheduler.");
        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
