package LLD.movie_booking_system.solutions;

import LLD.movie_booking_system.solutions.enums.SeatStatus;
import LLD.movie_booking_system.solutions.enums.SeatType;
import LLD.movie_booking_system.solutions.models.*;
import LLD.movie_booking_system.solutions.strategy.payment.CreditCardPaymentStrategy;
import LLD.movie_booking_system.solutions.strategy.pricing.WeekdayPricingStrategy;

import java.util.*;
import java.util.stream.*;
import java.time.*;

public class MovieBookingDemo {
    public static void main (String[]args){
        // Setup
        MovieBookingService service = MovieBookingService.getInstance();

        City nyc = service.addCity("New York");
        City la = service.addCity("Los Angeles");

        // 2. Add movies
        Movie matrix = new Movie("M1", "The Matrix", 120);
        Movie avengers = new Movie("M2", "Avengers: Endgame", 170);
        service.addMovie(matrix);
        service.addMovie(avengers);

        // Add Seats for a Screen
        Screen screen1 = new Screen("S1");

        for (int i = 1; i <= 10; i++) {
            screen1.addSeat(new Seat("A" + i, 1, i, i <= 5 ? SeatType.REGULAR : SeatType.PREMIUM));
            screen1.addSeat(new Seat("B" + i, 2, i, i <= 5 ? SeatType.REGULAR : SeatType.PREMIUM));
        }

        // Add Cinemas
        Cinema amcNYC = service.addCinema("cinema1", "AMC Times Square", nyc.getId(), List.of(screen1));

        // Per-show price table (prices are data, not baked into the SeatType enum)
        Map<SeatType, Double> seatPrices = Map.of(
                SeatType.REGULAR, 50.0,
                SeatType.PREMIUM, 80.0,
                SeatType.RECLINER, 120.0
        );

        // Add Shows
        Show matrixShow = service.addShow("show1", matrix, screen1, LocalDateTime.now().plusHours(2), seatPrices, new WeekdayPricingStrategy());
        Show avengersShow = service.addShow("show2", avengers, screen1, LocalDateTime.now().plusHours(5), seatPrices, new WeekdayPricingStrategy());

        // --- User Setup ---
        Customer alice = service.createUser("Alice", "alice@example.com");

        // --- User Story: Alice books tickets ---
        System.out.println("--- Alice's Booking Flow ---");
        String cityName = "New York";
        String movieTitle = "Avengers: Endgame";

        // 1. Search for shows
        List<Show> availableShows = service.findShows(movieTitle, cityName);
        if (availableShows.isEmpty()) {
            System.out.println("No shows found for " + movieTitle + " in " + cityName);
            return;
        }
        Show selectedShow = availableShows.get(0); // Alice selects the first show

        // 2. View available seats
        List<Seat> availableSeats = selectedShow.getScreen().getSeats().stream()
                .filter(seat -> seat.getStatus() == SeatStatus.AVAILABLE)
                .collect(Collectors.toList());
        System.out.printf("Available seats for '%s' at %s: %s%n",
                selectedShow.getMovie().getTitle(),
                selectedShow.getStartTime(),
                availableSeats.stream().map(Seat::getId).collect(Collectors.toList()));

        // 3. Select seats
        List<Seat> desiredSeats = List.of(availableSeats.get(2), availableSeats.get(3));
        System.out.println("Alice selects seats: " + desiredSeats.stream().map(Seat::getId).collect(Collectors.toList()));

        // 4. Book Tickets
        Optional<Booking> bookingOpt = service.bookTickets(
                alice.getId(),
                selectedShow.getId(),
                desiredSeats,
                new CreditCardPaymentStrategy("1234-5678-9876-5432", "123")
        );

        if (bookingOpt.isPresent()) {
            Booking booking = bookingOpt.get();
            System.out.println("\n--- Booking Successful! ---");
            System.out.println("Booking ID: " + booking.getId());
            System.out.println("User: " + booking.getUser().getName());
            System.out.println("Movie: " + booking.getShow().getMovie().getTitle());
            System.out.println("Seats: " + booking.getSeats().stream().map(Seat::getId).collect(Collectors.toList()));
            System.out.println("Total Amount: $" + booking.getTotalAmount());
            System.out.println("Payment Status: " + booking.getPayment().getStatus());
        } else {
            System.out.println("Booking failed.");
        }

        // 5. Verify seat status after booking
        System.out.println("\nSeat status after Alice's booking:");
        desiredSeats.forEach(seat -> System.out.printf("Seat %s status: %s%n", seat.getId(), seat.getStatus()));

        // 6. A second user tries to book the same seats. The lock manager must reject it.
        System.out.println("\n--- Bob tries to book the same seats ---");
        Customer bob = service.createUser("Bob", "bob@example.com");
        Optional<Booking> bobBooking = service.bookTickets(
                bob.getId(),
                selectedShow.getId(),
                desiredSeats,
                new CreditCardPaymentStrategy("9999-8888-7777-6666", "456")
        );
        System.out.println(bobBooking.isPresent()
                ? "Bob's booking succeeded (this should not happen)."
                : "Bob's booking was correctly rejected: those seats are already taken.");

        // 7. Shut down the system to release resources like the scheduler.
        service.shutdown();
    }
}
