// JPA entity mapping the "categories" table: a self-referencing tree via parent_id.
package com.iloveshopping.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    // A plain id rather than a @ManyToOne: the tree is small and always assembled in memory.
    @Column(name = "parent_id")
    private Integer parentId;

    @Column
    private Boolean active = true;
}
