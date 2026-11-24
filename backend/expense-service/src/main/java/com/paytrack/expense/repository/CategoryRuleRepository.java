package com.paytrack.expense.repository;

import com.paytrack.expense.entity.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRuleRepository extends JpaRepository<CategoryRule, String> {
    List<CategoryRule> findAllByOrderByPriorityDesc();
    List<CategoryRule> findByMatchOnOrderByPriorityDesc(String matchOn);
}
