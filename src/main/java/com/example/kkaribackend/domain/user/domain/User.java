package com.example.kkaribackend.domain.user.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
@Table(name = "tbl_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 320, nullable = false, unique = true)
    private String email;

    @Column(length = 255, nullable = false)
    private String password;

    @Column(length = 50, nullable = false)
    private String name;

    // 리포트 작성자 표시에 사용하는 소속 MSP 회사명
    @Column(length = 100, nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Role role;
}
