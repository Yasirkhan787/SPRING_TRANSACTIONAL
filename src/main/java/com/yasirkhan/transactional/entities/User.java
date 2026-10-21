package com.yasirkhan.transactional.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private double balance;

    /**
         * The User table does not have a foreign key for contacts.
         * Therefore, the User entity is the "inverse" (non-owning) side.
         * We use @OneToMany and mappedBy here to link a List of contacts to the user.
         *</br>
         * @OneToMany: Tells Hibernate that one user has many contacts.
         * </br>
         * mappedBy = "user": This is the most misunderstood annotation.
         * It simply tells Hibernate: "I do not own this relationship and I don't have a foreign key column.
         * If you want to know how we are linked, go look at the property named user inside the UserContact class."
         * mappedBy points to the exact variable name in the UserContact class (private User user;)
         * </br>
         * CascadeType.ALL tells Hibernate: "If I save a User, save its contacts too."
         * </br>
         * orphanRemoval = true tells Hibernate: "If I remove a contact from this list, delete it from the DB."
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserContact> contacts;

//    public void addContact(UserContact contact) {
//        this.contacts.add(contact);
//        contact.setUser(this); // The entity handles the foreign key linkage itself
//    }
}
