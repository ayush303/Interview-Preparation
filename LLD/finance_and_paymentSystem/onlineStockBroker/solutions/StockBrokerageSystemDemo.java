package LLD.finance_and_paymentSystem.onlineStockBroker.solutions;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.builder.OrderBuilder;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.enums.OrderType;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Stock;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.User;

public class StockBrokerageSystemDemo {
        public static void main(String[] args) throws InterruptedException {
                // --- System Setup ---
                // ---- Get the system facade ----
                StockBrokerageSystem system = StockBrokerageSystem.getInstance();

                // ---- Register stocks ----
                system.listStock(new Stock("INFY", 1500.0));
                system.listStock(new Stock("TCS", 3500.0));

                // ---- Register users ----
                User alice = new User("Alice", 200_000.0);
                User bob = new User("Bob", 100_000.0);
                User charlie = new User("Charlie", 50_000.0);
                User poorGuy = new User("PoorGuy", 500.0);

                system.registerUser(alice);
                system.registerUser(bob);
                system.registerUser(charlie);
                system.registerUser(poorGuy);

                // Give sellers stock to sell
                bob.getAccount().addStock("INFY", 200);
                charlie.getAccount().addStock("INFY", 50);

                Stock infy = system.getStock("INFY");
                Stock tcs = system.getStock("TCS");

                // ================================================================
                System.out.println("\n=== TEST 1: Basic Limit Match ===");
                // ================================================================
                // Caller just calls placeBuyOrder — knows nothing about
                // OrderBuilder, BuyStockCommand, or OrderInvoker internally
                system.placeBuyOrder(alice, infy, 50, 1500.0, OrderType.LIMIT);
                system.placeSellOrder(bob, infy, 50, 1490.0, OrderType.LIMIT);
                // → Match at ₹1490

                // ================================================================
                System.out.println("\n=== TEST 2: Insufficient Funds (rejected) ===");
                // ================================================================
                system.placeBuyOrder(poorGuy, infy, 500, 1500.0, OrderType.LIMIT);
                // → InsufficientFundsException caught by invoker, logged, batch continues

                // ================================================================
                System.out.println("\n=== TEST 3: Partial Fill ===");
                // ================================================================
                system.placeBuyOrder(alice, infy, 100, 1500.0, OrderType.LIMIT); // wants 100
                system.placeSellOrder(charlie, infy, 50, 1495.0, OrderType.LIMIT); // only 50
                // → 50 filled, alice's buy has 50 remaining (PARTIALLY_FILLED)

                // ================================================================
                System.out.println("\n=== TEST 4: Cancel last order (undo) ===");
                // ================================================================
                // Alice places a low-ball that won't match
                Order lowBall = system.placeBuyOrder(alice, infy, 20, 1400.0, OrderType.LIMIT);
                System.out.println("Order status before cancel: " + lowBall.getStatus()); // OPEN

                system.cancelLastOrder(alice); // invoker.undoLast() → OpenState.cancel()
                System.out.println("Order status after cancel: " + lowBall.getStatus()); // CANCELLED

                // ================================================================
                System.out.println("\n=== TEST 5: Batch pre-market orders ===");
                // ================================================================
                // Queue multiple orders first, execute all at once
                bob.getAccount().addStock("INFY", 100); // give bob more stock
                system.queueBuyOrder(alice, infy, 30, 1510.0, OrderType.LIMIT);
                system.queueSellOrder(bob, infy, 30, 1505.0, OrderType.LIMIT);
                system.queueBuyOrder(alice, tcs, 5, 3500.0, OrderType.LIMIT);

                System.out.println(
                                "Pending for Alice: " + system.userInvokers.get(alice.getUserId()).getPendingCount()); // 2
                System.out.println("Pending for Bob:   " + system.userInvokers.get(bob.getUserId()).getPendingCount()); // 1

                system.executePendingOrders(alice); // fires Alice's queued orders
                system.executePendingOrders(bob); // fires Bob's queued orders

                // ================================================================
                System.out.println("\n=== TEST 6: Portfolios and Audit Logs ===");
                // ================================================================
                system.printPortfolio(alice);
                system.printPortfolio(bob);
                system.printAuditLog(alice);
                system.printAuditLog(bob);
        }
}
