package com.example.letters.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.letters.support.IntegrationTest;

/** A failure at the last write (the shared queue) must roll back the run and its items too. */
class GenerateAtomicityTest extends IntegrationTest {

    @MockitoSpyBean
    LetterRequestQueueRepository queue;

    @Autowired
    LetterRunService service;

    @Test
    void failureWritingTheQueueRollsBackTheRunAndItems() {
        data.eligibleApplication("APP-1", "LETTER_A");
        doThrow(new DataAccessResourceFailureException("simulated"))
                .when(queue).insertPendingForRun(anyLong());

        assertThatThrownBy(() -> service.generateLetters(
                        new LetterRunRequest("LETTER_A", LocalDate.of(2026, 2, 1)), "key-1", "admin"))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(count("SELECT COUNT(*) FROM letter_run")).isZero();
        assertThat(count("SELECT COUNT(*) FROM letter_run_item")).isZero();
        assertThat(count("SELECT COUNT(*) FROM letter_run_audit")).isZero();
        assertThat(count("SELECT COUNT(*) FROM letter_request_queue")).isZero();
    }
}
