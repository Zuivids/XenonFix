import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { STARTING_PAGES } from '../../pages/testing/starting-pages';
import { adminRoutes } from '../admin.routes';
import { EditablePage, EditableText, PageDocument } from './page-admin-api';
import { labelFor, problems } from './page-editing';
import { PageEditor } from './page-editor';
import { PageList } from './page-list';
import { PAGE_SCHEMAS } from './page-schemas';

function editable(page: 'home' | 'about' | 'works' | 'site-info'): EditablePage {
  return {
    page,
    content: structuredClone(STARTING_PAGES[page]) as PageDocument,
    version: 3,
    updatedAt: '2026-11-06T10:00:00Z',
    updatedBy: null,
  };
}

describe('Page editing', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'admin', children: adminRoutes }]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function request(url: string, method = 'GET'): Promise<TestRequest> {
    for (let turn = 0; turn < 100; turn++) {
      const [found] = http.match((pending) => pending.url === url && pending.method === method);
      if (found) {
        return found;
      }
      await new Promise((resolve) => setTimeout(resolve));
    }
    throw new Error(`No ${method} request to ${url}`);
  }

  async function settled(fixture: ComponentFixture<unknown>): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 10));
    await fixture.whenStable();
  }

  async function openEditor(page: EditablePage): Promise<ComponentFixture<PageEditor>> {
    const fixture = TestBed.createComponent(PageEditor);
    fixture.componentRef.setInput('page', page.page);
    fixture.detectChanges();
    (await request(`/api/admin/pages/${page.page}`)).flush(page);
    await settled(fixture);
    return fixture;
  }

  /** The first input or text area under a section and label. */
  function field(root: HTMLElement, section: string, label: string): HTMLInputElement | HTMLTextAreaElement {
    const fieldset = [...root.querySelectorAll('fieldset')].find((set) =>
      set.querySelector(':scope > legend')?.textContent?.trim().startsWith(section),
    )!;
    const found = [...fieldset.querySelectorAll('label')].find((l) => l.textContent?.trim().startsWith(label))!;
    return found.querySelector('input, textarea')!;
  }

  function type(input: HTMLInputElement | HTMLTextAreaElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function click(root: HTMLElement, text: string): void {
    [...root.querySelectorAll('button')].find((button) => button.textContent?.trim() === text)!.click();
  }

  function list(root: HTMLElement, legend: string): HTMLFieldSetElement {
    return [...root.querySelectorAll<HTMLFieldSetElement>('fieldset.list')].find((set) =>
      set.querySelector('legend')?.textContent?.trim().startsWith(legend),
    )!;
  }

  it('lists the five pages and the contact details', async () => {
    const fixture = TestBed.createComponent(PageList);
    fixture.detectChanges();
    (await request('/api/admin/pages')).flush([
      { page: 'home', updatedAt: '2026-11-06T10:00:00Z', updatedBy: 'Uģis' },
      { page: 'about', updatedAt: '2026-11-06T10:00:00Z', updatedBy: null },
    ]);
    await settled(fixture);
    const links = [...(fixture.nativeElement as HTMLElement).querySelectorAll('a')];

    expect(links.map((link) => link.querySelector('.name')?.textContent)).toEqual([
      'Sākumlapa',
      'Par mums',
      'Mūsu pakalpojumi',
      'Mūsu darbi',
      'Kontakti',
      'Kontaktinformācija',
    ]);
    expect(links[0].getAttribute('href')).toBe('/admin/pages/home');
    expect(links[0].textContent).toContain('Mainīja Uģis');
    expect(links[1].textContent).toContain('Vēl nav mainīta');
  });

  it('edits each language, showing the Latvian text where a translation is empty', async () => {
    const page = editable('home');
    (page.content['intro'] as EditableText).en = '';
    const fixture = await openEditor(page);
    const root = fixture.nativeElement as HTMLElement;

    expect(field(root, 'Augšdaļa', 'Virsraksts').value).toBe('Auto priekšējo lukturu remonts');
    click(root, 'Krievu');
    await settled(fixture);
    expect(field(root, 'Augšdaļa', 'Virsraksts').value).toBe('Ремонт передних автомобильных фар');
    type(field(root, 'Augšdaļa', 'Virsraksts'), 'Новый заголовок');
    click(root, 'Angļu');
    await settled(fixture);
    expect(field(root, 'Augšdaļa', 'Ievads').value).toBe('');
    expect(field(root, 'Augšdaļa', 'Ievads').getAttribute('placeholder')).toBe('Auto lukturu remonta pieredze jau 10 gadi.');
    expect(root.textContent).toContain('Ir nesaglabātas izmaiņas');

    click(root, 'Saglabāt');
    const save = await request('/api/admin/pages/home', 'PUT');

    expect(save.request.body.version).toBe(3);
    const heading = save.request.body.content.heading as EditableText;
    expect(heading).toEqual({ lv: 'Auto priekšējo lukturu remonts', ru: 'Новый заголовок', en: 'Car headlight repair' });
    save.flush({ ...page, version: 4, updatedBy: 'Test', content: save.request.body.content });
    await settled(fixture);
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Saglabāts');
  });

  it('adds, moves and removes list items', async () => {
    const fixture = await openEditor(editable('about'));
    const root = fixture.nativeElement as HTMLElement;
    const points = () => list(root, 'Numurētie punkti');
    const first = (STARTING_PAGES.about as { points: EditableText[] }).points;

    click(points(), '+ Pievienot: rindkopa');
    await settled(fixture);
    const items = points().querySelectorAll('.item');
    expect(items).toHaveLength(5);
    type(items[4].querySelector('textarea')!, 'Jauns punkts');
    (points().querySelectorAll('.item')[0].querySelector('[aria-label="Rindkopa 1 uz leju"]') as HTMLButtonElement).click();
    await settled(fixture);
    (points().querySelectorAll('.item')[2].querySelector('.item-actions button:last-child') as HTMLButtonElement).click();
    await settled(fixture);

    click(root, 'Saglabāt');
    const sent = (await request('/api/admin/pages/about', 'PUT')).request.body.content.points as EditableText[];
    expect(sent.map((point) => point.lv)).toEqual([first[1].lv, first[0].lv, first[3].lv, 'Jauns punkts']);
  });

  it('does not save with a missing Latvian text or photo, and points at the field', async () => {
    const fixture = await openEditor(editable('works'));
    const root = fixture.nativeElement as HTMLElement;
    click(list(root, 'Galerija'), '+ Pievienot: foto');
    await settled(fixture);
    type(field(root, 'Augšdaļa', 'Virsraksts'), ' ');

    click(root, 'Saglabāt');
    await settled(fixture);

    http.expectNone({ method: 'PUT', url: '/api/admin/pages/works' });
    const alert = root.querySelector('[role="alert"]')!.textContent!;
    expect(alert).toContain('Virsraksts: vajag latvisko tekstu.');
    expect(alert).toContain('Foto 7. Foto: nav izvēlēts foto.');
    expect(alert).toContain('Foto 7. Paraksts: vajag latvisko tekstu.');
    expect(field(root, 'Augšdaļa', 'Virsraksts').closest('label')?.classList).toContain('invalid');
  });

  it('uploads a photo and shows it', async () => {
    const fixture = await openEditor(editable('about'));
    const root = fixture.nativeElement as HTMLElement;
    const vadītājs = [...root.querySelectorAll('fieldset')].find((set) => set.querySelector('legend')?.textContent === 'Vadītājs')!;
    const input = vadītājs.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'photo.jpg', { type: 'image/jpeg' })] });

    input.dispatchEvent(new Event('change'));
    const upload = await request('/api/admin/pages/images', 'POST');
    expect(upload.request.body instanceof FormData).toBe(true);
    upload.flush({ src: '/uploads/pages/0b0e5e8a-1111-4222-8333-444455556666.jpg', width: 1200, height: 1600 });
    await settled(fixture);

    expect(vadītājs.querySelector('img')?.getAttribute('src')).toBe('/uploads/pages/0b0e5e8a-1111-4222-8333-444455556666.jpg');
    click(root, 'Saglabāt');
    const sent = (await request('/api/admin/pages/about', 'PUT')).request.body.content;
    expect(sent.managerImage).toEqual({ src: '/uploads/pages/0b0e5e8a-1111-4222-8333-444455556666.jpg', width: 1200, height: 1600 });
  });

  it('keeps what was typed when someone else saved the page meanwhile', async () => {
    const fixture = await openEditor(editable('site-info'));
    const root = fixture.nativeElement as HTMLElement;
    type(field(root, 'Saziņa', 'Tālrunis'), '+371 200 00 999');

    click(root, 'Saglabāt');
    (await request('/api/admin/pages/site-info', 'PUT')).flush(
      { code: 'CHANGED_BY_ANOTHER' },
      { status: 409, statusText: 'Conflict' },
    );
    await settled(fixture);

    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Kāds cits tikko saglabāja');
    expect(field(root, 'Saziņa', 'Tālrunis').value).toBe('+371 200 00 999');
    click(root, 'Ielādēt saglabāto (jūsu izmaiņas zudīs)');
    (await request('/api/admin/pages/site-info')).flush({ ...editable('site-info'), version: 5 });
    await settled(fixture);
    expect(field(root, 'Saziņa', 'Tālrunis').value).toBe('+371 288 03 119');
  });

  it('points at the field the backend refused', async () => {
    const fixture = await openEditor(editable('site-info'));
    const root = fixture.nativeElement as HTMLElement;

    click(root, 'Saglabāt');
    (await request('/api/admin/pages/site-info', 'PUT')).flush(
      { code: 'INVALID_PAGE_CONTENT', field: 'email' },
      { status: 400, statusText: 'Bad Request' },
    );
    await settled(fixture);

    expect(field(root, 'Saziņa', 'E-pasts').closest('label')?.classList).toContain('invalid');
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('E-pasts: pārbaudiet pierakstu vai garumu.');
  });

  it('names a refused field inside a list', () => {
    expect(labelFor(PAGE_SCHEMAS.home.fields, 'tiles[2].title')).toBe('Pakalpojumu flīzes, flīze 3: Nosaukums');
    expect(labelFor(PAGE_SCHEMAS.about.fields, 'goal[1]')).toBe('Rindkopas, rindkopa 2');
    expect(labelFor(PAGE_SCHEMAS.home.fields, 'seo.title')).toBe('Nosaukums pārlūka cilnē un meklēšanas rezultātos');
  });

  it('finds nothing missing in any page as it starts', () => {
    for (const [name, schema] of Object.entries(PAGE_SCHEMAS)) {
      expect(problems(schema.fields, structuredClone(STARTING_PAGES[name as keyof typeof STARTING_PAGES]) as PageDocument)).toEqual([]);
    }
  });
});
