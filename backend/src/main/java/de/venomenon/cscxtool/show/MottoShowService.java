package de.venomenon.cscxtool.show;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
class MottoShowService {

    private final MottoShowRepository repository;

    MottoShowService(MottoShowRepository repository) {
        this.repository = repository;
    }

    List<MottoShow> findAll() {
        return repository.findAll();
    }

    MottoShow rename(long showId, String name) {
        String normalizedName = name.trim();
        if (!repository.rename(showId, normalizedName)) {
            throw new ShowNotFoundException(showId);
        }
        return repository.findById(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }
}
