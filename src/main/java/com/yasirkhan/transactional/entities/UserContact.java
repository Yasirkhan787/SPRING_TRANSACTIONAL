package com.yasirkhan.transactional.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "user_contacts")
public class UserContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String phoneNumber;

    /**
     * Because UserContact represents the database table that physically contains the foreign key column,
     * you use @ManyToOne and @JoinColumn here.
     * </br>
     * @ManyToOne: Tells Hibernate that many contacts can belong to one user.
     *</br>
     * @JoinColumn(name = "user_id"): Tells Hibernate the exact physical name of the foreign key column in the user_contacts database table.
     * </br>
     * name = the foreign key column in THIS table (user_contacts). The actual database column name
     * </br>
     * referencedColumnName = the specific target column in the PARENT table (users)
     */
    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonIgnore
    private User user;
}
