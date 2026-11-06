package lv.xenonfix.page;

import org.springframework.data.jpa.repository.JpaRepository;

interface PageRepository extends JpaRepository<Page, PageKey> {
}
