package com.example.letters.lettertype;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.letters.eligibility.ApplicationLetterEligibilityRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
public class LetterTypeService {

    private final LetterTypeRepository letterTypeRepository;
    private final ApplicationLetterEligibilityRepository eligibilityRepository;

    public LetterTypeService(LetterTypeRepository letterTypeRepository,
            ApplicationLetterEligibilityRepository eligibilityRepository) {
        this.letterTypeRepository = letterTypeRepository;
        this.eligibilityRepository = eligibilityRepository;
    }

    public List<LetterTypeResponse> getAllLetterTypes() {
        return letterTypeRepository.findAllByOrderByCodeAsc().stream().map(this::mapToResponse).toList();
    }

    /** How many applications a run would pick up now; the same rule the generate flow uses. */
    public EligibleCountResponse getEligibleCount(String code) {
        LetterType letterType = letterTypeRepository.findById(code)
                .orElseThrow(() -> new EntityNotFoundException("Letter type not found with code: " + code));
        return new EligibleCountResponse(letterType.getCode(), eligibilityRepository.countEligible(code));
    }

    private LetterTypeResponse mapToResponse(LetterType letterType) {
        return new LetterTypeResponse(letterType.getCode(), letterType.getName());
    }
}
