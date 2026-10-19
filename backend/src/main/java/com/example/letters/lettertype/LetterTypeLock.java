package com.example.letters.lettertype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per letter type. Generate takes a pessimistic write lock on the row for its type
 * ({@link LetterTypeLockRepository#findWithLockByLetterType}), which serialises generation per type.
 */
@Entity
@Table(name = "letter_type_lock")
@Getter
@Setter
@NoArgsConstructor
public class LetterTypeLock {

    @Id
    @Column(name = "letter_type")
    private String letterType;
}
