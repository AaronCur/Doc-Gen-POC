package com.example.letters.lettertype;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LetterTypeRepository extends JpaRepository<LetterType, String> {

    List<LetterType> findAllByOrderByCodeAsc();
}
