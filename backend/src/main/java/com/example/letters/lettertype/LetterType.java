package com.example.letters.lettertype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Stand-in for the existing letter type reference data. The code is also the document type. */
@Entity
@Table(name = "letter_types")
@Getter
@Setter
@NoArgsConstructor
public class LetterType {

    @Id
    @Column(name = "letter_type")
    private String code;

    @Column(name = "display_name", nullable = false)
    private String name;

    public LetterType(String code, String name) {
        this.code = code;
        this.name = name;
    }
}
