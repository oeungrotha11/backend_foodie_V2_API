package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.*;
import com.api.v1.bearerauth.model.enumerate.OrderStatus;
import com.api.v1.bearerauth.model.enumerate.RewardStatus;
import com.api.v1.bearerauth.model.request.RewardRuleRequest;
import com.api.v1.bearerauth.model.response.CustomerRewardResponse;
import com.api.v1.bearerauth.model.response.RewardRuleResponse;
import com.api.v1.bearerauth.repository.CustomerRepository;
import com.api.v1.bearerauth.repository.CustomerRewardRepository;
import com.api.v1.bearerauth.repository.RewardRuleRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RewardServiceImpl implements RewardService {

    private final RewardRuleRepository rewardRuleRepository;
    private final CustomerRewardRepository customerRewardRepository;
    private final CustomerRepository customerRepository;
    private final CurrentUserService currentUserService;

    @Override
    public List<RewardRuleResponse> listRules() {
        // The shop carousel is customer-facing, so return only rules that are currently active.
        return rewardRuleRepository.findAll().stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getActive()))
                .map(this::toRuleResponse)
                .toList();
    }

    @Override
    public RewardRuleResponse getRule(Long id) {
        return toRuleResponse(findRule(id));
    }

    @Override
    @Transactional
    public RewardRuleResponse createRule(RewardRuleRequest request) {
        validateRule(request);
        RewardRule rule = new RewardRule();
        applyRuleRequest(rule, request);
        rule.setActive(true);
        return toRuleResponse(rewardRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public RewardRuleResponse updateRule(Long id, RewardRuleRequest request) {
        validateRule(request);
        RewardRule rule = findRule(id);
        applyRuleRequest(rule, request);
        // Preserve current active state; use activate/deactivate endpoints to change it.
        return toRuleResponse(rewardRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public RewardRuleResponse activateRule(Long id) {
        RewardRule rule = findRule(id);
        rule.setActive(true);
        return toRuleResponse(rewardRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public RewardRuleResponse deactivateRule(Long id) {
        RewardRule rule = findRule(id);
        rule.setActive(false);
        return toRuleResponse(rewardRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public List<CustomerRewardResponse> getMyRewards() {
        Customer customer = currentCustomer();
        LocalDateTime now = LocalDateTime.now();
        List<CustomerReward> rewards = customerRewardRepository
                .findByCustomer_CustomerIdOrderByEarnedAtDesc(customer.getCustomerId());

        boolean changed = false;
        for (CustomerReward reward : rewards) {
            if (reward.getStatus() == RewardStatus.AVAILABLE
                    && reward.getExpiresAt() != null && !reward.getExpiresAt().isAfter(now)) {
                reward.setStatus(RewardStatus.EXPIRED);
                changed = true;
            }
        }
        if (changed) customerRewardRepository.saveAll(rewards);

        return rewards.stream().map(this::toCustomerRewardResponse).toList();
    }

    @Override
    public CustomerRewardResponse getRewardUsedForOrder(Long orderId) {
        if (orderId == null) return null;
        return customerRewardRepository.findFirstByRedemptionOrder_OrderId(orderId)
                .map(this::toCustomerRewardResponse).orElse(null);
    }

    @Override
    @Transactional
    public double reserveReward(Customer customer, Long rewardId, double foodSubtotal, OrderTb order) {
        if (rewardId == null) return 0.0;
        if (customer == null || customer.getCustomerId() == null || order == null || order.getOrderId() == null) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Order must be saved before reserving a reward");
        }
        if (!Double.isFinite(foodSubtotal) || foodSubtotal <= 0.0) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Food subtotal must be greater than zero to use a reward");
        }

        CustomerReward reward = customerRewardRepository
                .findByIdAndCustomerForUpdate(rewardId, customer.getCustomerId())
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Reward not found in your wallet"));

        if (reward.getStatus() != RewardStatus.AVAILABLE) {
            throw new CustomException(HttpStatus.CONFLICT, "This reward is not available to use");
        }
        LocalDateTime now = LocalDateTime.now();
        if (reward.getExpiresAt() == null || !reward.getExpiresAt().isAfter(now)) {
            // The wallet endpoint will persist EXPIRED status when the customer refreshes it.
            throw new CustomException(HttpStatus.BAD_REQUEST, "This reward has expired");
        }
        if (foodSubtotal < reward.getMinimumRedemptionSubtotal()) {
            throw new CustomException(HttpStatus.BAD_REQUEST,
                    "Food subtotal must be at least " + reward.getMinimumRedemptionSubtotal() + " to use this reward");
        }

        reward.setStatus(RewardStatus.RESERVED);
        reward.setRedemptionOrder(order);
        reward.setRedeemedAt(null);
        customerRewardRepository.save(reward);

        double discount = roundMoney(foodSubtotal * reward.getDiscountRate());
        // Avoid a percentage reward reducing food subtotal below zero.
        return Math.min(discount, roundMoney(foodSubtotal));
    }

    @Override
    @Transactional
    public void releaseReservedRewardForOrder(OrderTb order) {
        if (order == null || order.getOrderId() == null) return;
        LocalDateTime now = LocalDateTime.now();
        List<CustomerReward> attached = customerRewardRepository.findByRedemptionOrder_OrderId(order.getOrderId());
        for (CustomerReward reward : attached) {
            if (reward.getStatus() == RewardStatus.RESERVED) {
                reward.setRedemptionOrder(null);
                reward.setRedeemedAt(null);
                reward.setStatus(reward.getExpiresAt() != null && reward.getExpiresAt().isAfter(now)
                        ? RewardStatus.AVAILABLE : RewardStatus.EXPIRED);
                customerRewardRepository.save(reward);
            }
        }
    }

    @Override
    @Transactional
    public void handleOrderStatusChange(OrderTb order, OrderStatus previousStatus, OrderStatus newStatus) {
        if (order == null || order.getOrderId() == null || previousStatus == newStatus) return;

        if (newStatus == OrderStatus.cancelled) {
            releaseReservedRewardForOrder(order);
            return;
        }

        // This project currently uses `purchased` as the successful terminal order status.
        if (newStatus == OrderStatus.purchased && previousStatus != OrderStatus.purchased) {
            redeemReservedRewardForOrder(order);
            awardQualifyingRewards(order);
        }
    }

    private void redeemReservedRewardForOrder(OrderTb order) {
        List<CustomerReward> attached = customerRewardRepository.findByRedemptionOrder_OrderId(order.getOrderId());
        LocalDateTime now = LocalDateTime.now();
        for (CustomerReward reward : attached) {
            if (reward.getStatus() == RewardStatus.RESERVED) {
                reward.setStatus(RewardStatus.REDEEMED);
                reward.setRedeemedAt(now);
                customerRewardRepository.save(reward);
            }
        }
    }

    private void awardQualifyingRewards(OrderTb order) {
        double foodSubtotal = calculateFoodSubtotal(order);
        if (foodSubtotal <= 0 || order.getCustomer() == null || order.getCustomer().getCustomerId() == null) return;

        Long customerId = order.getCustomer().getCustomerId();
        // Award only the highest qualifying active rule for this order so one order
        // does not unexpectedly grant several rewards at once.
        RewardRule eligibleRule = rewardRuleRepository.findByActiveTrue().stream()
                .filter(rule -> foodSubtotal >= rule.getMinimumQualifyingSubtotal())
                .max(java.util.Comparator.comparing(RewardRule::getMinimumQualifyingSubtotal))
                .orElse(null);
        if (eligibleRule == null) return;

        boolean alreadyEarned = customerRewardRepository
                .existsByCustomer_CustomerIdAndQualifyingOrder_OrderIdAndRewardRule_RewardRuleId(
                        customerId, order.getOrderId(), eligibleRule.getRewardRuleId());
        if (alreadyEarned) return;

        LocalDateTime earnedAt = LocalDateTime.now();
        CustomerReward reward = new CustomerReward();
        reward.setCustomer(order.getCustomer());
        reward.setRewardRule(eligibleRule);
        reward.setQualifyingOrder(order);
        reward.setTitle(eligibleRule.getName());
        reward.setDiscountRate(eligibleRule.getDiscountRate());
        reward.setMinimumRedemptionSubtotal(eligibleRule.getMinimumRedemptionSubtotal());
        reward.setStatus(RewardStatus.AVAILABLE);
        reward.setEarnedAt(earnedAt);
        reward.setExpiresAt(earnedAt.plusDays(eligibleRule.getValidityDays()));
        customerRewardRepository.save(reward);
    }

    private double calculateFoodSubtotal(OrderTb order) {
        if (order.getItems() == null) return 0.0;
        double subtotal = order.getItems().stream().mapToDouble(item -> {
            double addOns = item.getSelectedOptions() == null ? 0.0 : item.getSelectedOptions().stream()
                    .filter(option -> option.getOptionType() == com.api.v1.bearerauth.model.enumerate.OptionType.ADD_ON)
                    .mapToDouble(option -> option.getExtraPrice() == null ? 0.0 : option.getExtraPrice())
                    .sum();
            return (item.getUnitPrice() + addOns) * item.getQuantity();
        }).sum();
        return roundMoney(subtotal);
    }

    private Customer currentCustomer() {
        AppUser user = currentUserService.getCurrentUser();
        return customerRepository.findByUser(user)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Customer profile not found"));
    }

    private RewardRule findRule(Long id) {
        return rewardRuleRepository.findById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, "Reward rule not found"));
    }

    private void validateRule(RewardRuleRequest request) {
        if (request.getMinimumQualifyingSubtotal() == null || !Double.isFinite(request.getMinimumQualifyingSubtotal())
                || request.getMinimumQualifyingSubtotal() <= 0) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Minimum qualifying subtotal must be greater than zero");
        }
        if (request.getDiscountRate() == null || !Double.isFinite(request.getDiscountRate())
                || request.getDiscountRate() <= 0 || request.getDiscountRate() > 1) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Discount rate must be greater than zero and at most 1.00");
        }
        if (request.getMinimumRedemptionSubtotal() == null || !Double.isFinite(request.getMinimumRedemptionSubtotal())
                || request.getMinimumRedemptionSubtotal() < 0) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Minimum redemption subtotal cannot be negative");
        }
        if (request.getValidityDays() == null || request.getValidityDays() < 1 || request.getValidityDays() > 3650) {
            throw new CustomException(HttpStatus.BAD_REQUEST, "Validity must be between 1 and 3650 days");
        }
    }

    private void applyRuleRequest(RewardRule rule, RewardRuleRequest request) {
        rule.setName(request.getName().trim());
        rule.setDescription(request.getDescription());
        rule.setMinimumQualifyingSubtotal(roundMoney(request.getMinimumQualifyingSubtotal()));
        rule.setDiscountRate(request.getDiscountRate());
        rule.setMinimumRedemptionSubtotal(roundMoney(request.getMinimumRedemptionSubtotal()));
        rule.setValidityDays(request.getValidityDays());
    }

    private RewardRuleResponse toRuleResponse(RewardRule rule) {
        RewardRuleResponse response = new RewardRuleResponse();
        response.setRewardRuleId(rule.getRewardRuleId());
        response.setName(rule.getName());
        response.setDescription(rule.getDescription());
        response.setMinimumQualifyingSubtotal(rule.getMinimumQualifyingSubtotal());
        response.setDiscountRate(rule.getDiscountRate());
        response.setMinimumRedemptionSubtotal(rule.getMinimumRedemptionSubtotal());
        response.setValidityDays(rule.getValidityDays());
        response.setActive(rule.getActive());
        return response;
    }

    private CustomerRewardResponse toCustomerRewardResponse(CustomerReward reward) {
        CustomerRewardResponse response = new CustomerRewardResponse();
        response.setRewardId(reward.getCustomerRewardId());
        response.setRewardRuleId(reward.getRewardRule().getRewardRuleId());
        response.setQualifyingOrderId(reward.getQualifyingOrder().getOrderId());
        response.setRedemptionOrderId(reward.getRedemptionOrder() == null ? null : reward.getRedemptionOrder().getOrderId());
        response.setTitle(reward.getTitle());
        response.setDiscountRate(reward.getDiscountRate());
        response.setMinimumRedemptionSubtotal(reward.getMinimumRedemptionSubtotal());
        response.setStatus(reward.getStatus());
        response.setEarnedAt(reward.getEarnedAt());
        response.setExpiresAt(reward.getExpiresAt());
        response.setRedeemedAt(reward.getRedeemedAt());
        return response;
    }

    private double roundMoney(double amount) {
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
