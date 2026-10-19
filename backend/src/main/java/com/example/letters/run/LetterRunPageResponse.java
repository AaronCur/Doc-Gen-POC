package com.example.letters.run;

import java.util.List;

public record LetterRunPageResponse(
        List<LetterRunResponse> items,
        long total
) { }
