package com.example.letters.lettertype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.letters.eligibility.ApplicationLetterEligibilityRepository;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class LetterTypeServiceTest {

    @Mock LetterTypeRepository letterTypeRepository;
    @Mock ApplicationLetterEligibilityRepository eligibilityRepository;

    @InjectMocks LetterTypeService letterTypeService;

    @Test
    void mapsLetterTypesToResponses() {
        when(letterTypeRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(new LetterType("LETTER_A", "Letter type A")));

        assertThat(letterTypeService.getAllLetterTypes())
                .containsExactly(new LetterTypeResponse("LETTER_A", "Letter type A"));
    }

    @Test
    void returnsTheEligibleCountForAKnownType() {
        when(letterTypeRepository.findById("LETTER_A")).thenReturn(Optional.of(new LetterType("LETTER_A", "Letter type A")));
        when(eligibilityRepository.countEligible("LETTER_A")).thenReturn(18L);

        assertThat(letterTypeService.getEligibleCount("LETTER_A")).isEqualTo(new EligibleCountResponse("LETTER_A", 18));
    }

    @Test
    void unknownTypeIsNotFoundAndNothingIsCounted() {
        when(letterTypeRepository.findById("LETTER_Z")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> letterTypeService.getEligibleCount("LETTER_Z"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Letter type not found with code: LETTER_Z");
        verify(eligibilityRepository, never()).countEligible(anyString());
    }
}
