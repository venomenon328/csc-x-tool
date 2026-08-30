package de.venomenon.cscxtool.search;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
class SearchController {

    private final SearchService searchService;

    SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    List<SearchResult> search(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long contestId) {
        return searchService.search(q, contestId);
    }
}
