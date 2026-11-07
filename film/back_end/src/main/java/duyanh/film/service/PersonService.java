package duyanh.film.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import duyanh.film.dto.MovieDto.PersonCredit;
import duyanh.film.dto.MovieDto.PersonDetail;
import duyanh.film.dto.MovieDto.PersonRequest;
import duyanh.film.dto.MovieDto.PersonSummary;
import duyanh.film.dto.response.PageResponse;
import duyanh.film.exception.ApiException;
import duyanh.film.mapper.MovieMapper;
import duyanh.film.model.Person;
import duyanh.film.repository.MovieCreditRepository;
import duyanh.film.repository.PersonRepository;
import duyanh.film.util.SlugUtil;
import lombok.RequiredArgsConstructor;

/** Đạo diễn, diễn viên, biên kịch... */
@Service
@RequiredArgsConstructor
public class PersonService {

	private final PersonRepository personRepository;
	private final MovieCreditRepository creditRepository;
	private final StorageService storage;
	private final MovieMapper movieMapper;
	private final AuditService auditService;

	public PageResponse<PersonSummary> search(String keyword, Pageable pageable) {
		String kw = keyword == null ? "" : keyword.trim();
		return PageResponse.of(personRepository.findByNameContaining(kw, pageable), movieMapper::toPersonSummary);
	}

	@Transactional(readOnly = true)
	public PersonDetail detail(String slug) {
		Person p = personRepository.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Không tìm thấy người này"));
		return toDetail(p, true);
	}

	@Transactional
	public PersonDetail create(PersonRequest req) {
		if (req.tmdbId() != null && personRepository.existsByTmdbId(req.tmdbId())) {
			throw ApiException.conflict("TMDB_ID_EXISTS", "tmdbId đã tồn tại");
		}
		Person p = new Person();
		apply(p, req);
		personRepository.save(p);
		PersonDetail result = toDetail(p, false);
		auditService.record("PERSON_CREATE", "PERSON", p.getId(), null, result);
		return result;
	}

	@Transactional
	public PersonDetail update(Long id, PersonRequest req) {
		Person p = get(id);
		if (req.tmdbId() != null && personRepository.existsByTmdbIdAndIdNot(req.tmdbId(), id)) {
			throw ApiException.conflict("TMDB_ID_EXISTS", "tmdbId đã tồn tại");
		}
		PersonDetail before = toDetail(p, false);
		apply(p, req);
		PersonDetail after = toDetail(p, false);
		auditService.record("PERSON_UPDATE", "PERSON", id, before, after);
		return after;
	}

	@Transactional
	public void delete(Long id) {
		Person p = get(id);
		if (creditRepository.existsByPerson_Id(id)) {
			throw ApiException.conflict("PERSON_IN_USE", "Người này còn gắn với phim, không thể xóa");
		}
		auditService.record("PERSON_DELETE", "PERSON", id, toDetail(p, false), null);
		personRepository.delete(p);
		storage.deleteAfterCommit(p.getAvatarUrl());
	}

	@Transactional
	public PersonDetail updateAvatar(Long id, MultipartFile file) {
		Person p = get(id);
		String old = p.getAvatarUrl();
		p.setAvatarUrl(storage.replaceImage(file, "people", old));
		auditService.record("PERSON_AVATAR", "PERSON", id, old, p.getAvatarUrl());
		return toDetail(p, false);
	}

	private Person get(Long id) {
		return personRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy người này"));
	}

	private void apply(Person p, PersonRequest req) {
		p.setName(req.name().trim());
		p.setBiography(req.biography());
		p.setAvatarUrl(blankToNull(req.avatarUrl()) != null ? req.avatarUrl().trim() : p.getAvatarUrl());
		p.setBirthDate(req.birthDate());
		p.setTmdbId(req.tmdbId());
		String wanted = req.slug() != null && !req.slug().isBlank() ? req.slug() : SlugUtil.toSlug(req.name());
		if (!wanted.equals(p.getSlug())) {
			if (req.slug() != null && !req.slug().isBlank() && personRepository.existsBySlug(wanted)) {
				throw ApiException.conflict("SLUG_TAKEN", "Slug đã được sử dụng");
			}
			p.setSlug(SlugUtil.unique(wanted, personRepository::existsBySlug));
		}
	}

	private PersonDetail toDetail(Person p, boolean withCredits) {
		var credits = withCredits
				? creditRepository.findPublicByPersonId(p.getId()).stream()
						.map(c -> new PersonCredit(movieMapper.toRef(c.getMovie()), c.getRole(),
								c.getCharacterName().isEmpty() ? null : c.getCharacterName()))
						.toList()
				: java.util.List.<PersonCredit>of();
		return new PersonDetail(p.getId(), p.getName(), p.getSlug(), p.getBiography(), storage.publicUrl(p.getAvatarUrl()),
				p.getBirthDate(), p.getTmdbId(), credits);
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
