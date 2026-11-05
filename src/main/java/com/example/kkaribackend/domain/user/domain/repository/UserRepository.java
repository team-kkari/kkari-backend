package com.example.kkaribackend.domain.user.domain.repository;

import com.example.kkaribackend.domain.user.domain.Role;
import com.example.kkaribackend.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndRole(Long id, Role role);

    List<User> findAllByRoleOrderByNameAscIdAsc(Role role);
}
