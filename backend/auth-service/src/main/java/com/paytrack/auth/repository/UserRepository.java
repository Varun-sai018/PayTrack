package com.paytrack.auth.repository;

import com.paytrack.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByUserId(String userId);
    boolean existsByPhone(String phone);
    boolean existsByUpiId(String upiId);
    List<User> findByKycStatus(User.KycStatus kycStatus);
    List<User> findByAccountStatus(User.AccountStatus accountStatus);

    @Query("SELECT u FROM User u WHERE u.phone = :phone")
    Optional<User> findByPhone(@Param("phone") String phone);

    @Query("SELECT u FROM User u WHERE u.upiId = :upiId")
    Optional<User> findByUpiId(@Param("upiId") String upiId);
}
