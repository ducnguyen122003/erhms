package com.erhms.user.entity;

import com.erhms.role.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "m_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "m_user_generator")
    @SequenceGenerator(name = "m_user_generator", sequenceName = "m_user_seq", allocationSize = 1)
    @Column(name = "user_id")
    public Long id;

    @Column(nullable = false, unique = true, length = 190)
    public String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    public String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    public String fullName;

    @Column(length = 20)
    public String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false, foreignKey = @ForeignKey(name = "fk_m_user_role"))
    public Role role;

    public User() {
    }

    public Long getId() {
        return id;
    }

    public String getRoleCode() {
        return role.getCode();
    }

    public Long getRoleId() {
        return role.getId();
    }

    public boolean hasRole(String code) {
        return role != null && code.equals(role.getCode());
    }
}
