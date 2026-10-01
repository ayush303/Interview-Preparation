package LLD.movie_booking_system.solutions;

import LLD.movie_booking_system.solutions.enums.SeatType;
import LLD.movie_booking_system.solutions.models.*;
import LLD.movie_booking_system.solutions.strategy.payment.PaymentStrategy;
import LLD.movie_booking_system.solutions.strategy.pricing.PricingStrategy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MovieBookingService {
    private static volatile MovieBookingService instance;

    private final Map<String, City> cities;
    private final Map<String, Cinema> cinemas;
    private final Map<String, Movie> movies;
    private final Map<String, Customer> users;
    private final Map<String, Show> shows;
    private final Map<Screen, Cinema> screenToCinema;

    // Core services - managed by the system
    private final SeatLockManager seatLockManager;
    private final BookingManager bookingManager;

    private MovieBookingService() {
        this.cities = new ConcurrentHashMap<>();
        this.cinemas = new ConcurrentHashMap<>();
        this.movies = new ConcurrentHashMap<>();
        this.users = new ConcurrentHashMap<>();
        this.shows = new ConcurrentHashMap<>();
        this.screenToCinema = new ConcurrentHashMap<>();

        this.seatLockManager = new SeatLockManager();
        this.bookingManager = new BookingManager(seatLockManager);
    }

    public static MovieBookingService getInstance() {
        if (instance == null) {
            synchronized (MovieBookingService.class) {
                if (instance == null) {
                    instance = new MovieBookingService();
                }
            }
        }
        return instance;
    }

    // --- Data Management Methods ---
    public City addCity(String name) {
        City city = new City(name);
        cities.put(city.getId(), city);
        return city;
    }

    public Cinema addCinema(String id, String name, String cityId, List<Screen> screens) {
        City city = cities.get(cityId);
        Cinema cinema = new Cinema(id, name, city, screens);
        cinemas.put(cinema.getId(), cinema);
        for (Screen screen : screens) {
            screenToCinema.put(screen, cinema);
        }
        return cinema;
    }

    public void addMovie(Movie movie) {
        this.movies.put(movie.getId(), movie);
    }

    public Show addShow(String id, Movie movie, Screen screen, LocalDateTime startTime, Map<SeatType, Double> seatPrices, PricingStrategy pricingStrategy) {
        Show show = new Show(id, movie, screen, startTime, seatPrices, pricingStrategy);
        shows.put(show.getId(), show);
        return show;
    }

    public Customer createUser(String name, String email) {
        Customer user = new Customer(name, email);
        users.put(user.getId(), user);
        return user;
    }

    public Optional<Booking> bookTickets(String userId, String showId, List<Seat> desiredSeats, PaymentStrategy paymentStrategy) {
        return bookingManager.createBooking(
                users.get(userId),
                shows.get(showId),
                desiredSeats,
                paymentStrategy
        );
    }

    // --- Search Functionality ---
    public List<Show> findShows(String movieTitle, String cityName) {
        List<Show> result = new ArrayList<>();
        shows.values().stream()
                .filter(show -> show.getMovie().getTitle().equalsIgnoreCase(movieTitle))
                .filter(show -> {
                    Cinema cinema = findCinemaForShow(show);
                    return cinema != null && cinema.getCity().getName().equalsIgnoreCase(cityName);
                })
                .forEach(result::add);
        return result;
    }

    private Cinema findCinemaForShow(Show show) {
        // O(1) lookup: the screen-to-cinema index is built as cinemas are added.
        return screenToCinema.get(show.getScreen());
    }

    public void shutdown() {
        this.seatLockManager.shutdown();
        System.out.println("MovieTicketBookingSystem has been shut down.");
    }

}
