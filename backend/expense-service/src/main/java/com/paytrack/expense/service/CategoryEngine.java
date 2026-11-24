package com.paytrack.expense.service;

import com.paytrack.expense.entity.CategoryRule;
import com.paytrack.expense.repository.CategoryRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoryEngine {

    private final CategoryRuleRepository categoryRuleRepository;

    public static final Set<String> VALID_CATEGORIES = Set.of(
            "FOOD", "TRANSPORT", "HOUSING", "ENTERTAINMENT", "UTILITIES",
            "HEALTHCARE", "SHOPPING", "EDUCATION", "OTHER"
    );

    public record ResolvedCategory(String category, String categorySource) {}

    public ResolvedCategory resolveCategory(String requestedCategory, String note, String txnType) {
        // Priority 1: User selection
        if (requestedCategory != null && !requestedCategory.trim().isEmpty()) {
            String upper = requestedCategory.trim().toUpperCase();
            if (VALID_CATEGORIES.contains(upper)) {
                log.info("Resolved category via USER request: {}", upper);
                return new ResolvedCategory(upper, "USER");
            }
        }

        // Priority 2: Rule matching
        // Check TXN_TYPE first (e.g. RECHARGE -> UTILITIES)
        if ("RECHARGE".equalsIgnoreCase(txnType)) {
            log.info("Resolved category via TXN_TYPE RECHARGE: UTILITIES");
            return new ResolvedCategory("UTILITIES", "RULE");
        }

        // Check NOTE keyword rules
        if (note != null && !note.trim().isEmpty()) {
            String lowerNote = note.toLowerCase();
            List<CategoryRule> noteRules = categoryRuleRepository.findByMatchOnOrderByPriorityDesc("NOTE");
            for (CategoryRule rule : noteRules) {
                if (rule.getKeyword() != null && lowerNote.contains(rule.getKeyword().toLowerCase())) {
                    log.info("Resolved category via NOTE keyword '{}': {}", rule.getKeyword(), rule.getCategory());
                    return new ResolvedCategory(rule.getCategory(), "RULE");
                }
            }
        }

        // Priority 3: Fallback default
        log.info("Resolved category via DEFAULT fallback: OTHER");
        return new ResolvedCategory("OTHER", "DEFAULT");
    }
}
