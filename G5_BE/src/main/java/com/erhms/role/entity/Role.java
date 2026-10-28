package com.erhms.role.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "m_role")
public class Role {
    public static final String ADMIN = "ADMIN";
    public static final String LECTURER = "LECTURER";
    public static final String STUDENT = "STUDENT";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String IT_SUPPORT = "IT_SUPPORT";
    public static final String MANAGER = "MANAGER";
    public static final String HR = "HR";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "m_role_generator")
    @SequenceGenerator(name = "m_role_generator", sequenceName = "m_role_seq", allocationSize = 1)
    @Column(name = "role_id")
    public Long id;

    @Column(nullable = false, unique = true, length = 40)
    public String code;

    @Column(nullable = false, length = 100)
    public String name;

    protected Role() {
    }

    public Role(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }
}
