package duyanh.film.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import duyanh.film.dto.MovieDto.CountryResponse;
import duyanh.film.dto.MovieDto.GenreResponse;
import duyanh.film.dto.MovieDto.PersonDetail;
import duyanh.film.dto.MovieDto.PersonSummary;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.service.CatalogService;
import duyanh.film.service.PersonService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Genre / Country / People")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {

	private final CatalogService catalogService;
	private final PersonService personService;

	@GetMapping("/genres")
	public List<GenreResponse> genres() {
		return catalogService.genres();
	}

	@GetMapping("/genres/{slug}")
	public GenreResponse genre(@PathVariable String slug) {
		return catalogService.genre(slug);
	}

	@GetMapping("/countries")
	public List<CountryResponse> countries() {
		return catalogService.countries();
	}

	@GetMapping("/people")
	public PageResponse<PersonSummary> people(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return personService.search(keyword,
				PageRequest.of(Math.max(page, 0), UserController.clamp(size), Sort.by("name")));
	}

	@GetMapping("/people/{slug}")
	public PersonDetail person(@PathVariable String slug) {
		return personService.detail(slug);
	}
}
