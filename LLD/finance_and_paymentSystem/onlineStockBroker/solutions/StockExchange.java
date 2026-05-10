package LLD.finance_and_paymentSystem.onlineStockBroker.solutions;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.enums.OrderStatus;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.enums.OrderType;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Order;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.models.Stock;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.state.CancelledState;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.state.FilledState;
import LLD.finance_and_paymentSystem.onlineStockBroker.solutions.state.PartiallyFilledState;

/**
 * ================================================================
 * StockExchange — The Optimized Matching Engine
 * ================================================================
 *
 * Optimizations over original:
 *
 * 1. PriorityQueue (heap) instead of CopyOnWriteArrayList + O(n) scan
 * Original: O(n) stream scan per match iteration
 * Optimized: O(log n) offer/poll on a heap
 * Impact: For 10,000 open orders, ~10,000 ops vs ~14 ops per match.
 *
 * 2. Per-symbol ReentrantLock instead of synchronized(this)
 * Original: One global lock blocks all stocks during any match
 * Optimized: INFY matching and TCS matching run in parallel
 * Impact: Throughput scales linearly with number of distinct stocks.
 *
 * 3. Fixed MARKET order handling via getEffectivePrice()
 * Original: Used stock.getPrice() for MARKET orders — broke PQ ordering
 * Optimized: Sentinel values (MAX_VALUE / 0.0) ensure correct PQ position
 *
 * 4. Partial fill support
 * Original: Always set status = FILLED regardless of quantity mismatch
 * Optimized: remainingQuantity tracked; order stays in book until fully filled
 *
 * 5. Pre-validation before order placement
 * Original: No checks — debit/removeStock could fail mid-trade
 * Optimized: Validate balance (buyer) and stock holdings (seller) upfront
 *
 * 6. Correct trade price determination (4 cases)
 * Original: Always used sell price — wrong for MARKET SELL vs LIMIT BUY
 * Optimized: 4-case logic covers all combinations correctly
 */
public class StockExchange {

    // ---- Singleton ----
    private static volatile StockExchange instance;

    /**
     * WHY PriorityQueue?
     *
     * Buy Book = MAX-HEAP: highest willing buyer always at the top.
     * Sell Book = MIN-HEAP: lowest asking seller always at the top.
     *
     * peek() → O(1) to see best order.
     * poll() → O(log n) to remove best order after it fills.
     * offer() → O(log n) to insert a new order.
     *
     * Original used CopyOnWriteArrayList + stream().max()/.min() = O(n) every
     * iteration.
     */
    private final Map<String, PriorityQueue<Order>> buyOrderBook;
    private final Map<String, PriorityQueue<Order>> sellOrderBook;

    /**
     * WHY per-symbol locks?
     *
     * Original: synchronized(this) = ONE lock for the entire exchange.
     * Thread A matching INFY and Thread B matching TCS would serialize, blocking
     * each other
     * despite having zero shared state between them.
     *
     * Optimized: Each symbol gets its own ReentrantLock.
     * Thread A locks "INFY", Thread B locks "TCS" — they run in parallel.
     *
     * ConcurrentHashMap.computeIfAbsent() is atomic, so creating locks is
     * thread-safe.
     */
    private final Map<String, ReentrantLock> symbolLocks;

    private StockExchange() {
        this.buyOrderBook = new ConcurrentHashMap<>();
        this.sellOrderBook = new ConcurrentHashMap<>();
        this.symbolLocks = new ConcurrentHashMap<>();
    }

    /**
     * Double-Checked Locking Singleton.
     *
     * First if(null): avoids acquiring the class-level lock on every call (fast
     * path).
     * synchronized block: only one thread creates the instance.
     * Second if(null): guards against two threads both passing the first check.
     * volatile: prevents the JVM from returning a partially constructed object
     * due to instruction reordering (write to reference before constructor
     * finishes).
     */
    public static StockExchange getInstance() {
        if (instance == null) {
            synchronized (StockExchange.class) {
                if (instance == null) {
                    instance = new StockExchange();
                }
            }
        }
        return instance;
    }

    // ================================================================
    // Order Book Accessors (lazy initialization per symbol)
    // ================================================================

    private PriorityQueue<Order> getBuyBook(String symbol) {
        return buyOrderBook.computeIfAbsent(symbol, k ->
        /**
         * MAX-HEAP by effective price.
         * Tie-break: earlier timestamp wins (Price-Time Priority = real exchange
         * behavior).
         *
         * getEffectivePrice() returns Double.MAX_VALUE for MARKET BUY orders,
         * so they always sit at position 0 and get matched before any LIMIT buy.
         */
        new PriorityQueue<>(
                Comparator.comparingDouble(Order::getEffectivePrice).reversed()
                        .thenComparingLong(Order::getTimestamp)));
    }

    private PriorityQueue<Order> getSellBook(String symbol) {
        return sellOrderBook.computeIfAbsent(symbol, k ->
        /**
         * MIN-HEAP by effective price.
         * Tie-break: earlier timestamp wins.
         *
         * getEffectivePrice() returns 0.0 for MARKET SELL orders,
         * so they always sit at position 0 and match before any LIMIT sell.
         */
        new PriorityQueue<>(
                Comparator.comparingDouble(Order::getEffectivePrice)
                        .thenComparingLong(Order::getTimestamp)));
    }

    private ReentrantLock getLockForSymbol(String symbol) {
        // computeIfAbsent on ConcurrentHashMap is atomic — safe to call from multiple
        // threads
        return symbolLocks.computeIfAbsent(symbol, k -> new ReentrantLock());
    }

    // ================================================================
    // Order Placement
    // ================================================================

    public void placeBuyOrder(Order order) {
        String symbol = order.getStock().getSymbol();

        /**
         * PRE-VALIDATION (FIX over original):
         * For LIMIT buy: check if buyer can afford worst-case cost.
         * For MARKET buy: can't know price yet, so we skip (production system would
         * hold margin).
         *
         * WHY validate here and not just in executeTrade?
         * Failing early is cheaper — we avoid adding a bad order to the book
         * and potentially triggering a partial match that then needs rollback.
         */
        if (order.getType() == OrderType.LIMIT) {
            double maxCost = order.getLimitPrice() * order.getRemainingQuantity();
            if (!order.getUser().getAccount().hasSufficientBalance(maxCost)) {
                System.out.printf("BUY ORDER REJECTED [%s] — %s needs ₹%.2f but has ₹%.2f%n",
                        order.getOrderId().substring(0, 8),
                        order.getUser().getName(), maxCost,
                        order.getUser().getAccount().getBalance());
                rejectOrder(order);
                return;
            }
        }

        // Acquire the per-symbol lock — only this stock is locked
        ReentrantLock lock = getLockForSymbol(symbol);
        lock.lock();
        try {
            getBuyBook(symbol).offer(order); // O(log n) heap insert
            System.out.println("Buy order added  : " + order);
            matchOrders(symbol, order.getStock());
        } finally {
            lock.unlock(); // ALWAYS in finally — prevents deadlock if matchOrders throws
        }
    }

    public void placeSellOrder(Order order) {
        String symbol = order.getStock().getSymbol();

        /**
         * PRE-VALIDATION (FIX over original):
         * Seller must hold at least as many shares as they want to sell.
         * Prevents "naked short selling" (simplified — real systems have margin
         * accounts).
         */
        if (!order.getUser().getAccount().hasSufficientStock(symbol, order.getRemainingQuantity())) {
            System.out.printf("SELL ORDER REJECTED [%s] — %s has %d shares of %s, needs %d%n",
                    order.getOrderId().substring(0, 8),
                    order.getUser().getName(),
                    order.getUser().getAccount().getStockQuantity(symbol),
                    symbol, order.getRemainingQuantity());
            rejectOrder(order);
            return;
        }

        ReentrantLock lock = getLockForSymbol(symbol);
        lock.lock();
        try {
            getSellBook(symbol).offer(order); // O(log n) heap insert
            System.out.println("Sell order added : " + order);
            matchOrders(symbol, order.getStock());
        } finally {
            lock.unlock();
        }
    }

    // ================================================================
    // Matching Engine
    // ================================================================

    /**
     * Core matching loop. Runs while compatible orders exist in both books.
     *
     * ALWAYS called with the per-symbol lock held by the caller.
     * So no additional synchronization is needed here — we own this symbol
     * exclusively.
     *
     * Loop invariant: after each iteration, either:
     * (a) one or both sides of the book are empty, OR
     * (b) bestBuy.effectivePrice < bestSell.effectivePrice (no more matches)
     */
    private void matchOrders(String symbol, Stock stock) {
        PriorityQueue<Order> buyBook = getBuyBook(symbol);
        PriorityQueue<Order> sellBook = getSellBook(symbol);

        while (!buyBook.isEmpty() && !sellBook.isEmpty()) {

            // Drain cancelled orders from tops of heaps before peeking.
            // Orders can be cancelled externally (e.g., user cancels while in book).
            // Lazy removal avoids O(n) book scan — we only clean when we encounter them.
            drainCancelled(buyBook);
            drainCancelled(sellBook);

            if (buyBook.isEmpty() || sellBook.isEmpty())
                break;

            Order bestBuy = buyBook.peek();
            Order bestSell = sellBook.peek();

            double buyPrice = bestBuy.getEffectivePrice(); // Double.MAX_VALUE if MARKET BUY
            double sellPrice = bestSell.getEffectivePrice(); // 0.0 if MARKET SELL

            /**
             * MATCH CONDITION: buyPrice >= sellPrice
             *
             * Natural language: "The best buyer is willing to pay at least
             * as much as the best seller is asking."
             *
             * With effective prices, this covers all 4 cases:
             * ┌─────────────┬─────────────┬──────────────────────────────────────┐
             * │ Buy Side │ Sell Side │ Result │
             * ├─────────────┼─────────────┼──────────────────────────────────────┤
             * │ MARKET │ LIMIT │ MAX_VALUE >= limitPrice → always match│
             * │ LIMIT │ MARKET │ limitPrice >= 0.0 → always match │
             * │ MARKET │ MARKET │ MAX_VALUE >= 0.0 → always match │
             * │ LIMIT │ LIMIT │ buyLimit >= sellLimit → match if true │
             * └─────────────┴─────────────┴──────────────────────────────────────┘
             */
            if (buyPrice >= sellPrice) {
                double tradePrice = determineTradePrice(bestBuy, bestSell, stock);
                executeTrade(bestBuy, bestSell, tradePrice, stock);
            } else {
                // The best possible match fails — no point checking further
                break;
            }
        }
    }

    /**
     * TRADE PRICE DETERMINATION (FIX over original):
     *
     * Original always used sellPrice — wrong for MARKET SELL vs LIMIT BUY.
     * A market sell order has no limit price (it's 0.0), so using sell price
     * would execute the trade at ₹0, which is clearly wrong.
     *
     * Correct rule: the "resting" (passive) order sets the price.
     * The "aggressive" (newly arrived) order takes the available price.
     *
     * In most cases:
     * LIMIT orders rest in the book.
     * MARKET orders aggressively take whatever price is available.
     *
     * ┌─────────────┬─────────────┬────────────────────────────────────────────┐
     * │ Buy Type │ Sell Type │ Trade Price │
     * ├─────────────┼─────────────┼────────────────────────────────────────────┤
     * │ LIMIT │ LIMIT │ Sell's limit (convention: sell sets floor) │
     * │ MARKET │ LIMIT │ Sell's limit (market buy takes sell's price)│
     * │ LIMIT │ MARKET │ Buy's limit (market sell takes buy's price) │
     * │ MARKET │ MARKET │ Stock's LTP (no anchor price exists) │
     * └─────────────┴─────────────┴────────────────────────────────────────────┘
     */
    private double determineTradePrice(Order buyOrder, Order sellOrder, Stock stock) {
        boolean buyIsMarket = buyOrder.getType() == OrderType.MARKET;
        boolean sellIsMarket = sellOrder.getType() == OrderType.MARKET;

        if (buyIsMarket && sellIsMarket) {
            return stock.getPrice(); // Both market → use LTP as reference
        } else if (buyIsMarket) {
            return sellOrder.getLimitPrice(); // Market buy takes whatever seller is asking
        } else if (sellIsMarket) {
            return buyOrder.getLimitPrice(); // Market sell takes whatever buyer is offering
        } else {
            return sellOrder.getLimitPrice(); // Both LIMIT → trade at sell price (passive order)
        }
    }

    // ================================================================
    // Trade Execution
    // ================================================================

    /**
     * Executes a matched trade between a buy and sell order.
     *
     * PARTIAL FILL LOGIC (FIX over original):
     * Trade quantity = min(buyRemaining, sellRemaining).
     * Only the FULLY FILLED side is removed from the book.
     * The PARTIALLY FILLED side stays in the book for future matching.
     *
     * Example — Buy: 100 shares, Sell: 60 shares:
     * Step 1: tradeQty = min(100, 60) = 60
     * Step 2: Buy → 40 remaining (PARTIALLY_FILLED), stays in book
     * Sell → 0 remaining (FILLED), polled from book
     * Step 3: Next loop iteration: buy's 40 shares may match a new sell order
     */
    private void executeTrade(Order buyOrder, Order sellOrder, double tradePrice, Stock stock) {
        // Trade quantity = as many shares as the smaller side can provide
        int tradeQty = Math.min(buyOrder.getRemainingQuantity(), sellOrder.getRemainingQuantity());
        double totalCost = (double) tradeQty * tradePrice;

        System.out.printf("%n  ===== TRADE EXECUTING =====%n");
        System.out.printf("  Stock: %s | Qty: %d | Price: ₹%.2f | Total: ₹%.2f%n",
                stock.getSymbol(), tradeQty, tradePrice, totalCost);
        System.out.printf("  Buyer : %s | Seller: %s%n",
                buyOrder.getUser().getName(), sellOrder.getUser().getName());

        // ---- Debit buyer's cash ----
        // Returns false if balance is insufficient — shouldn't happen due to
        // pre-validation,
        // but race conditions or MARKET orders (not pre-validated) could cause this.
        if (!buyOrder.getUser().getAccount().debit(totalCost)) {
            System.out.printf("  TRADE ABORTED — Buyer %s insufficient funds.%n",
                    buyOrder.getUser().getName());
            rejectOrder(buyOrder);
            getBuyBook(stock.getSymbol()).poll(); // Remove bad order from book
            return;
        }

        // ---- Remove stock from seller's portfolio ----
        // Defensive check even after pre-validation (state may change between validate
        // and execute).
        if (!sellOrder.getUser().getAccount().removeStock(stock.getSymbol(), tradeQty)) {
            System.out.printf("  TRADE ABORTED — Seller %s insufficient stock. Rolling back.%n",
                    sellOrder.getUser().getName());
            // Rollback the buyer debit to maintain consistency
            buyOrder.getUser().getAccount().credit(totalCost);
            rejectOrder(sellOrder);
            getSellBook(stock.getSymbol()).poll(); // Remove bad order
            return;
        }

        // ---- Transfer: cash to seller, stock to buyer ----
        sellOrder.getUser().getAccount().credit(totalCost);
        buyOrder.getUser().getAccount().addStock(stock.getSymbol(), tradeQty);

        // ---- Update order quantities and statuses ----
        // fill() decrements remainingQuantity and sets FILLED or PARTIALLY_FILLED
        buyOrder.fill(tradeQty);
        sellOrder.fill(tradeQty);
        updateOrderState(buyOrder);
        updateOrderState(sellOrder);

        // ---- Remove FULLY FILLED orders from the book ----
        // PARTIALLY FILLED orders stay in the heap — they'll continue matching next
        // iteration.
        // poll() is O(log n) — much cheaper than the O(n) remove() in the original.
        if (buyOrder.getStatus() == OrderStatus.FILLED) {
            getBuyBook(stock.getSymbol()).poll();
        }
        if (sellOrder.getStatus() == OrderStatus.FILLED) {
            getSellBook(stock.getSymbol()).poll();
        }

        // Update LTP (Last Traded Price) — affects MARKET order fallback and UI display
        stock.setPrice(tradePrice);

        // ---- Print results ----
        System.out.printf("  Buy  order : %s%n", buyOrder);
        System.out.printf("  Sell order : %s%n", sellOrder);
        System.out.printf("  %s account : %s%n", buyOrder.getUser().getName(),
                buyOrder.getUser().getAccount());
        System.out.printf("  %s account : %s%n", sellOrder.getUser().getName(),
                sellOrder.getUser().getAccount());
        System.out.printf("  ============================%n%n");
    }

    // ================================================================
    // Cancel Order (Public API)
    // ================================================================

    /**
     * Cancel an open or partially filled order.
     *
     * The order's status is immediately set to CANCELLED.
     * Physical removal from the PriorityQueue happens lazily in drainCancelled()
     * the next time matchOrders runs for this symbol.
     *
     * WHY lazy removal?
     * PriorityQueue.remove(element) is O(n). Marking cancelled + draining at the
     * top
     * is O(1) now + O(log n) when naturally encountered. Better amortized cost.
     */
    public void cancelOrder(Order order) {
        ReentrantLock lock = getLockForSymbol(order.getStock().getSymbol());
        lock.lock();
        try {
            // Delegates to State Pattern: FilledState.cancel() will refuse;
            // OpenState.cancel() will proceed
            order.getState().cancel(order);
        } finally {
            lock.unlock();
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * Remove cancelled/filled orders from the top of a heap.
     * We only peek the top — if it's not cancelled, we stop.
     * This is lazy cleanup that keeps the hot path O(1).
     */
    private void drainCancelled(PriorityQueue<Order> book) {
        while (!book.isEmpty()) {
            OrderStatus top = book.peek().getStatus();
            if (top == OrderStatus.CANCELLED || top == OrderStatus.FILLED) {
                book.poll(); // Remove stale top-of-book entry
            } else {
                break; // Top is OPEN or PARTIALLY_FILLED — stop draining
            }
        }
    }

    private void rejectOrder(Order order) {
        order.setStatus(OrderStatus.CANCELLED);
        order.setState(new CancelledState());
    }

    /**
     * Sync the State Pattern object with the updated OrderStatus.
     * Called after every fill() so the state object reflects reality.
     */
    private void updateOrderState(Order order) {
        switch (order.getStatus()) {
            case FILLED -> order.setState(new FilledState());
            case PARTIALLY_FILLED -> order.setState(new PartiallyFilledState());
            case CANCELLED -> order.setState(new CancelledState());
            default -> {
            } // OPEN — state object already set correctly
        }
    }
}