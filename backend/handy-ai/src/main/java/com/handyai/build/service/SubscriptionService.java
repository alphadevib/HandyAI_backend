package com.handyai.build.service;

import com.handyai.build.domain.AiTool;
import com.handyai.build.domain.PriceBook;
import com.handyai.build.domain.PriceBook.BillingCycle;
import com.handyai.build.domain.PriceBook.Currency;
import com.handyai.build.domain.PriceBook.Money;
import com.handyai.build.domain.PriceBook.Plans;
import com.handyai.build.domain.Subscription;
import com.handyai.build.domain.User;
import com.handyai.build.dto.SubscriptionRequest;
import com.handyai.build.dto.SubscriptionResponse;
import com.handyai.build.dto.SubscriptionResponse.Overview;
import com.handyai.build.dto.ToolResponse;
import com.handyai.build.exception.BadRequestException;
import com.handyai.build.exception.ConflictException;
import com.handyai.build.exception.ResourceNotFoundException;
import com.handyai.build.repository.AiToolRepository;
import com.handyai.build.repository.SubscriptionRepository;
import com.handyai.build.repository.UserRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user's own ledger of AI plans: what they pay, how often, and when it renews next. */
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final AiToolRepository toolRepository;
    private final UserRepository userRepository;
    private final ToolService toolService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository,
                               AiToolRepository toolRepository, UserRepository userRepository,
                               ToolService toolService) {
        this.subscriptionRepository = subscriptionRepository;
        this.toolRepository = toolRepository;
        this.userRepository = userRepository;
        this.toolService = toolService;
    }

    @Transactional(readOnly = true)
    public Overview overview(Long userId) {
        LocalDate today = LocalDate.now();
        Set<Long> favorites = toolService.favoriteIds(userId);
        List<SubscriptionResponse> items = subscriptionRepository
                .findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(subscription -> toResponse(subscription, today,
                        favorites.contains(subscription.getTool().getId())))
                .toList();

        List<SubscriptionResponse> active = items.stream()
                .filter(item -> Subscription.Status.ACTIVE.name().equals(item.status()))
                .toList();
        double monthlyUsd = active.stream().mapToDouble(item -> item.monthlyEquivalent().usd()).sum();
        long monthlyInr = active.stream().mapToLong(item -> item.monthlyEquivalent().inr()).sum();
        SubscriptionResponse next = active.stream()
                .filter(item -> item.nextRenewal() != null)
                .min(Comparator.comparing(SubscriptionResponse::nextRenewal))
                .orElse(null);

        return new Overview(items, active.size(),
                new Money(PriceBook.roundUsd(monthlyUsd), monthlyInr),
                new Money(PriceBook.roundUsd(monthlyUsd * 12), monthlyInr * 12),
                next);
    }

    @Transactional
    public SubscriptionResponse add(Long userId, SubscriptionRequest request) {
        if (request.toolSlug() == null || request.toolSlug().isBlank()) {
            throw new BadRequestException("Choose the tool you subscribe to");
        }
        AiTool tool = toolRepository.findBySlug(request.toolSlug().trim())
                .orElseThrow(() -> ResourceNotFoundException.of("Tool", request.toolSlug()));
        if (subscriptionRepository.existsByUserIdAndToolId(userId, tool.getId())) {
            throw new ConflictException(tool.getName() + " is already in your subscriptions");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));

        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setTool(tool);
        subscription.setBillingCycle(parseCycle(request.billingCycle(), BillingCycle.MONTHLY));
        subscription.setCurrency(parseCurrency(request.currency(), Currency.INR));
        subscription.setStartDate(request.startDate() == null ? LocalDate.now() : request.startDate());
        subscription.setNotes(trimToNull(request.notes()));
        subscription.setAmount(request.amount() != null ? request.amount()
                : listPrice(tool, subscription.getBillingCycle(), subscription.getCurrency()));

        Subscription saved = subscriptionRepository.save(subscription);
        return toResponse(saved, LocalDate.now(),
                toolService.favoriteIds(userId).contains(tool.getId()));
    }

    /**
     * Changing the cycle or currency without a new amount re-prices the plan from the catalogue, so
     * switching monthly to annual does not leave a monthly figure on an annual plan.
     */
    @Transactional
    public SubscriptionResponse update(Long userId, Long id, SubscriptionRequest request) {
        Subscription subscription = load(userId, id);
        boolean repriced = false;
        if (request.billingCycle() != null) {
            BillingCycle cycle = parseCycle(request.billingCycle(), subscription.getBillingCycle());
            repriced |= cycle != subscription.getBillingCycle();
            subscription.setBillingCycle(cycle);
        }
        if (request.currency() != null) {
            Currency currency = parseCurrency(request.currency(), subscription.getCurrency());
            repriced |= currency != subscription.getCurrency();
            subscription.setCurrency(currency);
        }
        if (request.amount() != null) {
            subscription.setAmount(request.amount());
        } else if (repriced) {
            subscription.setAmount(listPrice(subscription.getTool(), subscription.getBillingCycle(),
                    subscription.getCurrency()));
        }
        if (request.startDate() != null) {
            subscription.setStartDate(request.startDate());
        }
        if (request.status() != null) {
            subscription.setStatus(parseStatus(request.status()));
        }
        if (request.notes() != null) {
            subscription.setNotes(trimToNull(request.notes()));
        }
        return toResponse(subscription, LocalDate.now(),
                toolService.favoriteIds(userId).contains(subscription.getTool().getId()));
    }

    @Transactional
    public void remove(Long userId, Long id) {
        subscriptionRepository.delete(load(userId, id));
    }

    /** Scoped by user, so an id belonging to another account reads as not found. */
    private Subscription load(Long userId, Long id) {
        return subscriptionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Subscription", id));
    }

    private double listPrice(AiTool tool, BillingCycle cycle, Currency currency) {
        Plans plans = Plans.of(tool);
        if (plans == null) {
            return 0;
        }
        Money money = plans.forCycle(cycle);
        return currency == Currency.INR ? money.inr() : money.usd();
    }

    private SubscriptionResponse toResponse(Subscription subscription, LocalDate today,
                                            boolean favorite) {
        double perMonth = subscription.getAmount() / subscription.getBillingCycle().months();
        Money monthly = subscription.getCurrency() == Currency.INR
                ? new Money(PriceBook.roundUsd(perMonth / PriceBook.USD_TO_INR), Math.round(perMonth))
                : Money.ofUsd(perMonth);
        return new SubscriptionResponse(
                subscription.getId(),
                ToolResponse.from(subscription.getTool(), favorite),
                subscription.getBillingCycle().name(),
                subscription.getCurrency().name(),
                subscription.getAmount(),
                subscription.getStartDate(),
                subscription.nextRenewal(today),
                subscription.getStatus().name(),
                subscription.getNotes(),
                monthly);
    }

    private BillingCycle parseCycle(String value, BillingCycle fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return BillingCycle.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Billing cycle must be MONTHLY, QUARTERLY or ANNUAL");
        }
    }

    private Currency parseCurrency(String value, Currency fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Currency.parse(value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Currency must be INR or USD");
        }
    }

    private Subscription.Status parseStatus(String value) {
        try {
            return Subscription.Status.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Status must be ACTIVE or CANCELLED");
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
